# Link IM

基于 **Netty + Spring Boot 4** 的高性能即时通讯（IM）服务端。自定义二进制协议，支持 TCP / WebSocket 双承载，事件驱动架构，按职责拆分为多模块工程；长连接接入、HTTP 业务、对象存储、网关、注册中心可独立部署。

## 特性

### 连接与协议

- **双承载协议**：底层编解码与业务 Handler 完全复用，仅 pipeline 前端不同，一套代码同时支持 **TCP** 和 **WebSocket**，通过配置切换。
- **自定义二进制协议**：`magic(2B) + action(2B) + length(4B) + body`，魔数校验 + 帧长度校验，从源头抵御非法/超大包攻击。
- **事件驱动**：按 `action` 路由到对应 `EventHandler`，工厂模式注册，新增业务事件只需实现一个 Handler；RTC 类事件再经二级工厂按子事件分发。
- **连接安全**：登录认证门禁（未认证连接仅放行 LOGIN）、认证超时踢线、单 IP 连接数限制、全局连接数限制、单帧长度上限。
- **会话管理**：Channel 与用户会话双向映射，支持多端登录与定向推送。
- **心跳保活**：基于 `IdleStateHandler` 的读写空闲检测，自动清理僵尸连接。
- **可插拔序列化**：默认 JSON（FastJSON / Gson），支持 Protobuf，消息体多态反序列化。

### 消息与会话

- **消息类型**：文本 / 图片 / 视频 / 语音 / 红包 / 通知 / 通话记录 / 系统消息，`MessageType` 统一登记 data 类、会话摘要标签与可引用性。
- **分层扩散**：消息体读扩散只存一份，实时投递扇出不落库，未读数由 `lastMsgSeq - lastReadSeq` 计算得出——发一条消息的写入次数与群规模无关。详见 [`docs/im-fanout-architecture.md`](docs/im-fanout-architecture.md)。
- **seq 分配**：Redis `INCR seq:{chatId}` 作权威计数器，配合 Lua 脚本做原子去重 + 分配。
- **群聊事件**：加入群聊、移除群成员、管理员设置、群通知消息推送；被踢期间的消息用成员身上的 `blackoutGaps` 区间在读时 `$nor` 过滤。
- **@提及**：正文以 `{@userId}` / `{@all}` 占位符承载，服务端还原昵称快照生成会话摘要，兼顾展示与鉴权。
- **消息引用**：引用只传 `{msgId, seq, chatId}` 定位，快照由服务端回查补全；按 `MessageType.quotable` 控制可引用范围（红包 / 语音 / 通知不可引用）。
- **消息转发**：支持逐条转发（`SINGLE_FORWARD`）与合并转发（`MERGE_FORWARD`）。

### 音视频通话

- **1v1 通话（自建信令）**：服务端只做信令转发，媒体走 WebRTC P2P。`RTC_CALL(1019)` 下再分 `CALL / ACCEPT / CANCEL / OFFER / ANSWER / CANDIDATE / HANG_UP / UNANSWERED` 八个子事件，各自一个 Handler。
- **群通话（腾讯云 TRTC）**：`RTC_GROUP_CALL(1020)` 承载 TRTC 侧已发生事件的状态回报（`CALL / RINGING / ACCEPT / REJECT / USER_ENTER / USER_LEAVE / NO_RESPONSE / LINE_BUSY / HANG_UP / NOT_CONNECTED / INVITE`），服务端负责身份校验与落库记账；`/trtc/user-sig` 签发 UserSig，`/trtc/call-back` 接收腾讯云事件回调。

### 资金能力

- **钱包体系**：钱包余额与流水（收支类型、业务类型区分普通 / 红包 / 商户 / 链上 / 后台转账），充值、提现、本地事务表与退款记录。
- **红包**：发红包 / 抢红包 / 查红包，MongoDB 事务保证扣款与领取一致；`biz_detail_id` 唯一索引挡重复扣款，`(packet_id, user_id)` 唯一索引挡一人多领；定时任务处理过期红包退款。
- **金额精度**：`spring.data.mongodb.representation.big-decimal=decimal128`，两个写库进程必须一致，否则 `$inc` 报 non-numeric、`gte` 退化成字典序比较。存量数据需先跑 [`docs/mongo/migrate-bigdecimal-to-decimal128.js`](docs/mongo/migrate-bigdecimal-to-decimal128.js)。

### 服务化

