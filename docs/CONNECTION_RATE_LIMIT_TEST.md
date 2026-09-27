# 连接限流测试指南

## 测试目标

验证 Netty 连接限流器能够有效防止服务重启后的雷击效应（10万连接同时重连）。

---

## 测试环境准备

### 1. 修改配置调低限流阈值（方便测试）

```yaml
# link-server-starter/src/main/resources/application.yml
link:
  core:
    connection-rate-limit-enabled: true
    connection-rate-limit-per-second: 10   # 测试用：每秒只允许10个连接
    connection-rate-limit-block: false     # 立即拒绝模式
```

### 2. 启动服务

```bash
mvn clean package
java -jar link-server-starter/target/link-server-starter.jar
```

观察日志，应该看到：
```
✅ 连接限流器已启用：每秒 10.0 个连接，阻塞模式=false，最大等待=1000ms
Link IM Server started on port 9000
```

---

## 测试方案1：使用 WebSocket 压测工具

### 使用 `wscat` 工具

```bash
# 安装 wscat
npm install -g wscat

# 手动测试单个连接
wscat -c ws://localhost:9000/ws
```

### 使用脚本模拟并发连接

创建 `test_concurrent_connections.js`：

```javascript
const WebSocket = require('ws');

// 配置
const WS_URL = 'ws://localhost:9000/ws';
const TOTAL_CONNECTIONS = 100;      // 总连接数
const CONCURRENT_BATCH = 50;        // 每批并发数

let successCount = 0;
let failedCount = 0;
let startTime = Date.now();

function createConnection(id) {
  return new Promise((resolve) => {
    const ws = new WebSocket(WS_URL);
    
    ws.on('open', () => {
      successCount++;
      console.log(`✅ [${id}] 连接成功 (总成功: ${successCount})`);
      ws.close();
      resolve('success');
    });
    
    ws.on('error', (err) => {
      failedCount++;
      console.log(`❌ [${id}] 连接失败 (总失败: ${failedCount}) - ${err.message}`);
      resolve('failed');
    });
    
    ws.on('close', () => {
      resolve('closed');
    });
    
    // 5秒超时
    setTimeout(() => {
      if (ws.readyState === WebSocket.CONNECTING) {
        failedCount++;
        console.log(`⏰ [${id}] 连接超时 (总失败: ${failedCount})`);
        ws.terminate();
        resolve('timeout');
      }
    }, 5000);
  });
}

async function runTest() {
  console.log(`开始测试：尝试建立 ${TOTAL_CONNECTIONS} 个并发连接`);
  console.log(`限流配置：每秒最多 10 个连接`);
  console.log(`预期结果：前 10 个成功，其余被拒绝\n`);
  
  const promises = [];
  
  // 批量创建连接
  for (let i = 0; i < TOTAL_CONNECTIONS; i++) {
    promises.push(createConnection(i + 1));
    
    // 每批并发数限制
    if ((i + 1) % CONCURRENT_BATCH === 0) {
      await Promise.all(promises.splice(0, promises.length));
      console.log(`--- 完成第 ${(i + 1) / CONCURRENT_BATCH} 批 ---\n`);
    }
  }
  
  // 等待剩余的连接
  if (promises.length > 0) {
    await Promise.all(promises);
  }
  
  const duration = ((Date.now() - startTime) / 1000).toFixed(2);
  
  console.log('\n========== 测试结果 ==========');
  console.log(`总耗时: ${duration} 秒`);
  console.log(`成功连接: ${successCount}`);
  console.log(`失败连接: ${failedCount}`);
  console.log(`成功率: ${((successCount / TOTAL_CONNECTIONS) * 100).toFixed(2)}%`);
  
  if (failedCount > 80) {
    console.log('\n✅ 限流器工作正常：大部分连接被拒绝');
  } else {
    console.log('\n⚠️ 限流器可能未生效：成功连接过多');
  }
}

runTest();
```

运行测试：
```bash
npm install ws
node test_concurrent_connections.js
```

**预期结果**：
```
开始测试：尝试建立 100 个并发连接
限流配置：每秒最多 10 个连接
预期结果：前 10 个成功，其余被拒绝

✅ [1] 连接成功 (总成功: 1)
✅ [2] 连接成功 (总成功: 2)
...
✅ [10] 连接成功 (总成功: 10)
❌ [11] 连接失败 (总失败: 1) - socket hang up
❌ [12] 连接失败 (总失败: 2) - socket hang up
...

========== 测试结果 ==========
总耗时: 1.23 秒
成功连接: 10
失败连接: 90
成功率: 10.00%

✅ 限流器工作正常：大部分连接被拒绝
```

---

## 测试方案2：模拟服务重启场景

### 步骤

1. **启动服务并建立100个客户端连接**

```javascript
// keep_alive_clients.js
const WebSocket = require('ws');

const WS_URL = 'ws://localhost:9000/ws';
const CLIENT_COUNT = 100;
const clients = [];

for (let i = 0; i < CLIENT_COUNT; i++) {
  const ws = new WebSocket(WS_URL);
  
  ws.on('open', () => {
    console.log(`客户端 ${i + 1} 已连接`);
    
    // 发送登录消息（根据你的协议）
    // ws.send(JSON.stringify({ action: 1001, body: { token: 'test-token' } }));
  });
  
  ws.on('message', (data) => {
    const msg = JSON.parse(data);
    
    // 收到服务维护通知
    if (msg.action === 1024) {
      console.log(`客户端 ${i + 1} 收到服务维护通知: ${msg.body.message}`);
      
      // 模拟客户端重连（5秒后）
      setTimeout(() => {
        console.log(`客户端 ${i + 1} 开始重连...`);
        reconnect(i + 1);
      }, 5000);
    }
  });
  
  ws.on('close', () => {
    console.log(`客户端 ${i + 1} 连接已关闭`);
  });
  
  clients.push(ws);
}

function reconnect(id) {
  const ws = new WebSocket(WS_URL);
  
  ws.on('open', () => {
    console.log(`✅ 客户端 ${id} 重连成功`);
  });
  
  ws.on('error', (err) => {
    console.log(`❌ 客户端 ${id} 重连失败: ${err.message}`);
  });
}

console.log(`启动 ${CLIENT_COUNT} 个客户端...`);
```

