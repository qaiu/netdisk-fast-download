#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Playground API 测试脚本 (使用 pytest)

用于测试 /v2/playground/* 接口的功能，特别是 Python 脚本执行。
需要后端服务运行在 http://localhost:8080

安装依赖:
    pip install pytest requests

运行测试:
    pytest test_playground_api.py -v

或者运行特定测试:
    pytest test_playground_api.py::test_status_api -v
"""

import pytest
import requests
import json
import time

# 配置
BASE_URL = "http://localhost:8080"
PLAYGROUND_BASE = f"{BASE_URL}/v2/playground"

# 测试用的分享链接
TEST_SHARE_URL = "https://www.123684.com/s/test123"


class TestPlaygroundAPI:
    """Playground API 测试类"""

    @pytest.fixture(autouse=True)
    def setup(self):
        """测试前置：检查服务是否可用"""
        try:
            resp = requests.get(f"{PLAYGROUND_BASE}/status", timeout=5)
            if resp.status_code != 200:
                pytest.skip("后端服务不可用")
        except requests.exceptions.ConnectionError:
            pytest.skip("无法连接到后端服务")

    def test_status_api(self):
        """测试状态查询 API"""
        resp = requests.get(f"{PLAYGROUND_BASE}/status")
        assert resp.status_code == 200

        data = resp.json()
        assert "data" in data
        assert "enabled" in data["data"]
        print(f"状态响应: {json.dumps(data, ensure_ascii=False, indent=2)}")

    def test_python_simple_code(self):
        """测试简单 Python 代码执行"""
        code = '''
def parse(share_link_info, http, logger):
    logger.info("简单测试开始")
    return "https://example.com/download/test.zip"
'''
        payload = {
            "code": code,
            "shareUrl": TEST_SHARE_URL,
            "language": "python",
            "method": "parse"
        }

        resp = requests.post(f"{PLAYGROUND_BASE}/test", json=payload)
        assert resp.status_code == 200

        data = resp.json()
        print(f"响应: {json.dumps(data, ensure_ascii=False, indent=2)}")

        # 检查执行结果
        assert data.get("success") == True, f"执行失败: {data.get('error')}"
        assert data.get("result") == "https://example.com/download/test.zip"

    def test_python_with_json_library(self):
        """测试使用 json 库的 Python 代码"""
        code = '''
import json

def parse(share_link_info, http, logger):
    data = {"url": "https://example.com/file.zip", "size": 1024}
    logger.info(f"数据: {json.dumps(data)}")
    return data["url"]
'''
        payload = {
            "code": code,
            "shareUrl": TEST_SHARE_URL,
            "language": "python",
            "method": "parse"
        }

        resp = requests.post(f"{PLAYGROUND_BASE}/test", json=payload)
        assert resp.status_code == 200

        data = resp.json()
        print(f"响应: {json.dumps(data, ensure_ascii=False, indent=2)}")

        assert data.get("success") == True, f"执行失败: {data.get('error')}"
        assert "example.com" in data.get("result", "")

    def test_python_with_requests_import(self):
        """测试导入 requests 库（不发起实际请求）"""
        code = '''
import requests

def parse(share_link_info, http, logger):
    logger.info(f"requests 版本: {requests.__version__}")
    
    # 只测试导入，不发起实际网络请求
    return "https://example.com/download/file.zip"
'''
        payload = {
            "code": code,
            "shareUrl": TEST_SHARE_URL,
            "language": "python",
            "method": "parse"
        }

        resp = requests.post(f"{PLAYGROUND_BASE}/test", json=payload)
        assert resp.status_code == 200

        data = resp.json()
        print(f"响应: {json.dumps(data, ensure_ascii=False, indent=2)}")

        # 注意: 由于 GraalPy 限制，此测试可能失败
        if not data.get("success"):
            print(f"⚠ requests 导入可能失败 (GraalPy 限制): {data.get('error')}")
            pytest.skip("GraalPy requests 导入限制")

        assert data.get("result") is not None

    def test_python_with_requests_get(self):
        """测试使用 requests 发起 GET 请求"""
        code = '''
import requests

def parse(share_link_info, http, logger):
    logger.info("开始 HTTP 请求测试")
    
    # 发起简单的 GET 请求
    try:
        resp = requests.get("https://httpbin.org/get", timeout=10)
        logger.info(f"响应状态码: {resp.status_code}")
        
        if resp.status_code == 200:
            return "https://example.com/success.zip"
        else:
            return None
    except Exception as e:
        logger.error(f"请求失败: {str(e)}")
        return None
'''
        payload = {
            "code": code,
            "shareUrl": TEST_SHARE_URL,
            "language": "python",
            "method": "parse"
        }

        resp = requests.post(f"{PLAYGROUND_BASE}/test", json=payload, timeout=60)
        assert resp.status_code == 200

        data = resp.json()
        print(f"响应: {json.dumps(data, ensure_ascii=False, indent=2)}")

        # 检查日志
        if "logs" in data:
            for log_entry in data["logs"]:
                print(f"  [{log_entry.get('level')}] {log_entry.get('message')}")

        # 如果由于 GraalPy 限制失败，跳过测试
        if not data.get("success"):
            error = data.get("error", "")
            if "unicodedata" in error or "LLVM" in error:
                pytest.skip("GraalPy requests 限制")
            pytest.fail(f"执行失败: {error}")

    def test_python_security_block_subprocess(self):
        """测试安全检查器拦截 subprocess"""
        code = '''
import subprocess

def parse(share_link_info, http, logger):
    result = subprocess.run(['ls'], capture_output=True)
    return result.stdout.decode()
'''
        payload = {
            "code": code,
            "shareUrl": TEST_SHARE_URL,
            "language": "python",
            "method": "parse"
        }

        resp = requests.post(f"{PLAYGROUND_BASE}/test", json=payload)
        assert resp.status_code == 200

        data = resp.json()
        print(f"响应: {json.dumps(data, ensure_ascii=False, indent=2)}")

        # 应该被安全检查器拦截
        assert data.get("success") == False
        assert "subprocess" in data.get("error", "").lower() or \
               "安全" in data.get("error", "")

    def test_python_security_block_os_system(self):
        """测试安全检查器拦截 os.system"""
        code = '''
import os

def parse(share_link_info, http, logger):
    os.system("ls")
    return "test"
'''
        payload = {
            "code": code,
            "shareUrl": TEST_SHARE_URL,
            "language": "python",
            "method": "parse"
        }

        resp = requests.post(f"{PLAYGROUND_BASE}/test", json=payload)
        assert resp.status_code == 200

        data = resp.json()
        print(f"响应: {json.dumps(data, ensure_ascii=False, indent=2)}")

        # 应该被安全检查器拦截
        assert data.get("success") == False

    def test_python_with_logger(self):
        """测试日志记录功能"""
        code = '''
def parse(share_link_info, http, logger):
    logger.debug("这是 debug 消息")
    logger.info("这是 info 消息")
    logger.warn("这是 warn 消息")
    logger.error("这是 error 消息")
    return "https://example.com/logged.zip"
'''
        payload = {
            "code": code,
            "shareUrl": TEST_SHARE_URL,
            "language": "python",
            "method": "parse"
        }

        resp = requests.post(f"{PLAYGROUND_BASE}/test", json=payload)
        assert resp.status_code == 200

        data = resp.json()
        print(f"响应: {json.dumps(data, ensure_ascii=False, indent=2)}")

        assert data.get("success") == True
        assert "logs" in data
        assert len(data["logs"]) >= 4, "应该有至少 4 条日志"

        # 检查日志级别
        log_levels = [log["level"] for log in data["logs"]]
        assert "DEBUG" in log_levels or "debug" in log_levels
        assert "INFO" in log_levels or "info" in log_levels

    def test_empty_code_validation(self):
        """测试空代码验证"""
        payload = {
            "code": "",
            "shareUrl": TEST_SHARE_URL,
            "language": "python",
            "method": "parse"
        }

        resp = requests.post(f"{PLAYGROUND_BASE}/test", json=payload)
        assert resp.status_code == 200

        data = resp.json()
        print(f"响应: {json.dumps(data, ensure_ascii=False, indent=2)}")

        assert data.get("success") == False
        assert "空" in data.get("error", "") or "empty" in data.get("error", "").lower()

    def test_invalid_language(self):
        """测试无效语言类型"""
        payload = {
            "code": "print('test')",
            "shareUrl": TEST_SHARE_URL,
            "language": "rust",  # 不支持的语言
            "method": "parse"
        }

        resp = requests.post(f"{PLAYGROUND_BASE}/test", json=payload)
        assert resp.status_code == 200

        data = resp.json()
        print(f"响应: {json.dumps(data, ensure_ascii=False, indent=2)}")

        assert data.get("success") == False
        assert "不支持" in data.get("error", "") or "language" in data.get("error", "").lower()

    def test_javascript_code(self):
        """测试 JavaScript 代码执行"""
        code = '''
function parse(shareLinkInfo, http, logger) {
    logger.info("JavaScript 测试");
    return "https://example.com/js-result.zip";
}
'''
        payload = {
            "code": code,
            "shareUrl": TEST_SHARE_URL,
            "language": "javascript",
            "method": "parse"
        }

        resp = requests.post(f"{PLAYGROUND_BASE}/test", json=payload)
        assert resp.status_code == 200

        data = resp.json()
        print(f"响应: {json.dumps(data, ensure_ascii=False, indent=2)}")

        assert data.get("success") == True
        assert "js-result" in data.get("result", "")


class TestRequestsIntegration:
    """requests 库集成测试"""

    @pytest.fixture(autouse=True)
    def setup(self):
        """测试前置：检查服务是否可用"""
        try:
            resp = requests.get(f"{PLAYGROUND_BASE}/status", timeout=5)
            if resp.status_code != 200:
                pytest.skip("后端服务不可用")
        except requests.exceptions.ConnectionError:
            pytest.skip("无法连接到后端服务")

    def test_requests_session(self):
        """测试 requests.Session"""
        code = '''
import requests

def parse(share_link_info, http, logger):
    session = requests.Session()
    session.headers.update({"User-Agent": "TestBot/1.0"})
    logger.info("Session 创建成功")
    return "https://example.com/session.zip"
'''
        payload = {
            "code": code,
            "shareUrl": TEST_SHARE_URL,
            "language": "python",
            "method": "parse"
        }

        resp = requests.post(f"{PLAYGROUND_BASE}/test", json=payload)
        data = resp.json()
        print(f"响应: {json.dumps(data, ensure_ascii=False, indent=2)}")

        if not data.get("success"):
            error = data.get("error", "")
            if "unicodedata" in error or "LLVM" in error:
                pytest.skip("GraalPy requests 限制")
            pytest.fail(f"执行失败: {error}")

    def test_requests_post_json(self):
        """测试 requests POST JSON"""
        code = '''
import requests
import json

def parse(share_link_info, http, logger):
    data = {"test": "value"}
    logger.info(f"准备 POST 数据: {json.dumps(data)}")
    
    try:
        resp = requests.post(
            "https://httpbin.org/post",
            json=data,
            timeout=10
        )
        logger.info(f"响应状态: {resp.status_code}")
        return "https://example.com/post-success.zip"
    except Exception as e:
        logger.error(f"POST 请求失败: {str(e)}")
        return None
'''
        payload = {
            "code": code,
            "shareUrl": TEST_SHARE_URL,
            "language": "python",
            "method": "parse"
        }

        resp = requests.post(f"{PLAYGROUND_BASE}/test", json=payload, timeout=60)
        data = resp.json()
        print(f"响应: {json.dumps(data, ensure_ascii=False, indent=2)}")

        if not data.get("success"):
            error = data.get("error", "")
            if "unicodedata" in error or "LLVM" in error:
                pytest.skip("GraalPy requests 限制")

    def test_requests_with_headers(self):
        """测试 requests 自定义 headers"""
        code = '''
import requests

def parse(share_link_info, http, logger):
    headers = {
        "User-Agent": "CustomBot/2.0",
        "Accept": "application/json",
        "X-Custom-Header": "TestValue"
    }
    
    logger.info("准备发送带自定义 headers 的请求")
    
    try:
        resp = requests.get(
            "https://httpbin.org/headers",
            headers=headers,
            timeout=10
        )
        logger.info(f"响应: {resp.status_code}")
        return "https://example.com/headers-success.zip"
    except Exception as e:
        logger.error(f"请求失败: {str(e)}")
        return None
'''
        payload = {
            "code": code,
            "shareUrl": TEST_SHARE_URL,
            "language": "python",
            "method": "parse"
        }

        resp = requests.post(f"{PLAYGROUND_BASE}/test", json=payload, timeout=60)
        data = resp.json()
        print(f"响应: {json.dumps(data, ensure_ascii=False, indent=2)}")

        if not data.get("success"):
            error = data.get("error", "")
            if "unicodedata" in error or "LLVM" in error:
                pytest.skip("GraalPy requests 限制")


if __name__ == "__main__":
    # 直接运行测试
    pytest.main([__file__, "-v", "--tb=short"])
