package com.link.dispatcher.config;

import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonDocument;

/**
 * MongoDB 命令监听器：记录每个操作的目标节点（主库/从库）
 */
@Slf4j
public class MongoCommandListener implements CommandListener {

    private final String templateName;

    public MongoCommandListener(String templateName) {
        this.templateName = templateName;
    }

    @Override
    public void commandStarted(CommandStartedEvent event) {
        String commandName = event.getCommandName();

        // 只监听读操作（find、aggregate、count 等）
        if (isReadCommand(commandName)) {
            BsonDocument command = event.getCommand();
            String connectionDescription = event.getConnectionDescription().toString();

            log.debug("[{}] MongoDB读操作 [{}] 发送到节点: {}, 数据库: {}, 命令: {}",
                    templateName,
                    commandName,
                    extractServerAddress(connectionDescription),
                    event.getDatabaseName(),
                    command.toJson());
        }
    }

    @Override
    public void commandSucceeded(CommandSucceededEvent event) {
        String commandName = event.getCommandName();

        if (isReadCommand(commandName)) {
            String connectionDescription = event.getConnectionDescription().toString();

            log.info("[{}] MongoDB读操作 [{}] 成功 | 节点: {} | 耗时: {}ms",
                    templateName,
                    commandName,
                    extractServerAddress(connectionDescription),
                    event.getElapsedTime(java.util.concurrent.TimeUnit.MILLISECONDS));
        }
    }

    @Override
    public void commandFailed(CommandFailedEvent event) {
        log.error("MongoDB命令 [{}] 失败: {}",
                event.getCommandName(),
                event.getThrowable().getMessage());
    }

    /**
     * 判断是否为读命令
     */
    private boolean isReadCommand(String commandName) {
        return commandName.equals("find")
                || commandName.equals("aggregate")
                || commandName.equals("count")
                || commandName.equals("countDocuments")
                || commandName.equals("distinct");
    }

    /**
     * 从连接描述中提取服务器地址
     */
    private String extractServerAddress(String connectionDescription) {
        // connectionDescription 格式: "ServerDescription{address=175.178.245.39:27018, ...}"
        // 或者: "{address=175.178.245.39:27018, ...}"
        try {
            int addressStart = connectionDescription.indexOf("address=");
            if (addressStart == -1) {
                return connectionDescription; // 返回完整描述用于调试
            }

            addressStart += 8; // "address=".length()
            int addressEnd = connectionDescription.indexOf(",", addressStart);
            if (addressEnd == -1) {
                addressEnd = connectionDescription.indexOf("}", addressStart);
            }
            if (addressEnd == -1) {
                addressEnd = connectionDescription.length();
            }

            return connectionDescription.substring(addressStart, addressEnd).trim();
        } catch (Exception e) {
            log.warn("解析服务器地址失败: {}", connectionDescription);
            return "parse-error: " + connectionDescription;
        }
    }
}
