# Link IM

基于 **Netty + Spring Boot** 的高性能即时通讯（IM）服务端，采用自定义二进制协议，支持 TCP / WebSocket 双承载，事件驱动架构，按职责拆分为多模块工程，长连接接入与 HTTP 业务可独立部署。

## 特性

- **双承载协议**：底层编解码与业务 Handler 完全复用，仅 pipeline 前端不同，一套代码同时支持 **TCP** 和 **WebSocket**，通过配置切换。
- **自定义二进制协议**：`magic(2B) + action(2B) + length(4B) + body`，魔数校验 + 帧长度校验，从源头抵御非法/超大包攻击。
- **事件驱动**：基于 `action` 路由到对应 `EventHandler`，工厂模式注册，新增业务事件只需实现一个 Handler。
- **连接安全**：登录认证门禁（未认证连接仅放行 LOGIN）、认证超时踢线、单 IP 连接数限制、全局连接数限制、单帧长度上限。
- **会话管理**：Channel 与用户会话双向映射，支持多端登录与定向推送。
- **心跳保活**：基于 `IdleStateHandler` 的读写空闲检测，自动清理僵尸连接。
- **可插拔序列化**：默认 JSON（FastJSON / Gson），支持 Protobuf，消息体多态反序列化。
- **业务能力**：单聊、群聊、好友关系（加好友 / 审批）、多种消息类型（文本 / 图片 / 视频 / 语音 / 红包 / 通知）。
- **群聊事件**：加入群聊、移除群成员、群通知消息推送，成员变更实时下发在线成员。
- **@提及**：正文以 `{@userId}` / `{@all}` 占位符承载，服务端还原昵称快照生成会话摘要，兼顾展示与鉴权。
- **消息引用**：引用消息只传 `{msgId, seq, chatId}` 定位，快照由服务端回查补全；按 `MessageType.quotable` 控制可引用范围（红包 / 语音 / 通知不可引用）。
- **钱包体系**：钱包余额与流水（收支类型、业务类型区分普通 / 红包 / 商户 / 链上 / 后台转账），提供充值等 REST 接口。
- **REST API**：会话、好友、消息、用户、钱包等 HTTP 接口，与长连接服务共用一套领域模型。
- **存储**：Redis（会话路由 / 缓存）+ MongoDB（消息 / 关系 / 钱包持久化）。

## 技术栈

| 分类 | 技术 |
|------|------|
| 语言 | Java 21 |
| 框架 | Spring Boot 4.0.6 |
| 网络 | Netty 4.1.119 |
| 存储 | Redis、MongoDB |
| 认证 | JWT（java-jwt） |
| 序列化 | FastJSON、Gson、Protobuf |
| 构建 | Maven（多模块） |

## 模块划分

工程按职责拆分为 5 个 Maven 模块，构建顺序由依赖关系自动推导：`common → im → core → restapi / server-starter`。

| 模块 | 职责 | 产物 |
|------|------|------|
| `link-common` | 通用基础：注解、序列化、协议模型、ID 生成、Redis 常量、编解码工具 | 库 |
| `link-im` | 业务领域：实体、业务事件处理、消息处理器、MongoDB 访问、领域服务 | 库 |
| `link-core` | IM 引擎：Netty 服务端、协议编解码、pipeline 处理器、事件分发、会话、发送器、连接安全 | 库 |
| `link-restapi` | HTTP 业务层：注册/登录、好友、会话、拉取历史消息等 REST 接口 | **可执行 jar** |
| `link-server-starter` | 长连接接入进程：组装并启动 Netty，绑定 TCP / WebSocket 端口 | **可执行 jar** |

两个可执行模块各自独立启动：

- **`link-server-starter`** → 启动类 `LinkImApplication`。依赖 `link-im`（传递带上 `link-core` 引擎），classpath 无 webmvc，**只跑长连接**。靠 `link.netty.enabled=true` 让 `DefaultServer` 生效并绑定端口。
- **`link-restapi`** → 启动类 `LinkApiApplication`。**只跑 HTTP**，未开启 Netty。注意：本进程内“异地登录”校验与好友通过后的在线推送会降级（需后续 Redis 在线表 + MQ 跨进程打通）。

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
- Maven 3.6+（或直接用仓库内的 `mvnw`）
- Redis
- MongoDB

### 配置

两个可执行模块各自的 `application.yml` 中，连接信息均支持环境变量覆盖：

`link-server-starter/src/main/resources/application.yml`（长连接进程）：

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
  mongodb:
    uri: ${MONGO_URI:mongodb://localhost:27017/im}
link:
  netty:
    enabled: true        # 开启 Netty 接入服务
```

`link-restapi/src/main/resources/application.yml`（HTTP 进程）：

```yaml
server:
  port: ${API_PORT:8080}
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
  mongodb:
    uri: ${MONGO_URI:mongodb://<user>:<password>@<host>:27017/im?authSource=admin}
```

IM 引擎参数（端口、协议、线程、安全策略等）见 `LinkCoreConfig`，可用 `link.*` 前缀覆盖，关键项：

```java
private int port = 8899;                 // 监听端口
private Protocol protocol = WEBSOCKET;   // TCP / WEBSOCKET
private String websocketPath = "/ws";    // WebSocket 握手路径
private int authTimeoutSeconds = 10;     // 认证超时
private int maxConnPerIp = 100;          // 单 IP 最大连接数
private int maxConnections = 100000;     // 全局最大连接数
```

### 构建

```bash
# 编译并安装全部模块（在仓库根目录执行）
./mvnw clean install
```

### 运行

两个进程独立启动，可分别部署：

```bash
# 1) 长连接接入进程（Netty，绑定 TCP / WebSocket 端口）
./mvnw -pl link-server-starter spring-boot:run
# 或：java -jar link-server-starter/target/link-server-starter-0.0.1-SNAPSHOT.jar

# 2) HTTP 业务进程（REST API）
./mvnw -pl link-restapi spring-boot:run
# 或：java -jar link-restapi/target/link-restapi-0.0.1-SNAPSHOT.jar
```

长连接进程启动后控制台输出：`Link IM Server started on port 8899`。

## 目录结构

```
cc-link
├── pom.xml                     # 聚合 + 依赖/插件版本统管
├── link-common/                # 通用：注解、序列化、协议模型、ID、Redis 常量
│   └── com/link/common
├── link-im/                    # 业务领域：实体、业务 Handler、处理器、Mongo、服务
│   └── com/link/im
├── link-core/                  # IM 引擎：Netty 服务端 + 协议 + 事件 + 会话 + 安全
│   └── com/link/core
│       ├── server/             #   Netty 服务端启动
│       ├── codec/              #   协议编解码
│       ├── handler/            #   pipeline 处理器（tcp/ws/idle/security）
│       ├── event/              #   事件类型、分发器、Handler 工厂
│       ├── session/            #   会话工厂 / 管理 / 服务
│       ├── sender/             #   消息发送
│       └── security/           #   连接安全管理
├── link-restapi/               # HTTP 接口（chat / friend / message / user）→ 可执行 jar
│   └── com/link
│       ├── LinkApiApplication.java
│       └── restapi/
└── link-server-starter/        # 长连接接入进程 → 可执行 jar
    └── com/link
        └── LinkImApplication.java
```

## 安全提示

- 仓库内 `application.yml` 的连接信息已改为环境变量注入（`REDIS_HOST` / `MONGO_URI` 等），**请勿把真实生产凭据写死并提交**。
- 推送到公开仓库前，确认 `.gitignore` 已排除敏感配置，并轮换任何曾经提交过的密钥。

## License

待定。
