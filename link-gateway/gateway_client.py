#!/usr/bin/env python3
"""
AI 服务端访问网关示例代码
演示如何使用 AI 令牌直接访问后端服务，绕过 JWT 验证
"""

import requests
from typing import Dict, Any, Optional

# AI 令牌（从环境变量或配置文件读取更安全）
AI_TOKEN = "0ARFsoLQyn9QlwkMwd20lcm71O5cOf2P+GvVexdYETQRNKaq+zZCsj2GQGO6cqYC"

# 网关地址
GATEWAY_URL = "http://localhost:8080"


class GatewayClient:
    """网关客户端，封装 AI 令牌认证逻辑"""

    def __init__(self, gateway_url: str, ai_token: str):
        """
        初始化网关客户端

        Args:
            gateway_url: 网关地址，如 http://localhost:8080
            ai_token: AI 服务端专用令牌
        """
        self.gateway_url = gateway_url.rstrip('/')
        self.ai_token = ai_token
        self.session = requests.Session()
        # 设置默认请求头
        self.session.headers.update({
            'X-AI-Secret-Token': self.ai_token,
            'Content-Type': 'application/json'
        })

    def get(self, path: str, params: Optional[Dict] = None) -> requests.Response:
        """
        发起 GET 请求

        Args:
            path: API 路径，如 /api/user/info
            params: 查询参数

        Returns:
            requests.Response 对象
        """
        url = f"{self.gateway_url}{path}"
        return self.session.get(url, params=params)

    def post(self, path: str, json_data: Optional[Dict] = None) -> requests.Response:
        """
        发起 POST 请求

        Args:
            path: API 路径，如 /api/message/send
            json_data: JSON 请求体

        Returns:
            requests.Response 对象
        """
        url = f"{self.gateway_url}{path}"
        return self.session.post(url, json=json_data)

    def put(self, path: str, json_data: Optional[Dict] = None) -> requests.Response:
        """
        发起 PUT 请求

        Args:
            path: API 路径
            json_data: JSON 请求体

        Returns:
            requests.Response 对象
        """
        url = f"{self.gateway_url}{path}"
        return self.session.put(url, json=json_data)

    def delete(self, path: str) -> requests.Response:
        """
        发起 DELETE 请求

        Args:
            path: API 路径

        Returns:
            requests.Response 对象
        """
        url = f"{self.gateway_url}{path}"
        return self.session.delete(url)


def main():
    """示例：使用网关客户端访问后端服务"""

    # 初始化客户端
    client = GatewayClient(GATEWAY_URL, AI_TOKEN)

    print("=" * 50)
    print("AI 服务端网关客户端示例")
    print("=" * 50)
    print()

    # 示例 1：获取用户信息
    print("示例 1: GET 请求 - 获取用户信息")
    print("-" * 50)
    try:
        response = client.get("/api/user/info")
        print(f"状态码: {response.status_code}")
        print(f"响应: {response.text}")
        if response.status_code == 200:
            data = response.json()
            print(f"数据: {data}")
    except Exception as e:
        print(f"请求失败: {e}")
    print()

    # 示例 2：发送消息
    print("示例 2: POST 请求 - 发送消息")
    print("-" * 50)
    try:
        payload = {
            "toUserId": "user123",
            "content": "Hello from AI service!",
            "messageType": 1
        }
        response = client.post("/api/message/send", json_data=payload)
        print(f"状态码: {response.status_code}")
        print(f"响应: {response.text}")
    except Exception as e:
        print(f"请求失败: {e}")
    print()

    # 示例 3：查询数据（带查询参数）
    print("示例 3: GET 请求 - 查询数据（带参数）")
    print("-" * 50)
    try:
        params = {
            "page": 1,
            "size": 10,
            "keyword": "test"
        }
        response = client.get("/api/search", params=params)
        print(f"状态码: {response.status_code}")
        print(f"响应: {response.text}")
    except Exception as e:
        print(f"请求失败: {e}")
    print()

    print("=" * 50)
    print("示例完成")
    print("=" * 50)


if __name__ == "__main__":
    main()
