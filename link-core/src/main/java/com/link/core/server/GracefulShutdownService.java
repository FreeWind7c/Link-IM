package com.link.core.server;

import com.link.common.core.event.EventType;
import com.link.common.core.model.ServerMaintenanceNotice;
import com.link.core.config.LinkCoreConfig;
import com.link.core.sender.LinkMessageSender;
import com.link.core.session.manager.LinkSessionManager;
import com.link.core.session.service.LinkSession;
import io.netty.channel.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 优雅停服服务
 * 负责在服务关闭前通知所有在线用户，并等待连接自然断开
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月29日
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GracefulShutdownService {

    private final LinkCoreConfig config;



    /** 优雅停服等待时间（秒） */
    private static final int GRACEFUL_SHUTDOWN_WAIT_SECONDS = 20;

    /**
     * 执行优雅停服流程
     * 1. 通知所有在线用户服务即将维护
     * 2. 等待指定时间让用户自然断开
     * 3. 强制关闭剩余连接
     */
    public void performGracefulShutdown() {
        if (this.config.getSessionManager() == null || this.config.getLinkSender() == null) {
            log.warn("SessionManager 或 MessageSender 未初始化，跳过优雅停服通知");
            return;
        }

        try {
            // 1. 获取所有在线会话
            List<LinkSession> allSessions = this.config.getSessionManager().getAllSessions();
            if (allSessions == null || allSessions.isEmpty()) {
                log.info("当前无在线用户，跳过优雅停服通知");
                return;
            }

            int onlineCount = allSessions.size();
            log.info("开始优雅停服流程，当前在线会话数: {}", onlineCount);

            // 2. 给所有在线用户发送服务维护通知
            ServerMaintenanceNotice notice = ServerMaintenanceNotice.createUpgradeNotice();
            int successCount = 0;

            for (LinkSession session : allSessions) {
                try {
                    Channel channel = session.getChannel();
                    if (channel != null && channel.isActive()) {
                        this.config.getLinkSender().send(EventType.SERVER_MAINTENANCE, channel, notice);
                        successCount++;
                    }
                } catch (Exception e) {
                    log.error("发送维护通知失败，userId: {}, platform: {}",
                        session.getSessionId(), session.getPlatform(), e);
                }
            }

            log.info("维护通知发送完成，成功: {}/{}", successCount, onlineCount);

            // 3. 等待用户自然断开
            log.info("等待 {} 秒让用户自然断开连接...", GRACEFUL_SHUTDOWN_WAIT_SECONDS);
            TimeUnit.SECONDS.sleep(GRACEFUL_SHUTDOWN_WAIT_SECONDS);

            // 4. 检查剩余连接数
            List<LinkSession> remainingSessions = this.config.getSessionManager().getAllSessions();
            int remainingCount = (remainingSessions != null) ? remainingSessions.size() : 0;

            if (remainingCount > 0) {
                log.info("等待结束，仍有 {} 个连接未主动断开，即将强制关闭", remainingCount);

                // 5. 强制关闭剩余连接
                for (LinkSession session : remainingSessions) {
                    try {
                        Channel channel = session.getChannel();
                        if (channel != null && channel.isActive()) {
                            channel.close();
                        }
                    } catch (Exception e) {
                        log.error("强制关闭连接失败，userId: {}", session.getSessionId(), e);
                    }
                }
            } else {
                log.info("所有用户已自然断开，优雅停服完成");
            }

        } catch (InterruptedException e) {
            log.warn("优雅停服流程被中断", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("优雅停服流程执行异常", e);
        }
    }

    /**
     * 快速通知模式（用于紧急重启）
     * 只发送通知，等待5秒后返回
     */
    public void performQuickNotification() {
        if (this.config.getSessionManager() == null || this.config.getLinkSender() == null) {
            return;
        }

        try {
            List<LinkSession> allSessions = this.config.getSessionManager().getAllSessions();
            if (allSessions == null || allSessions.isEmpty()) {
                return;
            }

            ServerMaintenanceNotice notice = ServerMaintenanceNotice.createShutdownNotice();

            for (LinkSession session : allSessions) {
                try {
                    Channel channel = session.getChannel();
                    if (channel != null && channel.isActive()) {
                        this.config.getLinkSender().send(EventType.SERVER_MAINTENANCE, channel, notice);
                    }
                } catch (Exception e) {
                    log.error("发送快速通知失败", e);
                }
            }

            log.info("快速通知发送完成，等待 5 秒...");
            TimeUnit.SECONDS.sleep(5);

        } catch (Exception e) {
            log.error("快速通知流程异常", e);
        }
    }
}
