# 客户端断线重连规范

## 概述

本文档定义了 Link-IM 客户端的断线重连机制，确保在网络波动、服务重启等场景下提供良好的用户体验。

## 一、服务端支持

### 1.1 服务维护通知

服务端在重启前会发送 `SERVER_MAINTENANCE` 事件（action = 1024），消息体为：

```json
{
  "type": "upgrade",           // 类型：upgrade=升级维护, shutdown=即将关闭
  "message": "服务升级中，请稍后...",  // 提示消息
  "estimatedDowntime": 60,     // 预计停机时长（秒）
  "reconnectDelay": 5          // 建议重连延迟（秒）
}
```

**客户端收到此消息后应当：**
1. 向用户显示友好提示（如"服务升级中，稍后自动恢复"）
2. 标记进入"维护模式"，不再发送新消息
3. 按照 `reconnectDelay` 延迟后开始重连

---

## 二、客户端重连策略

### 2.1 触发重连的场景

以下情况应触发自动重连：

1. **WebSocket `onerror` 事件** - 连接出错
2. **WebSocket `onclose` 事件** - 连接关闭（排除用户主动退出登录）
3. **心跳超时** - 超过 30 秒未收到服务端心跳回复
4. **收到 `SERVER_MAINTENANCE` 消息** - 服务端维护通知

### 2.2 指数退避算法

重连间隔采用指数退避策略，避免对服务器造成雷击效应：

```javascript
// 重连延迟计算
const delays = [1, 2, 4, 8, 16, 32, 60]; // 秒
let retryCount = 0;

function getRetryDelay() {
  if (retryCount >= delays.length) {
    return 60; // 最大延迟 60 秒
  }
  return delays[retryCount++] * 1000; // 转换为毫秒
}

// 收到 SERVER_MAINTENANCE 消息时
function onServerMaintenance(notice) {
  retryCount = 0; // 重置重试计数
  const baseDelay = notice.reconnectDelay || 5;
  
  // 🔑 关键：加入随机抖动（Jitter），分散重连时间
  // 防止10万连接在同一秒重连导致雷击效应
  // 例如：5秒 → 3-7秒随机分布
  const jitter = (Math.random() * 0.8 - 0.4) * baseDelay; // ±40%
  const finalDelay = Math.max(1, baseDelay + jitter);
  
  console.log(`${finalDelay.toFixed(1)}秒后重连（基准${baseDelay}s + 抖动${jitter.toFixed(1)}s）`);
  setTimeout(() => reconnect(), finalDelay * 1000);
}

// 普通断线重连
function onConnectionLost() {
  const delay = getRetryDelay();
  console.log(`连接断开，${delay/1000}秒后重连...`);
  setTimeout(() => reconnect(), delay);
}
```

### 2.3 重连流程

```
1. 断线检测
   ↓
2. 显示"连接中..."提示
   ↓
3. 等待退避延迟
   ↓
4. 创建新 WebSocket 连接
   ↓
5. 连接成功后发送 LOGIN 事件（携带 token）
   ↓
6. 收到 LOGIN 响应，恢复会话
   ↓
7. 调用消息补偿接口拉取离线消息
   ↓
8. 更新 UI 为"已连接"
```

---

## 三、代码示例

### 3.1 JavaScript / TypeScript 示例

