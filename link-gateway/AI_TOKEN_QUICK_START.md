# AI 令牌快速使用指南

## 给 AI 端的令牌

```
0ARFsoLQyn9QlwkMwd20lcm71O5cOf2P+GvVexdYETQRNKaq+zZCsj2GQGO6cqYC
```

**⚠️ 妥善保管此令牌，不要泄露或提交到代码仓库**

## 使用方法

在发送给网关的请求中，添加以下请求头：

```
X-AI-Secret-Token: 0ARFsoLQyn9QlwkMwd20lcm71O5cOf2P+GvVexdYETQRNKaq+zZCsj2GQGO6cqYC
```

### cURL 示例

```bash
curl -X POST http://your-gateway.com/api/some-endpoint \
  -H "X-AI-Secret-Token: 0ARFsoLQyn9QlwkMwd20lcm71O5cOf2P+GvVexdYETQRNKaq+zZCsj2GQGO6cqYC" \
  -H "Content-Type: application/json" \
  -d '{"key": "value"}'
```

### Python 示例

```python
import requests

AI_TOKEN = "0ARFsoLQyn9QlwkMwd20lcm71O5cOf2P+GvVexdYETQRNKaq+zZCsj2GQGO6cqYC"

response = requests.post(
    "http://your-gateway.com/api/some-endpoint",
    headers={
        "X-AI-Secret-Token": AI_TOKEN,
        "Content-Type": "application/json"
    },
    json={"key": "value"}
)
```

### JavaScript/TypeScript 示例

```javascript
const AI_TOKEN = "0ARFsoLQyn9QlwkMwd20lcm71O5cOf2P+GvVexdYETQRNKaq+zZCsj2GQGO6cqYC";

const response = await fetch("http://your-gateway.com/api/some-endpoint", {
  method: "POST",
  headers: {
    "X-AI-Secret-Token": AI_TOKEN,
    "Content-Type": "application/json"
  },
  body: JSON.stringify({ key: "value" })
});
```

## 工作原理

1. AI 端在请求头中携带明文令牌
2. 网关计算令牌的 SHA-256 哈希值
3. 与预存的哈希值比对
4. 匹配成功则直接放行，无需 JWT 验证

## 安全说明

- ✅ **防反编译**：Java 代码中只存储哈希值，无法还原原始令牌
- ✅ **强随机性**：64 字符强随机令牌，暴力破解不可行
- ⚠️ **必须 HTTPS**：生产环境务必使用 HTTPS，防止令牌被中间人截获
- ⚠️ **妥善保管**：建议使用环境变量或密钥管理服务存储令牌

## 完整文档

详细说明请参考：[AI_TOKEN_README.md](./AI_TOKEN_README.md)

## 测试工具

- **Bash 测试脚本**：`test-ai-token.sh`
- **Python 客户端**：`gateway_client.py`（封装了令牌认证逻辑，可直接集成）