- **统一网关**：`link-gateway`（WebFlux）作为 HTTP 统一入口，负责路由、JWT 全局鉴权（白名单放行登录/注册/TRTC 回调）与集中式 CORS。
- **跨进程推送**：HTTP 进程触发的在线推送经 RabbitMQ **fanout 广播**发出，由持有 Netty 长连接的进程消费后用本节点 `sessionMap` 中的 Channel 真正下发——解决「REST 进程没有连接、推不动」的问题。
- **自研注册中心**：`link-register` 基于 gRPC + Protobuf 实现注册 / 注销 / 心跳 / 拉取 / 订阅推送（server stream），带健康检查与超时摘除。可与 Nacos 并存（业务模块同时保留 Nacos discovery 配置，默认关闭）。
- **对象存储**：`link-oss` 独立进程，对接腾讯云 COS 处理图片 / 视频 / 语音上传。

## 技术栈

| 分类 | 技术 |
|------|------|
| 语言 | Java 21 |
| 框架 | Spring Boot 4.0.6 |
| 微服务 | Spring Cloud 2025.1.2、Spring Cloud Alibaba 2025.1.0.0（Nacos）、Spring Cloud Gateway (WebFlux) |
| 网络 | Netty（版本由 Spring Boot BOM 管理）、gRPC + Protobuf |
| 消息队列 | RabbitMQ（fanout 广播推送指令） |
| 存储 | Redis（Redisson）、MongoDB（含事务、decimal128） |
| 云服务 | 腾讯云 COS（对象存储）、腾讯云 TRTC（群通话） |
| 认证 | JWT（java-jwt） |
| 序列化 | FastJSON、Gson、Protobuf |
| 构建 | Maven（多模块） |

> **版本注意事项**（踩过的坑，改动前先读根 `pom.xml` 注释）：
> - 不要重定义 `netty.version` —— 该属性名与 Spring Boot BOM 内部 import netty-bom 的属性同名，覆盖会把全工程 Netty 降级，与 reactor-netty 所需的 Netty 4.2 冲突。
> - 不要重定义 `junit-jupiter.version` —— 它是 SB4 BOM 用来 import junit-bom 的属性，降到 JUnit 5 会让 spring-test 7 的 `SpringExtension` 在 beforeAll 抛 `NoSuchMethodError`。

## 模块划分

工程按职责拆分为 9 个 Maven 模块（`link-register` 下再含 server / client 两个子模块），构建顺序由依赖关系自动推导。

| 模块 | 职责 | 产物 |
|------|------|------|
| `link-common` | 通用基础：注解、序列化、协议模型、事件枚举、MQ 契约、ID 生成、Redis 常量、编解码工具 | 库 |
| `link-core` | IM 引擎：Netty 服务端、协议编解码、pipeline 处理器、事件分发、会话、发送器、seq 分配、连接安全 | 库 |
| `link-im` | 业务领域：实体、业务事件处理（消息 / 好友 / 转发 / RTC）、消息处理器、MongoDB 访问、领域服务 | 库 |
| `link-consumer` | 推送指令消费者：`@RabbitListener` 消费 `DirectPushCommand`，必须与 Netty Channel 同 JVM | 库 |
| `link-restapi` | HTTP 业务层：用户、好友、会话、群组、消息、钱包、红包、TRTC 等 REST 接口 | **可执行 jar** |
| `link-server-starter` | 长连接接入进程：组装并启动 Netty，绑定 TCP / WebSocket 端口，内嵌推送消费者 | **可执行 jar** |
| `link-gateway` | 统一 HTTP 入口：路由、JWT 全局鉴权、CORS | **可执行 jar** |
| `link-oss` | 对象存储服务：腾讯云 COS 上传 | **可执行 jar** |
| `link-register` | 自研注册中心：`link-register-server`（gRPC 服务端 + 健康检查）/ `link-register-client`（SDK） | **可执行 jar** + 库 |

### 可执行进程

| 进程 | 启动类 | 默认端口 | 说明 |
|------|--------|---------|------|
| `link-gateway` | `LinkGatewayApplication` | 88 | 统一 HTTP 入口，`/api/**` → restapi，`/api/oss/**` → oss |
| `link-restapi` | `LinkApiApplication` | 8080 | 只跑 HTTP，未开启 Netty；需在线推送时发 MQ 指令 |
| `link-server-starter` | `LinkImApplication` | 8899 | classpath 无 webmvc，只跑长连接；靠 `link.netty.enabled=true` 让 `DefaultServer` 绑定端口 |
| `link-oss` | `LinkOssApplication` | 9000 | 文件上传 |
| `link-register-server` | `RegisterServerApplication` | 9500（gRPC） | 纯 gRPC，`web-application-type: none`，不起 Tomcat |

