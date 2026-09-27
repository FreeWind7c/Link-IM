package com.link.common.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务器维护通知
 * 用于优雅停服时通知客户端服务即将重启
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月29日
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServerMaintenanceNotice {

    /** 通知类型：shutdown=即将关闭, upgrade=升级维护 */
    private String type;

    /** 提示消息 */
    private String message;

    /** 预计停机时长（秒），0表示未知 */
    private int estimatedDowntime;

    /** 建议重连延迟（秒） */
    private int reconnectDelay;

    public static ServerMaintenanceNotice createUpgradeNotice() {
        return new ServerMaintenanceNotice(
            "upgrade",
            "服务升级中，请稍后...",
            60,
            5
        );
    }

    public static ServerMaintenanceNotice createShutdownNotice() {
        return new ServerMaintenanceNotice(
            "shutdown",
            "服务即将重启，请稍后重连",
            30,
            3
        );
    }
}
