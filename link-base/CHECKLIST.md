# Link-Base 模块改造清单

## ✅ 已完成的工作

### 1. 模块结构
- ✅ 创建 `link-base` 模块
- ✅ 移动实体类到 `link-base/entity/`
- ✅ 移动 DTO 到 `link-base/dto/`
- ✅ 移动 VO 到 `link-base/vo/`

### 2. MongoDB 抽象层
- ✅ `BasePlatFormMongoService<T>` - MongoDB 操作基类
  - 支持通过 `@Autowired` 注入外部 MongoTemplate
  - 支持 `@Qualifier("primaryMongoTemplate")` 注入事务专用 Template
  - 提供泛型类型安全的 CRUD 操作
  - 提供 Lambda 表达式字段名引用（避免硬编码字符串）

### 3. Redis 抽象层
- ✅ `BasePlatFormRedisService` - Redis 操作基类
  - 支持通过 `@Autowired` 注入外部 RedisTemplate
  - 封装 String/Hash/List/Set/ZSet 常用操作
  - 提供过期时间管理方法

### 4. 依赖配置
- ✅ link-base 的 pom.xml 添加必要依赖：
  - `spring-boot-starter-data-mongodb`
  - `spring-boot-starter-data-redis`
- ✅ 注释说明：本模块不配置数据源，由调用方注入

### 5. 配置示例
- ✅ link-im 的 `MongoConfig.java` 已配置（支持主从读写分离）
- ✅ link-im 的 `RedisConfig.java` 已配置（Jackson 序列化）

### 6. 文档
- ✅ `link-base/README.md` - 完整的使用文档
- ✅ `USAGE_EXAMPLE.java` - 详细的代码示例

## 🔍 需要验证的点

### 1. 依赖检查
```bash
# 检查 link-im 是否依赖了 link-base
cd link-im
grep -A 5 "link-base" pom.xml
```

### 2. Spring 扫描路径
确保 link-im 和 link-restapi 的启动类能扫描到 link-base 的包：

```java
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.link.base",      // 当前模块
    "com.link.base"     // link-base 模块
})
public class LinkImApplication {
    // ...
}
```

### 3. 编译验证
```bash
# 先编译 link-base
cd link-base
mvn clean install

# 再编译 link-im
cd ../link-im
mvn clean compile
```

### 4. 运行验证
启动 link-im 后，检查日志是否有：
- ✅ MongoTemplate 注入成功
- ✅ RedisTemplate 注入成功
- ✅ link-base 的 @Service 被扫描并注册

## 📋 使用示例

### 示例 1：在 link-base 中创建新的 MongoDB Service

```java
package com.link.base.service;

import com.link.base.entity.chat.ChatSession;
import com.link.base.mongo.BasePlatFormMongoService;
import org.springframework.stereotype.Service;

@Service
public class ChatSessionService extends BasePlatFormMongoService<ChatSession> {
    
    public ChatSession findBySessionId(String sessionId) {
        return findOne(eq("sessionId", sessionId));
    }
}
```

### 示例 2：在 link-base 中创建新的 Redis Service

```java
package com.link.base.service;

import com.link.base.redis.BasePlatFormRedisService;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service
public class TokenCacheService extends BasePlatFormRedisService {
    
    public void cacheToken(String userId, String token) {
        set("token:" + userId, token, 2, TimeUnit.HOURS);
    }
    
    public String getToken(String userId) {
        return (String) get("token:" + userId);
    }
}
```

### 示例 3：在 link-im 中使用（自动注入）

```java
package com.link.base.handler;

import com.link.base.service.ChatSessionService;
import com.link.base.service.TokenCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MessageHandler {
    
    @Autowired
    private ChatSessionService chatSessionService;  // 来自 link-base
    
    @Autowired
    private TokenCacheService tokenCacheService;    // 来自 link-base
    
    public void handleMessage(String sessionId, String userId) {
        // 使用 link-im 配置的 MongoDB
        var session = chatSessionService.findBySessionId(sessionId);
        
        // 使用 link-im 配置的 Redis
        var token = tokenCacheService.getToken(userId);
    }
}
```

## 🎯 架构优势

### 1. 多数据源支持
```
link-im:
  MongoDB: mongodb://im-mongo:27017/im
  Redis:   redis://im-redis:6379

link-restapi:
  MongoDB: mongodb://api-mongo:27017/restapi
  Redis:   redis://api-redis:6379

两者使用相同的 link-base Service，但操作不同的数据库实例
```

### 2. 统一管理
- 所有实体定义在一个地方
- 所有数据访问逻辑在一个地方
- 减少代码重复，提高可维护性

### 3. 灵活配置
- 开发环境：连接本地 MongoDB/Redis
- 测试环境：连接测试集群
- 生产环境：连接生产集群
- 只需修改各模块的配置文件，无需改动 link-base 代码

## ⚠️ 注意事项

### 1. 绝对不要在 link-base 中配置数据源
❌ **错误做法**：
```java
// 不要在 link-base 中这样做！
@Configuration
public class BaseMongoConfig {
    @Value("${mongodb.uri}")  // ❌ 不要这样
    private String uri;
    
    @Bean
    public MongoTemplate mongoTemplate() {  // ❌ 不要这样
        return new MongoTemplate(...);
    }
}
```

✅ **正确做法**：
```java
// link-base 只提供抽象服务类
public abstract class BasePlatFormMongoService<T> {
    @Autowired
    protected MongoTemplate mongo;  // ✅ 只声明注入
}
```

### 2. 使用 @Primary 避免歧义
当有多个相同类型的 Bean 时：
```java
@Bean
@Primary  // 默认注入这个
public MongoTemplate mongoTemplate() { ... }

@Bean("primaryMongoTemplate")  // 需要时用 @Qualifier 指定
public MongoTemplate primaryMongoTemplate() { ... }
```

### 3. 包扫描配置
确保启动类能扫描到 link-base 的 @Service：
```java
@SpringBootApplication
@ComponentScan(basePackages = {"com.link.base", "com.link.base"})
public class LinkImApplication { }
```

## 🚀 下一步建议

1. **测试验证**
   - 编译 link-base 模块
   - 在 link-im 中注入并测试
   - 验证多数据源场景

2. **迁移现有 Service**
   - 将 link-im 中的 MongoDB Service 迁移到 link-base
   - 让它们继承 `BasePlatFormMongoService<T>`

3. **统一 Redis 操作**
   - 将分散的 Redis 操作封装成 Service
   - 继承 `BasePlatFormRedisService`

4. **文档完善**
   - 补充更多实际业务场景的示例
   - 添加常见问题解答（FAQ）