## 架构概览

```
                        客户端
              ┌───────────┴────────────┐
     HTTP     │                        │   TCP / WebSocket
              ▼                        ▼
    ┌──────────────────┐     ┌────────────────────────────────┐
    │  link-gateway    │     │  Netty Pipeline                 │
    │  路由 / JWT / CORS│     │  ┌──────────────┐ WS 时前置     │
    └───────┬──────────┘     │  │ WS 适配 / 握手│               │
       ┌────┴─────┐          │  └──────┬───────┘               │
       ▼          ▼          │         ▼                       │
  ┌─────────┐ ┌────────┐     │  PackData 编解码（魔数 + 帧长）  │
  │ restapi │ │  oss   │     │         ▼                       │
  └────┬────┘ └────────┘     │  IdleHandler 心跳检测            │
       │                     │         ▼                       │
       │  DirectPushCommand  │  Inbound/Outbound 业务 Handler   │
       │  （fanout 广播）     └────────┬───────────────────────┘
       ▼                              ▼
  ┌──────────┐   消费    ┌──────────────────────────┐
  │ RabbitMQ │──────────▶│ LinkEventDispatcher      │
  └──────────┘           │  └ 认证门禁 ──▶ 拒绝/关闭 │
                         └────────┬─────────────────┘
                                  ▼  (按 action 路由 + 线程池异步)
                         EventHandlerFactory ──▶ EventHandler
                                  │              └ RTC / GroupRTC 二级工厂
                                  ▼
                         Session / Sender / 业务 Processor
                                  ▼
                            Redis  /  MongoDB

  服务发现：各进程 → link-register-server (gRPC) 或 Nacos
```

## 目录结构