2. **优雅重启服务**

在另一个终端执行：
```bash
# 查找进程
jps | grep LinkImApplication

# 优雅停服（发送 SIGTERM）
kill -15 <PID>
```

3. **观察服务端日志**

应该看到：
```
==================== 开始优雅停服 ====================
停止接受新连接...
开始优雅停服流程，当前在线会话数: 100
维护通知发送完成，成功: 100/100
等待 20 秒让用户自然断开连接...
所有用户已自然断开，优雅停服完成
关闭 Netty EventLoopGroup...
==================== 优雅停服完成 ====================
```

4. **重新启动服务**

```bash
java -jar link-server-starter/target/link-server-starter.jar
```

5. **观察重连日志**

应该看到限流器起作用：
```
✅ 连接通过限流检查（立即获取），远程地址: /127.0.0.1:52341
✅ 连接通过限流检查（立即获取），远程地址: /127.0.0.1:52342
...
🚫 连接限流触发（立即拒绝），拒绝连接: /127.0.0.1:52351
🚫 连接限流触发（立即拒绝），拒绝连接: /127.0.0.1:52352
...
```

客户端会看到部分连接失败，然后按指数退避重试，最终全部连接成功。

---

## 测试方案3：使用 JMeter 压测

### 1. 下载 JMeter WebSocket 插件

https://github.com/Blazemeter/JMeter-WebSocketSamplers

### 2. 创建测试计划

- 添加线程组：100 个线程，1 秒内启动
- 添加 WebSocket Sampler
  - Server: localhost
  - Port: 9000
  - Path: /ws
- 添加查看结果树

### 3. 运行测试

观察成功率，应该只有 10% 左右（每秒10个）成功。

---

## 性能指标验证

### 无限流（注释掉限流配置）

```yaml
link:
  core:
    connection-rate-limit-enabled: false  # 关闭限流
```

**测试结果**：
- 100 个连接同时到达
- CPU 使用率瞬间 80%+
- 内存使用激增
- 可能出现部分连接超时

### 有限流（启用限流）

```yaml
link:
  core:
    connection-rate-limit-enabled: true
    connection-rate-limit-per-second: 10
```

**测试结果**：
- 每秒只接受 10 个连接
- CPU 使用率平稳在 20-30%
- 内存使用平稳增长
- 无超时，拒绝的连接立即返回

---

## 生产环境配置建议

根据服务器性能调整限流阈值：

### 低配服务器（2核4G）
```yaml
connection-rate-limit-per-second: 500
```

### 中配服务器（4核8G）
```yaml
connection-rate-limit-per-second: 1000
```

### 高配服务器（8核16G+）
```yaml
connection-rate-limit-per-second: 2000
```

### 计算公式

```
每秒连接数 = CPU核心数 × 250
```

例如：8核服务器 → 8 × 250 = 2000 连接/秒

---

## 监控指标

建议监控以下指标：

1. **连接成功率** - 应该在重启后逐步恢复到 100%
2. **连接拒绝数** - 重启后 1-2 分钟内会有拒绝，之后降为 0
3. **CPU 使用率** - 重启后不应超过 80%
4. **内存使用** - 应该平稳增长，不出现突刺

---

## 常见问题

### Q: 为什么配置了限流，但日志里没有拒绝记录？

A: 检查以下几点：
1. 配置是否生效：`connection-rate-limit-enabled: true`
2. 限流阈值是否太高：测试时改成 10 方便观察
3. 并发连接数是否足够：测试时至少 100 个并发

### Q: 客户端被拒绝后应该怎么办？

A: 客户端应该实现指数退避重连：
- 第1次：1秒后重试
- 第2次：2秒后重试
- 第3次：4秒后重试
- ...
- 最大：60秒后重试

### Q: 生产环境建议用阻塞模式还是拒绝模式？

A: **推荐拒绝模式（block: false）**
- 拒绝模式：客户端自己控制重试，分散压力
- 阻塞模式：连接在服务端排队，可能堆积

### Q: 如何动态调整限流速率？

A: 可以通过 JMX 或自定义接口：

```java
@RestController
public class RateLimitController {
    
    @Autowired
    private DefaultServer defaultServer;
    
    @PostMapping("/admin/rate-limit/update")
    public String updateRateLimit(@RequestParam double rate) {
        defaultServer.getConnectionRateLimitHandler().updateRate(rate);
        return "限流速率已更新为: " + rate;
    }
}
```

---

## 总结

连接限流是防止雷击效应的关键手段：

✅ **启用前**：10万连接同时重连 → 服务雪崩  
✅ **启用后**：每秒1000个连接 → 100秒平滑接入 → 服务稳定

配合客户端的指数退避重连，可以实现：
- 用户无感知的服务重启
- CPU/内存平稳增长
- 无连接超时
- 无数据丢失
