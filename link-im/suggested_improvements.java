// 建议的完整实现

@Override
public void handler(Object obj, Channel channel) {
    DefaultMessageInfo message = (DefaultMessageInfo) obj;

    // 单线程分配 seq
    MessageSeqAllocator.SeqResult seqResult = messageSeqAllocator.allocate(
        message.getChatId(),
        message.getId().toHexString()
    );

    if (seqResult.duplicate()) {
        // 兜底：Redis 有 seq 但 DB 可能没有消息
        int seq = (int) this.messageSeqAllocator.getMessageSeq(
            message.getChatId(),
            message.getId().toHexString()
        );
        if (seq <= 0) {
            log.warn("重复消息但无法获取 seq: msgId={}", message.getId());
            return;
        }
        message.setSeq(seq);

        try {
            this.insert(message);
            log.info("兜底插入成功: msgId={}, seq={}", message.getId(), seq);
        } catch (DuplicateKeyException e) {
            // 预期内的并发重复，静默处理
            log.debug("兜底插入时检测到已存在: msgId={}", message.getId());
        } catch (Exception e) {
            // 其他异常记录日志，但仍发 ACK（消息可能已在 DB 中）
            log.error("兜底插入异常: msgId={}, seq={}", message.getId(), seq, e);
        } finally {
            this.config.getLinkSender().send(EventType.ACK, channel, message.getSeq());
        }
        return;
    }

    message.setSeq((int) seqResult.seq());

    // 提交到线程池，捕获拒绝异常
    try {
        this.workerPool.submit(() -> {
            try {
                processHeavy(message, channel);
            } catch (Exception e) {
                log.error("消息处理彻底失败(重试后): msgId={}, chatId={}, seq={}",
                         message.getId(), message.getChatId(), message.getSeq(), e);
                // 可选：记录到死信队列
                // saveToDeadLetterQueue(message, e);
            }
        });
    } catch (RejectedExecutionException e) {
        log.error("线程池已满，降级为同步处理: msgId={}, chatId={}",
                 message.getId(), message.getChatId());
        // 降级：在当前线程同步处理
        try {
            processHeavy(message, channel);
        } catch (Exception ex) {
            log.error("同步处理也失败: msgId={}", message.getId(), ex);
            // 清除 Redis 去重标记，允许客户端重试
            cleanupDedupKey(message.getChatId(), message.getId().toHexString());
        }
    }
}

@Retryable(
    retryFor = {
        com.mongodb.MongoTimeoutException.class,
        com.mongodb.MongoSocketException.class,
        com.mongodb.MongoConnectionException.class
    },
    noRetryFor = {DuplicateKeyException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2)
)
public void processHeavy(DefaultMessageInfo message, Channel channel) {
    // 引用校验
    sanitizeQuote(message);
    LinkAck linkAck = new LinkAck(
        message.getId().toHexString(),
        message.getChatId(),
        message.getSeq()
    );

    try {
        this.insert(message);
    } catch (DuplicateKeyException e) {
        // Redis 去重漏网的重发
        log.debug("DB 检测到重复消息: msgId={}", message.getId());
        this.config.getLinkSender().send(EventType.ACK, channel, linkAck);
        return;
    }

    // 更新会话
    Query eq = eq(
        where(col(ChatSession::getChatId)).is(message.getChatId())
            .and(col(ChatSession::getLastMsgSeq)).lt(message.getSeq())
    );
    Update update = update()
        .set(col(ChatSession::getLastMsgSummary),
             MessageType.summaryOf(message.getType(), message.getBaseData()))
        .set(col(ChatSession::getLastMsgType), message.getType())
        .set(col(ChatSession::getLastMsgTime), now())
        .set(col(ChatSession::getLastMsgSeq), message.getSeq());

    try {
        this.getMongoTemplate().updateFirst(eq, update, ChatSession.class);
    } catch (Exception e) {
        // 会话更新失败不影响主流程，但记录日志
        log.error("会话更新失败: chatId={}, msgId={}",
                 message.getChatId(), message.getId(), e);
    }

    // 发送 ACK
    this.config.getLinkSender().send(EventType.ACK, channel, linkAck);

    // 转发给接收方
    try {
        this.messageProcessor.processor(message, channel);
    } catch (Exception e) {
        // 转发失败记录日志，依赖推送重试机制和上线同步兜底
        log.error("消息转发失败: msgId={}, rcvId={}",
                 message.getId(), message.getRcvId(), e);
    }
}

private void cleanupDedupKey(String chatId, String msgId) {
    try {
        String dedupKey = "dedup:" + chatId + ":" + msgId;
        stringRedisTemplate.delete(dedupKey);
        log.info("清除去重标记: dedupKey={}", dedupKey);
    } catch (Exception e) {
        log.error("清除去重标记失败: chatId={}, msgId={}", chatId, msgId, e);
    }
}