```
cc-link
├── pom.xml                     # 聚合 + 依赖/插件版本统管
├── docs/
│   ├── im-fanout-architecture.md          # 消息扩散架构说明
│   └── mongo/migrate-bigdecimal-to-decimal128.js
├── link-common/                # 通用：注解、序列化、协议模型、事件枚举、MQ 契约、ID
│   └── com/link/common
│       ├── core/event/         #   EventType / RTCEventType / GroupRTCEventType
│       ├── core/mq/            #   DirectPushCommand、PushMqConst
│       └── channel/            #   Channel 属性键
├── link-core/                  # IM 引擎：Netty 服务端 + 协议 + 事件 + 会话 + 安全
│   └── com/link/core
│       ├── server/             #   Netty 服务端启动
│       ├── codec/              #   协议编解码
│       ├── handler/            #   pipeline 处理器（tcp/ws/idle/security）
│       ├── event/              #   事件分发器、Handler 工厂（含 RTC 二级工厂）
│       ├── session/            #   会话工厂 / 管理 / 服务
│       ├── sender/             #   消息发送
│       └── security/           #   连接安全管理
├── link-im/                    # 业务领域：实体、业务 Handler、处理器、Mongo、服务
│   └── com/link/im
│       ├── entity/             #   message / chat / group / friend / wallet / redpack / data
│       ├── handler/            #   消息、好友、转发、RTC（call/ 下 8 个子 Handler）
│       └── mongo/ service/
├── link-consumer/              # RabbitMQ 推送指令消费者（与 Netty 同 JVM）
├── link-restapi/               # HTTP 接口 → 可执行 jar
│   └── com/link
│       ├── LinkApiApplication.java
│       ├── cron/               #   红包过期退款、钱包定时任务
│       └── restapi/module/     #   user / friend / chat / group / message / wallet / redpack / trtc
├── link-server-starter/        # 长连接接入进程 → 可执行 jar
│   └── com/link/LinkImApplication.java
├── link-gateway/               # 统一入口：路由 + JWT 全局过滤 → 可执行 jar
├── link-oss/                   # 腾讯云 COS 上传 → 可执行 jar
└── link-register/              # 自研注册中心
    ├── link-register-server/   #   gRPC 服务端 + 健康检查 → 可执行 jar
    └── link-register-client/   #   客户端 SDK（registry.proto）
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
| 1014 | REMOVE_GROUP_MEMBER | 移除群成员 |
| 1015 | JOIN_GROUP | 加入群聊 |
| 1016 | NOTICE_MESSAGE | 群通知消息 |
| 1017 | SINGLE_FORWARD | 逐条转发 |
| 1018 | MERGE_FORWARD | 合并转发 |
| 1019 | RTC_CALL | 1v1 通话信令（自建 WebRTC） |
| 1020 | RTC_GROUP_CALL | 群通话状态上报（TRTC） |
| 1021 | USER_EXIT | 用户退出 |
| 1022 | UPDATE_RED_PACKET | 红包状态更新 |

### RTC 子事件

`RTC_CALL(1019)` 的 `eventType` 字段（`RTCEventType`）：

| 值 | 事件 | 值 | 事件 |
|----|------|----|------|
| 1 | CALL 呼叫 | 5 | ANSWER 应答 SDP |
| 2 | ACCEPT 接听 | 6 | CANDIDATE ICE 候选 |
| 3 | CANCEL 取消 | 7 | HANG_UP 挂断 |
| 4 | OFFER 发起 SDP | 8 | UNANSWERED 未接听 |

`RTC_GROUP_CALL(1020)` 的 `eventType` 字段（`GroupRTCEventType`）：`1 CALL` / `2 RINGING` / `3 ACCEPT` / `4 REJECT` / `5 USER_ENTER` / `6 USER_LEAVE` / `7 NO_RESPONSE` / `8 LINE_BUSY` / `9 HANG_UP` / `10 NOT_CONNECTED` / `11 INVITE`。

### 消息类型（type）

| type | 类型 | data 类 | 会话摘要 | 可引用 |
|------|------|---------|---------|-------|
| 1000 | SYSTEM_MESSAGE | `SystemData` | — | 否 |
| 1001 | TEXT_MESSAGE | `TextData` | 正文（截断 30 字） | 是 |
| 1002 | IMAGE_MESSAGE | `ImageData` | `[图片]` | 是 |
| 1003 | VIDEO_MESSAGE | `VideoData` | `[视频]` | 是 |
| 1004 | VOICE_MESSAGE | `VoiceData` | `[语音]` | 否 |
| 1005 | RED_PACK_MESSAGE | `RedPackData` | `[红包]` | 否 |
| 1006 | NOTICE_MESSAGE | `NoticeData` | — | 否 |
| 1007 | RTC_CALL_MESSAGE | `CallData` | `[语音通话]` / `[视频通话]` | 否 |

### REST 接口一览

网关统一前缀 `/api`，转发时剥掉一层（`StripPrefix=1`）。

| 前缀 | 模块 | 主要接口 |
|------|------|---------|
| `/user` | 用户 | `auth` 登录、`register` 注册 |
| `/friend` | 好友 | 好友列表、申请、审批 |
| `/chat` | 会话 | 会话列表、会话成员 |
| `/group` | 群组 | `create-group`、`join-group`、`remove-member`、`get-member`、`add-administrator` |
| `/message` | 消息 | `pull-message` 拉取历史（读扩散 + 空档过滤） |
| `/wallet` | 钱包 | `init-wallet`、`myself-wallet`、`top-up`、`withdraw`、`created-top` |
| `/red-pack` | 红包 | `send-packet`、`grab-packet`、`get-red-packet` |
| `/trtc` | 群通话 | `user-sig` 签发凭证、`call-back` 腾讯云回调 |
| `/oss` | 对象存储 | `upload` 文件上传（走 `link-oss` 进程） |

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.6+（或直接用仓库内的 `mvnw`）
- Redis
- MongoDB **副本集**（红包 / 钱包用到多文档事务，单机模式不支持）
- RabbitMQ
- 可选：Nacos 2.4+ / 3.x（1.x 无 gRPC，nacos-client 3.x 注册会失败），或直接用自研 `link-register-server`
- 可选：腾讯云 COS（文件上传）、TRTC（群通话）账号

### 配置

各进程 `application.yml` 中的连接信息均支持环境变量覆盖。关键变量：

| 变量 | 用途 | 默认 |
|------|------|------|
| `REDIS_HOST` | Redis 地址 | `localhost` |
| `MONGO_URI` | MongoDB 连接串 | 见各模块 yml |
| `RABBITMQ_HOST` / `_PORT` / `_USERNAME` / `_PASSWORD` / `_VHOST` | RabbitMQ | — |
| `API_PORT` | restapi / oss HTTP 端口 | `8080` / `9000` |
| `GATEWAY_PORT` | 网关端口 | `88` |
| `NACOS_ADDR` | Nacos 地址（`discovery.enabled` 默认 `false`） | `127.0.0.1:8848` |
| `REGISTER_PORT` | 自研注册中心 gRPC 端口 | `9500` |
| `COS_SECRET_ID` / `COS_SECRET_KEY` / `COS_REGION` / `COS_BUCKET_NAME` / `COS_BASE_URL` | 腾讯云 COS | — |
| `TRTC_SDK_APP_ID` / `TRTC_SECRET_KEY` / `TRTC_EXPIRE_SECONDS` | 腾讯云 TRTC | — |

两处必须两个写库进程保持一致的 MongoDB 配置：

```yaml
spring:
  data:
    mongodb:
      auto-index-creation: true      # 红包唯一索引靠它建；不开则重复扣款/一人多领全靠运气
      representation:
        big-decimal: decimal128      # 不设则 BigDecimal 存成字符串，$inc 报错、金额比较退化成字典序