```typescript
class ImWebSocket {
  private ws: WebSocket | null = null;
  private retryCount: number = 0;
  private reconnectTimer: number | null = null;
  private isManualClose: boolean = false;
  private inMaintenanceMode: boolean = false;

  // 重连延迟（秒）
  private readonly RETRY_DELAYS = [1, 2, 4, 8, 16, 32, 60];

  constructor(private url: string, private token: string) {}

  connect() {
    this.ws = new WebSocket(this.url);

    this.ws.onopen = () => {
      console.log('WebSocket 连接成功');
      this.retryCount = 0;
      this.inMaintenanceMode = false;
      this.sendLoginEvent();
    };

    this.ws.onmessage = (event) => {
      this.handleMessage(event.data);
    };

    this.ws.onerror = (error) => {
      console.error('WebSocket 错误', error);
    };

    this.ws.onclose = () => {
      console.log('WebSocket 连接关闭');
      if (!this.isManualClose) {
        this.scheduleReconnect();
      }
    };
  }

  private handleMessage(data: string) {
    const message = JSON.parse(data);

    // 处理服务维护通知
    if (message.action === 1024) {
      this.handleServerMaintenance(message.body);
      return;
    }

    // 处理其他消息...
  }

  private handleServerMaintenance(notice: any) {
    console.log('收到服务维护通知:', notice.message);
    this.inMaintenanceMode = true;

    // 显示用户提示
    this.showToast(notice.message);

    // 重置重试计数，按照服务端建议延迟重连
    this.retryCount = 0;
    const delay = (notice.reconnectDelay || 5) * 1000;

    this.reconnectTimer = window.setTimeout(() => {
      this.reconnect();
    }, delay);
  }

  private scheduleReconnect() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
    }

    const delayIndex = Math.min(this.retryCount, this.RETRY_DELAYS.length - 1);
    const delay = this.RETRY_DELAYS[delayIndex] * 1000;

    console.log(`${delay / 1000}秒后尝试重连（第${this.retryCount + 1}次）...`);
    this.retryCount++;

    this.reconnectTimer = window.setTimeout(() => {
      this.reconnect();
    }, delay);
  }

  private reconnect() {
    console.log('开始重连...');
    this.close(false); // 关闭旧连接
    this.connect();    // 创建新连接
  }

  private sendLoginEvent() {
    const loginData = {
      action: 1001, // LOGIN
      body: {
        token: this.token,
        platform: 1, // 1=Web
        deviceId: this.getDeviceId()
      }
    };
    this.send(JSON.stringify(loginData));

    // 登录成功后拉取离线消息
    this.pullOfflineMessages();
  }

  private async pullOfflineMessages() {
    try {
      const response = await fetch('/api/message/pull-message', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${this.token}`
        },
        body: JSON.stringify({
          lastMessageId: this.getLastMessageId(),
          limit: 100
        })
      });

      const result = await response.json();
      if (result.code === 200) {
        console.log(`拉取到 ${result.data.length} 条离线消息`);
        // 处理离线消息...
      }
    } catch (error) {
      console.error('拉取离线消息失败', error);
    }
  }

  send(data: string) {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(data);
    } else {
      console.warn('WebSocket 未连接，消息发送失败');
    }
  }

  close(isManual: boolean = true) {
    this.isManualClose = isManual;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
    }
    this.ws?.close();
  }

  private getDeviceId(): string {
    // 生成或获取设备ID
    let deviceId = localStorage.getItem('deviceId');
    if (!deviceId) {
      deviceId = 'web_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
      localStorage.setItem('deviceId', deviceId);
    }
    return deviceId;
  }

  private getLastMessageId(): string {
    return localStorage.getItem('lastMessageId') || '';
  }

  private showToast(message: string) {
    // 实现你的 Toast 提示
    console.log('[Toast]', message);
  }
}

