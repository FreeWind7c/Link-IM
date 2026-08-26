#!/bin/bash

# AI 令牌测试脚本
# 用于验证网关 AI 服务端专用通道是否正常工作

# 网关地址（根据实际环境修改）
GATEWAY_URL="http://localhost:8080"

# AI 令牌（明文）
AI_TOKEN="0ARFsoLQyn9QlwkMwd20lcm71O5cOf2P+GvVexdYETQRNKaq+zZCsj2GQGO6cqYC"

# 测试端点（根据实际业务修改）
TEST_ENDPOINT="/api/user/info"

echo "=========================================="
echo "AI 令牌网关测试"
echo "=========================================="
echo ""

# 测试 1：使用正确的 AI 令牌
echo "测试 1: 使用正确的 AI 令牌访问受保护的端点"
echo "---"
curl -X GET "${GATEWAY_URL}${TEST_ENDPOINT}" \
  -H "X-AI-Secret-Token: ${AI_TOKEN}" \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

# 测试 2：使用错误的 AI 令牌
echo "测试 2: 使用错误的 AI 令牌（应该返回 401）"
echo "---"
curl -X GET "${GATEWAY_URL}${TEST_ENDPOINT}" \
  -H "X-AI-Secret-Token: wrong-token-12345" \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

# 测试 3：不带任何令牌
echo "测试 3: 不带任何令牌（应该返回 401）"
echo "---"
curl -X GET "${GATEWAY_URL}${TEST_ENDPOINT}" \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

echo "=========================================="
echo "测试完成"
echo "=========================================="
