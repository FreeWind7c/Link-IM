# Link-Base 模块

## 模块定位

link-base 是数据访问层的抽象模块，统一管理：
- **实体类 (Entity)**：MongoDB 文档实体
- **DTO (Data Transfer Object)**：数据传输对象
- **VO (Value Object)**：视图对象
- **MongoDB 抽象服务**：`BasePlatFormMongoService`
- **Redis 抽象服务**：`BasePlatFormRedisService`

## 设计原则

**依赖注入 + 多数据源支持**

- ✅ link-base **不配置** MongoDB 和 Redis 连接
- ✅ 由调用方（link-im、link-restapi）各自配置并注入
- ✅ 支持不同模块使用不同地址的 MongoDB/Redis

## 使用方式

### 1. MongoDB 操作

#### 在 link-base 中定义实体和服务

```java
// 实体类
@Document(collection = "chat_messages")
public class ChatMessage extends BaseMessage {
    private String content;
    // ...
}

// 服务类
@Service
public class ChatMessageService extends BasePlatFormMongoService<ChatMessage> {
    
    public ChatMessage findByMessageId(String messageId) {
        return findOne(eq("messageId", messageId));
    }
    
    public List<ChatMessage> findBySessionId(String sessionId) {
        return find(eq("sessionId", sessionId));
    }
}
```

#### 在 link-im 中配置 MongoDB

```java
@Configuration
public class MongoConfig {
    
    @Value("${spring.mongodb.uri}")
    private String mongoUri;
    
    @Bean
    @Primary
    public MongoClient mongoClient() {
        return MongoClients.create(mongoUri);
    }
    
    @Bean
    @Primary
    public MongoDatabaseFactory mongoDatabaseFactory(MongoClient mongoClient) {
        return new SimpleMongoClientDatabaseFactory(mongoClient, "im");
    }
    
    @Bean
    @Primary
    public MongoTemplate mongoTemplate(MongoDatabaseFactory factory, MongoConverter converter) {
        return new MongoTemplate(factory, converter);
    }
    
    // 事务专用的 Primary Template
    @Bean("primaryMongoTemplate")
    public MongoTemplate primaryMongoTemplate(
            @Qualifier("primaryMongoDatabaseFactory") MongoDatabaseFactory factory,
            MongoConverter converter) {
        return new MongoTemplate(factory, converter);
    }
}
```

**application.yml**
```yaml
spring:
  mongodb:
    uri: mongodb://localhost:27017/im?readPreference=secondaryPreferred
```

#### 在 link-restapi 中配置不同的 MongoDB

```java
@Configuration
public class RestApiMongoConfig {
    
    @Value("${restapi.mongodb.uri}")
    private String mongoUri;
    
    @Bean
    @Primary
    public MongoTemplate mongoTemplate() {
        MongoClient client = MongoClients.create(mongoUri);
        return new MongoTemplate(new SimpleMongoClientDatabaseFactory(client, "restapi"));
    }
}
```

**application.yml**
```yaml
restapi:
  mongodb:
    uri: mongodb://192.168.1.100:27017/restapi
```

### 2. Redis 操作

#### 在 link-base 中定义服务

```java
@Service
public class UserCacheService extends BasePlatFormRedisService {
    
    public void cacheUser(String userId, User user) {
        set("user:" + userId, user, 30, TimeUnit.MINUTES);
    }
    
    public User getUser(String userId) {
        return (User) get("user:" + userId);
    }
    
    public void cacheUserOnline(String userId) {
        sAdd("online:users", userId);
    }
}
```

#### 在 link-im 中配置 Redis

```java
@Configuration
public class RedisConfig {
    
    @Value("${spring.redis.host}")
    private String host;
    
    @Value("${spring.redis.port}")
    private int port;
    
    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(host);
        config.setPort(port);
        return new LettuceConnectionFactory(config);
    }
    
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        
        // 使用 Jackson 序列化
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(Object.class);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        
        return template;
    }
}
```

**application.yml**
```yaml
spring:
  redis:
    host: localhost
    port: 6379
```

## 依赖关系

```
link-im          → link-base (使用 MongoDB/Redis 服务)
  ↓ 配置注入
MongoTemplate
RedisTemplate

link-restapi     → link-base (使用 MongoDB/Redis 服务)
  ↓ 配置注入
MongoTemplate
RedisTemplate
```

## 核心优势

1. **统一管理**：所有实体类、DTO、VO 集中在 link-base
2. **多数据源**：不同模块可以连接不同的 MongoDB/Redis
3. **代码复用**：通用的数据访问逻辑只写一次
4. **灵活配置**：调用方完全控制连接参数和连接池配置
5. **类型安全**：`BasePlatFormMongoService<T>` 提供泛型支持

## 注意事项

1. **不要在 link-base 中配置数据源**
   - ❌ 不要在 link-base 添加 `@ConfigurationProperties`
   - ❌ 不要在 link-base 创建 `MongoClient` 或 `RedisConnectionFactory`
   
2. **使用 @Primary 标记默认 Bean**
   - 当有多个 MongoTemplate 时，用 `@Primary` 标记默认的
   - 其他的用 `@Qualifier` 区分

3. **事务支持**
   - 使用 `primaryMongoTemplate` 进行事务操作
   - 通过 `@Qualifier("primaryMongoTemplate")` 注入

4. **序列化配置**
   - RedisTemplate 的序列化器由调用方配置
   - 建议使用 Jackson2JsonRedisSerializer