```

IM 引擎参数（端口、协议、线程、安全策略等）见 `LinkCoreConfig`，可用 `link.*` 前缀覆盖，关键项：

```java
private short magic = 0x59C3;            // 协议魔数
private int port = 8899;                 // 监听端口
private Protocol protocol = WEBSOCKET;   // TCP / WEBSOCKET
private String websocketPath = "/ws";    // WebSocket 握手路径
private int groupPushLimit = 50;         // 群消息单批推送上限
private int authTimeoutSeconds = 10;     // 认证超时
private int maxConnPerIp = 100;          // 单 IP 最大连接数
private int maxConnections = 100000;     // 全局最大连接数
private int maxFrameLength = 2*1024*1024;// 单帧最大长度
```

### 构建

```bash
# 编译并安装全部模块（在仓库根目录执行）
./mvnw clean install
```

### 运行

各进程独立启动，可分别部署。最小可用组合为 **restapi + server-starter**；完整链路再加网关、oss、注册中心：

```bash
# 1) 长连接接入进程（Netty，绑定 TCP / WebSocket 端口，内嵌 MQ 推送消费者）
./mvnw -pl link-server-starter spring-boot:run

# 2) HTTP 业务进程（REST API）
./mvnw -pl link-restapi spring-boot:run

# 3) 网关（统一 HTTP 入口）
./mvnw -pl link-gateway spring-boot:run

# 4) 对象存储服务
./mvnw -pl link-oss spring-boot:run

# 5) 自研注册中心（gRPC）
./mvnw -pl link-register/link-register-server spring-boot:run
```

或直接跑打好的 jar：

```bash
java -jar link-server-starter/target/link-server-starter-0.0.1-SNAPSHOT.jar
```

长连接进程启动后控制台输出：`Link IM Server started on port 8899`。

### 首次部署清单

1. MongoDB 起副本集（红包/钱包事务需要），并确认连的是 PRIMARY。
2. 跑一次 `docs/mongo/migrate-bigdecimal-to-decimal128.js` 迁移存量金额字段（新库可跳过）。
3. RabbitMQ 创建好账号与 vhost；交换机与队列由 `@RabbitListener` 自动声明，无需手工建。
4. 按上表注入环境变量，尤其是 COS / TRTC 密钥。



## 文档

- [消息扩散架构](docs/im-fanout-architecture.md)：读扩散 / 投递扇出 / 未读计算 / blackout gap 模型。

## 安全提示

> ⚠️ **当前仓库内多处 `application.yml` 的环境变量默认值里写着可用的真实凭据**（MongoDB、RabbitMQ 账号密码，腾讯云 COS SecretId/SecretKey，TRTC SecretKey）。推送到公开仓库前必须先处理：

- 把这些默认值清空或换成占位符，只保留 `${VAR}` 形式，凭据全部走环境变量 / 配置中心注入。
- 轮换所有曾经提交过的密钥——已进入 git 历史的凭据视同泄露，仅删除当前文件不够。
- TRTC SecretKey 泄露意味着任何人都能签出任意 userID 的 UserSig，以他人身份登录腾讯云 IM。
- 确认 `.gitignore` 已排除本地敏感配置。

## License

Apache License 2.0，见 [LICENSE](LICENSE)。
