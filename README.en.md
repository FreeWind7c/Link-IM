# Link IM

A high-performance instant-messaging (IM) server built on **Netty + Spring Boot**, using a custom binary protocol over dual TCP / WebSocket transports. Event-driven architecture, split into Maven modules so the long-connection access layer and the HTTP business layer can be deployed independently.

[中文文档](./README.md)

## Features

- **Dual transport**: codec and business handlers are fully shared; only the pipeline front-end differs, so one codebase serves both **TCP** and **WebSocket**, switchable by config.
- **Custom binary protocol**: `magic(2B) + action(2B) + length(4B) + body`, with magic-number and frame-length validation to reject malformed / oversized packets at the source.
- **Event-driven**: routes by `action` to the matching `EventHandler`, registered via a factory; adding a business event means implementing one handler.
- **Connection safety**: auth gate (unauthenticated connections may only send LOGIN), auth-timeout eviction, per-IP connection cap, global connection cap, max frame length.
- **Session management**: bidirectional Channel ↔ user-session mapping, multi-device login and targeted push.
- **Heartbeat**: `IdleStateHandler`-based read/write idle detection, auto-cleanup of zombie connections.
- **Pluggable serialization**: JSON by default (FastJSON / Gson), Protobuf supported, polymorphic message-body deserialization.
- **Business capabilities**: single chat, group chat, friend relations (add / approve), multiple message types (text / image / video / voice / red packet).
- **REST API**: HTTP endpoints for sessions, friends, messages and users, sharing one domain model with the long-connection service.
- **Storage**: Redis (session routing / cache) + MongoDB (message / relation persistence).

## Tech Stack

| Category | Tech |
|----------|------|
| Language | Java 21 |
| Framework | Spring Boot 4.0.6 |
| Network | Netty 4.1.119 |
| Storage | Redis, MongoDB |
| Auth | JWT (java-jwt) |
| Serialization | FastJSON, Gson, Protobuf |
| Build | Maven (multi-module) |

## Modules

The project is split into 5 Maven modules; build order is derived from dependencies: `common → im → core → restapi / server-starter`.

| Module | Responsibility | Artifact |
|--------|----------------|----------|
| `link-common` | Shared basics: annotations, serialization, protocol models, ID generation, Redis constants, codec utils | library |
| `link-im` | Business domain: entities, business event handlers, message processors, MongoDB access, domain services | library |
| `link-core` | IM engine: Netty server, protocol codec, pipeline handlers, event dispatch, sessions, sender, connection safety | library |
| `link-restapi` | HTTP layer: register/login, friends, sessions, history pull, etc. | **executable jar** |
| `link-server-starter` | Long-connection access process: assembles and starts Netty, binds TCP / WebSocket ports | **executable jar** |

The two executable modules start independently:

- **`link-server-starter`** → entry class `LinkImApplication`. Depends on `link-im` (which transitively pulls in the `link-core` engine); no webmvc on the classpath, so it runs **long connections only**. `link.netty.enabled=true` activates `DefaultServer` and binds the ports.
- **`link-restapi`** → entry class `LinkApiApplication`. Runs **HTTP only**, Netty disabled. Note: within this process the "remote login" check and post-friend-approval online push are degraded (they need a Redis online table + MQ to work across processes).

## Protocol

| Field | Type | Description |
|-------|------|-------------|
| magic | short (2B) | Magic number, default `0x59C3`; mismatch closes the connection |
| action | short (2B) | Event type, see `EventType` |
| length | int (4B) | body byte length; over-limit closes the connection |
| body | bytes | Serialized business payload |

### Event types (action)

| action | Event | Description |
|--------|-------|-------------|
| 1 | HEARTBEAT | Heartbeat |
| 2 | ACK | Acknowledgement |
| 1001 | LOGIN | Login / auth |
| 1002 | LOGOUT | Logout |
| 1010 | DEFAULT_MESSAGE | Single chat message |
| 1011 | GROUP_MESSAGE | Group chat message |
| 1012 | ADD_FRIEND | Add friend |
| 1013 | APPROVE_FRIEND | Approve friend |

## Getting Started

### Requirements

- JDK 21+
- Maven 3.6+ (or the bundled `mvnw`)
- Redis
- MongoDB

### Configuration

Connection settings in each executable module's `application.yml` are overridable via environment variables (`REDIS_HOST`, `MONGO_URI`, `API_PORT`). Do not hard-code real production credentials.

### Build

```bash
# From the repository root
./mvnw clean install
```

### Run

The two processes start independently and can be deployed separately:

```bash
# 1) Long-connection access process (Netty, binds TCP / WebSocket)
./mvnw -pl link-server-starter spring-boot:run
# or: java -jar link-server-starter/target/link-server-starter-0.0.1-SNAPSHOT.jar

# 2) HTTP business process (REST API)
./mvnw -pl link-restapi spring-boot:run
# or: java -jar link-restapi/target/link-restapi-0.0.1-SNAPSHOT.jar
```

On startup the long-connection process logs: `Link IM Server started on port 8899`.

## Contribution

1. Fork the repository
2. Create a `Feat_xxx` branch
3. Commit your code
4. Create a Pull Request

## License

TBD.
