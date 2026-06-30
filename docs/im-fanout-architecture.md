# IM 消息扩散架构（分层扩散）

> 本文说明 cc-link 群聊/单聊的消息存储与读取模型：**消息体读扩散、实时投递扇出不落库、未读数计算得出、会话项轻量写**。
> 核心结论：**群聊统一走读扩散**，避免大群按成员数放大的写入。

---

## 一、三类数据，三种扩散策略

| 数据 | 载体（集合/存储） | 扩散模型 | 写入时机 |
|---|---|---|---|
| 消息体 | `GroupMessageInfo` / `DefaultMessageInfo`（Mongo，按 `chat_id+seq`） | **读扩散**：只存一份 | 每条消息写 1 次 |
| seq 分配 | Redis `seq:{chatId}`（`INCR`，权威计数器） | 单点自增 | 每条消息 `INCR` 1 次 |
| 会话级状态 | `ChatSession`（Mongo，每会话一条，全员共享） | 单条共享 | 每条消息更新 `lastMsgSeq/summary/time` |
| 用户级状态 | `ChatSessionMember`（Mongo，每人每会话一条） | 轻量、与消息量解耦 | 仅进出会话 / 手动置顶免打扰 / 进出群时写 |
| 实时投递 | Netty channel（内存，临时） | 投递时扇出，**不落库** | 每条消息推一次在线成员 |
| 未读数 | 不存储 | **计算得出** | 读时 `lastMsgSeq - lastReadSeq` |

关键点：**未读数和消息体都不按成员数放大**。群里 N 人，发一条消息 = 1 次消息写 + 1 次 `ChatSession` 更新 + 1 次 `INCR`，与 N 无关。

---

## 二、发消息数据流（写一份 + 实时扇出）

```
                          发送方 client
                               │  GROUP_MESSAGE
                               ▼
                 ┌─────────────────────────────┐
                 │ LinkGroupMessageEventHandler │
                 └─────────────────────────────┘
                               │
        ┌──────────────────────┼───────────────────────────┐
        ▼                      ▼                            ▼
  ① MessageSeqAllocator   ② insert 消息体            ③ updateFirst
     INCR seq:{chatId}        GroupMessageInfo            ChatSession
     (Lua 原子去重+分配)      (Mongo，存一份)             lastMsgSeq/summary/time
        │                      │                            │
        │  seq                 │ 只写 1 条                  │ 只写 1 条（全员共享）
        ▼                      ▼                            ▼
   ── 不论群里多少人，以上写入次数都与成员数 N 无关（读扩散）──

                               │
                               ▼  ④ 实时投递（不落库）
                 ┌─────────────────────────────┐
                 │  LinkGroupMessageProcessor   │
                 │  取成员列表（Redis 缓存，    │
                 │  miss 时查 active=true 重建）│
                 └─────────────────────────────┘
                               │ 遍历在线成员的 channel
              ┌────────────────┼────────────────┐
              ▼                ▼                ▼
          成员A在线         成员B离线          成员C在线
          推 channel       （跳过，不存       推 channel
                            离线消息）
        ↑ 这一步是「投递扇出」，是临时的内存推送，不产生 N 份持久化数据
```

**离线成员怎么补**：不靠写扩散补，靠 client 上线后**主动拉**（见第三节）。

---

## 三、拉历史消息数据流（读扩散 + 空档过滤）

```
        client（B）打开群，翻历史
                 │  POST /message/pull-message
                 │  { chatId, sessionType=2, userId=B, skip, limit }
                 ▼
        ┌─────────────────────┐
        │  MessageInfoService │
        └─────────────────────┘
                 │
                 │ 群聊：查 B 的 ChatSessionMember.blackoutGaps
                 │       （被踢期间不可见的 seq 区间，普通成员为空）
                 ▼
        ┌──────────────────────────────────────────┐
        │ Mongo 查询 GroupMessageInfo               │
        │   where chat_id = {chatId}                │
        │   $nor [ seq ∈ gap1, seq ∈ gap2, ... ]    │ ← 排除空档
        │   sort by timestamp desc, skip/limit      │
        └──────────────────────────────────────────┘
                 │ 返回的列表里：空档期消息压根不存在
                 ▼
        client 直接渲染（前端不需要懂 gap，拿到即是过滤后的结果）
```

