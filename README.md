# Link IM

基于 **Netty + Spring Boot** 的高性能即时通讯（IM）服务端，采用自定义二进制协议，支持 TCP / WebSocket 双承载，事件驱动架构，开箱即用。

## 特性

- **双承载协议**：底层编解码与业务 Handler 完全复用，仅 pipeline 前端不同，一套代码同时支持 **TCP** 和 **WebSocket**，通过配置切换。
- **自定义二进制协议**：`magic(2B) + action(2B) + length(4B) + body`，魔数校验 + 帧长度校验，从源头抵御非法/超大包攻击。
- **事件驱动**：基于 `action` 路由到对应 `EventHandler`，工厂模式注册，新增业务事件只需实现一个 Handler。
- **连接安全**：登录认证门禁（未认证连接仅放行 LOGIN）、认证超时踢线、单 IP 连接数限制、全局连接数限制、单帧长度上限。
- **会话管理**：Channel 与用户会话双向映射，支持多端登录与定向推送。
- **心跳保活**：基于 `IdleStateHandler` 的读写空闲检测，自动清理僵尸连接。
- **可插拔序列化**：默认 JSON（FastJSON / Gson），支持 Protobuf，消息体多态反序列化。
- **业务能力**：单聊、群聊、好友关系（加好友 / 审批）、多种消息类型（文本 / 图片 / 视频 / 语音 / 红包）。
- **REST API**：会话、好友、消息、用户等 HTTP 接口，与长连接服务共用一套领域模型。
- **存储**：Redis（会话路由 / 缓存）+ MongoDB（消息 / 关系持久化）。

## 技术栈

| 分类 | 技术 |
|------|------|
| 语言 | Java 21 |
| 框架 | Spring Boot 4.0.6 |
| 网络 | Netty 4.1.119 |
| 存储 | Redis、MongoDB |
| 认证 | JWT（java-jwt） |
| 序列化 | FastJSON、Gson、Protobuf |
| 构建 | Maven |

## 架构概览

```
客户端 (TCP / WebSocket)
        │
        ▼
┌─────────────────────────────────────────┐
│  Netty Pipeline                          │
│  ┌──────────────┐  WebSocket 时前置      │
│  │ WS 适配 / 握手 │                       │
│  └──────┬───────┘                        │
│         ▼                                │
│  PackData 编解码 (魔数 + 帧长度校验)       │
│         ▼                                │
│  IdleHandler 心跳检测                     │
│         ▼                                │
│  Inbound/Outbound 业务 Handler            │
└─────────┬───────────────────────────────┘
          ▼
   LinkEventDispatcher  ── 认证门禁 ──▶ 拒绝/关闭
          │
          ▼  (按 action 路由 + 线程池异步)
   EventHandlerFactory ──▶ EventHandler
          │
          ▼
   Session / Sender / 业务 Processor
          │
          ▼
     Redis  /  MongoDB
```

### 协议格式

| 字段 | 类型 | 说明 |
|------|------|------|
| magic | short (2B) | 魔数，默认 `0x59C3`，校验失败直接关连接 |
| action | short (2B) | 事件类型，见 `EventType` |
| length | int (4B) | body 字节长度，超限关连接 |
| body | bytes | 序列化后的业务数据 |

### 事件类型（action）

| action | 事件 | 说明 |
|--------|------|------|
| 1 | HEARTBEAT | 心跳 |
| 2 | ACK | 确认 |
| 1001 | LOGIN | 登录认证 |
| 1002 | LOGOUT | 登出 |
| 1010 | DEFAULT_MESSAGE | 单聊消息 |
| 1011 | GROUP_MESSAGE | 群聊消息 |
| 1012 | ADD_FRIEND | 添加好友 |
| 1013 | APPROVE_FRIEND | 好友审批 |

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.6+
- Redis
- MongoDB

### 配置

修改 `src/main/resources/application.yml`：

```yaml
spring:
  data:
    redis:
      host: localhost
  mongodb:
    uri: mongodb://<user>:<password>@<host>:27017/im?authSource=admin
```

IM 服务端参数（端口、协议、线程、安全策略等）见 `LinkCoreConfig`，关键项：

```java
private int port = 8899;                 // 监听端口
private Protocol protocol = WEBSOCKET;   // TCP / WEBSOCKET
private String websocketPath = "/ws";    // WebSocket 握手路径
private int authTimeoutSeconds = 10;     // 认证超时
private int maxConnPerIp = 100;          // 单 IP 最大连接数
private int maxConnections = 100000;     // 全局最大连接数
```

### 运行

```bash
# 使用 Maven Wrapper
./mvnw spring-boot:run

# 或打包后运行
./mvnw clean package
java -jar target/cc-im-0.0.1-SNAPSHOT.jar
```

启动后控制台输出：`Link IM Server started on port 8899`。

## 目录结构

```
src/main/java/com/link
├── LinkImApplication.java        # 启动入口
├── common/                       # 通用：注解、序列化、Channel 属性、Redis 常量
├── config/                       # Spring 配置（MongoDB 等）
├── core/                         # IM 核心
│   ├── server/                   #   Netty 服务端启动
│   ├── client/                   #   内置测试客户端
│   ├── codec/                    #   协议编解码
│   ├── handler/                  #   pipeline 处理器（in/out/idle/ws）
│   ├── event/                    #   事件类型、分发器、Handler 工厂
│   ├── session/                  #   会话工厂 / 管理 / 模型
│   ├── sender/                   #   消息发送
│   ├── security/                 #   连接安全管理
│   └── model/                    #   协议模型（PackData、心跳、好友等）
├── im/                           # 业务领域
│   ├── entity/                   #   实体（消息 / 好友 / 群 / 用户 / 各类消息体）
│   ├── handler/                  #   业务事件处理（登录 / 消息 / 群消息 / 心跳）
│   ├── processor/                #   消息处理器
│   └── util/                     #   JWT、MD5 等工具
├── restapi/                      # HTTP 接口（chat / friend / message / user）
└── pool/                         # 业务线程池
```

## 安全提示

- 当前 `application.yml` 中包含明文 MongoDB 连接凭据，**建议改用环境变量或配置中心注入**，不要提交真实生产凭据到仓库。
- 推送到公开仓库前，请确认 `.gitignore` 已排除敏感配置，并轮换任何曾经提交过的密钥。

## License

待定。