// 使用示例
const imClient = new ImWebSocket('ws://localhost:9000/ws', 'your-jwt-token');
imClient.connect();
```

### 3.2 Android (Kotlin) 示例

```kotlin
class ImWebSocketClient(
    private val url: String,
    private val token: String
) {
    private var webSocket: WebSocket? = null
    private var retryCount = 0
    private var isManualClose = false
    private val retryDelays = listOf(1, 2, 4, 8, 16, 32, 60) // 秒
    private val handler = Handler(Looper.getMainLooper())
    
    fun connect() {
        val client = OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val request = Request.Builder()
            .url(url)
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("ImWebSocket", "连接成功")
                retryCount = 0
                sendLoginEvent()
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("ImWebSocket", "连接失败", t)
                if (!isManualClose) {
                    scheduleReconnect()
                }
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("ImWebSocket", "连接关闭")
                if (!isManualClose) {
                    scheduleReconnect()
                }
            }
        })
    }
    
    private fun handleMessage(text: String) {
        val json = JSONObject(text)
        val action = json.getInt("action")
        
        // 服务维护通知
        if (action == 1024) {
            val body = json.getJSONObject("body")
            val message = body.getString("message")
            val reconnectDelay = body.optInt("reconnectDelay", 5)
            
            Log.d("ImWebSocket", "收到服务维护通知: $message")
            showToast(message)
            
            retryCount = 0
            handler.postDelayed({ reconnect() }, reconnectDelay * 1000L)
            return
        }
        
        // 处理其他消息...
    }
    
    private fun scheduleReconnect() {
        val delayIndex = minOf(retryCount, retryDelays.size - 1)
        val delay = retryDelays[delayIndex] * 1000L
        
        Log.d("ImWebSocket", "${delay / 1000}秒后重连（第${retryCount + 1}次）")
        retryCount++
        
        handler.postDelayed({ reconnect() }, delay)
    }
    
    private fun reconnect() {
        Log.d("ImWebSocket", "开始重连...")
        close(false)
        connect()
    }
    
    private fun sendLoginEvent() {
        val loginData = JSONObject().apply {
            put("action", 1001)
            put("body", JSONObject().apply {
                put("token", token)
                put("platform", 2) // 2=Android
                put("deviceId", getDeviceId())
            })
        }
        webSocket?.send(loginData.toString())
        
        // 拉取离线消息
        pullOfflineMessages()
    }
    
    private fun pullOfflineMessages() {
        // 使用 Retrofit 或其他 HTTP 客户端调用接口
        // POST /api/message/pull-message
    }
    
    fun close(isManual: Boolean = true) {
        isManualClose = isManual
        handler.removeCallbacksAndMessages(null)
        webSocket?.close(1000, "Normal close")
    }
    
    private fun getDeviceId(): String {
        // 获取设备ID（可使用 Android ID 或自定义生成）
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
    }
    
    private fun showToast(message: String) {
        handler.post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
```

---

## 四、消息补偿机制

### 4.1 离线消息拉取接口

**接口**: `POST /api/message/pull-message`

**请求参数**:
```json
{
  "lastMessageId": "66d0f12345abcdef",  // 客户端最后一条消息ID
  "limit": 100                           // 最多拉取条数
}
```

**响应示例**:
```json
{
  "code": 200,
  "data": [
    {
      "messageId": "66d0f12345abcdef",
      "chatId": "group_123",
      "senderId": "user_456",
      "content": "Hello",
      "timestamp": 1724900000000
    }
  ]
}
```

### 4.2 拉取时机

1. **重连成功后** - 立即拉取离线消息
2. **定期同步** - 每隔 5 分钟检查一次（可选）
3. **进入聊天页面** - 确保消息最新

---

## 五、用户体验指导

### 5.1 连接状态提示

| 状态 | 提示文案 | UI 表现 |
|------|---------|---------|
| 连接中 | "连接中..." | 显示加载动画 |
| 已连接 | 无提示或"已连接" | 绿色指示器 |
| 断开连接 | "连接已断开，正在重连..." | 黄色警告条 |
| 服务维护 | "服务升级中，约1分钟后恢复" | 蓝色提示条 |
| 重连失败 | "网络异常，请检查网络连接" | 红色错误条 |

### 5.2 消息发送失败处理

1. 断线期间的消息标记为"发送中"
2. 重连成功后自动重试发送
3. 重试失败后显示"发送失败"，允许用户手动重试

---

## 六、测试验证

### 6.1 测试场景

1. **正常重启** - 服务端执行 `kill -15` 优雅停服，客户端自动重连成功
2. **异常断线** - 网络断开，客户端按指数退避重连
3. **弱网环境** - 模拟网络延迟，验证超时重连
4. **并发重连** - 5000 用户同时断线，避免雷击效应
5. **消息补偿** - 断线期间的消息在重连后全部拉取

### 6.2 验收标准

- ✅ 服务重启时，客户端能在 5 秒内自动重连成功
- ✅ 重连后消息无丢失（通过离线消息补偿）
- ✅ 用户看到明确的"服务升级中"提示，而非"连接失败"
- ✅ 多次重连不会导致连接风暴（指数退避生效）

---

## 七、常见问题

**Q: 为什么不使用固定间隔重连？**  
A: 固定间隔会导致大量客户端同时重连，造成服务器雷击效应。指数退避能分散重连压力。

**Q: 最大重连次数是多少？**  
A: 无限次，但间隔最大为 60 秒。用户主动退出登录时停止重连。

**Q: 如何判断是服务器维护还是网络问题？**  
A: 收到 `SERVER_MAINTENANCE` 消息时是服务器维护；其他断线场景视为网络问题。

**Q: 离线消息拉取的最大时间范围？**  
A: 建议保留 7 天内的离线消息，超过 7 天的消息客户端不再拉取。

---

## 八、版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0 | 2026-08-29 | 初始版本，定义基础重连策略 |

---

**文档维护人**: Link-IM 开发团队  
**最后更新**: 2026-08-29