> 普通成员 `blackoutGaps` 为空 → 不加 `$nor`，查询与原来完全一致，走 `idx_chat_seq` 索引，零额外开销。
> 只有「被踢过又拉回」的成员才带 gaps，成本有界。

---

## 四、未读数：算出来，不存储

```
   ChatSession.lastMsgSeq ───┐
                             ├──►  unreadCount = lastMsgSeq − lastReadSeq
   ChatSessionMember         │
     .lastReadSeq ───────────┘
   （owner 私有，进出会话时上报）
```

- 来消息时**不**给每个成员的信箱写未读（省掉信箱写扩散）。
- 进出会话时 client 惰性上报 `lastReadSeq` 落库。

---

## 五、踢人 / 进群：blackout gap 的维护

读扩散下「被踢期间看不到消息」需要**读时过滤**，靠成员身上的空档区间实现。

```
时间线（seq）:  ... 5 │ 6  7  8  9  10 │ 11 ...
                     │└── B 被踢期间 ──┘│
                  B被踢            B重新进群

removeMember（踢）:
   读 Redis seq:{chatId} → currentSeq=5
   给 B 的 blackoutGaps push 开口段 { from: 6, to: null }   // to=null 表示仍被踢
   set active=false

joinGroup（拉回，复活分支）:
   读 Redis seq:{chatId} → currentSeq=10
   封口：最后一段 to=null 的 gap → { from: 6, to: 10 }
        （若 from > currentSeq，即空档期无消息 → 直接丢弃该段，自愈）
   set active=true
   set lastReadSeq=10   // 抬到最新，否则空档期消息被算成未读（看不到却提示未读）

结果：B 的可见集合 = [1,5] ∪ [11,∞)，中间 [6,10] 被 $nor 挖掉
```

**为什么 gap 边界读 Redis 而不读 `ChatSession.lastMsgSeq`**：
seq 权威来源是 Redis 计数器，`ChatSession.lastMsgSeq` 是它的异步镜像、可能滞后。踢/回两端都读 Redis，保证口径一致、边界不差一。

---

## 六、为什么不用写扩散

| 维度 | 读扩散（现状） | 写扩散（未采用） |
|---|---|---|
| 发一条消息的写次数 | 1（与群规模无关） | N（每成员一份，大群爆炸） |
| 存储 | 一份 | N 份 |
| 「被踢看不到」 | 需读时 `$nor` 过滤（gap 模型） | 天然实现（不往其信箱写） |
| 适用 | **大群 / 本项目** | 强隔离个性化 feed（IM 群聊用不上） |

结论：群聊的写放大是写扩散在大群的致命伤。「被踢看不到」这点写扩散更省，但不值得为它吃 N× 写放大。当前分层（消息体读扩散 + 投递扇出不落库 + 未读计算 + 会话项轻量写）是大群主流形态，维持现状。

---

## 七、涉及的关键类/常量

- `link-core` · `MessageSeqAllocator` + `resources/lua/dedup_seq.lua`：seq 分配与去重（`INCR seq:{chatId}`）
- `link-common` · `RedisConstant.SEQ` = `"seq:"`：计数器 key 前缀（权威来源）
- `link-im` · `LinkGroupMessageEventHandler`：消息入库 + 更新 ChatSession
- `link-im` · `LinkGroupMessageProcessor`：实时投递扇出（取 `active=true` 成员，Redis 缓存）
- `link-im` · `ChatSessionMember.blackoutGaps` / `Gap`：被踢期间不可见的 seq 区间
- `link-restapi` · `GroupMemberService.removeMember`：踢人时开口
- `link-restapi` · `GroupInfoService.joinGroup`：拉回时封口 + 抬 `lastReadSeq`
- `link-restapi` · `MessageInfoService.pullMessage/completeMessage`：读时 `$nor` 过滤空档
```
