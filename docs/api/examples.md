# 示例代码

本页面提供了各种编程语言调用 Netdisk Fast Download API 的完整示例。

## JavaScript/TypeScript 示例

### 基础 API 封装

```javascript
class NetdiskAPI {
  constructor(baseURL = 'http://localhost:6400') {
    this.baseURL = baseURL;
  }
  
  /**
   * 解析分享链接
   * @param {string} url - 分享链接
   * @param {string} password - 分享密码（可选）
   * @returns {Promise<Object>} 解析结果
   */
  async parseLink(url, password = '') {
    const params = new URLSearchParams({ url });
    if (password) params.append('pwd', password);
    
    const response = await fetch(`${this.baseURL}/json/parser?${params}`);
    const data = await response.json();
    
    if (!data.success) {
      throw new Error(data.msg);
    }
    
    return data.data;
  }
  
  /**
   * 获取文件夹列表
   * @param {string} url - 文件夹分享链接
   * @param {string} password - 分享密码（可选）
   * @returns {Promise<Array>} 文件列表
   */
  async getFileList(url, password = '') {
    const params = new URLSearchParams({ url });
    if (password) params.append('pwd', password);
    
    const response = await fetch(`${this.baseURL}/v2/getFileList?${params}`);
    const data = await response.json();
    
    if (!data.success) {
      throw new Error(data.msg);
    }
    
    return data.data;
  }
  
  /**
   * 获取统计信息
   * @returns {Promise<Object>} 统计数据
   */
  async getStatistics() {
    const response = await fetch(`${this.baseURL}/v2/statisticsInfo`);
    const data = await response.json();
    
    if (!data.success) {
      throw new Error(data.msg);
    }
    
    return data.data;
  }
  
  /**
   * 获取链接详情
   * @param {string} url - 分享链接
   * @returns {Promise<Object>} 链接详情
   */
  async getLinkInfo(url) {
    const params = new URLSearchParams({ url });
    const response = await fetch(`${this.baseURL}/v2/linkInfo?${params}`);
    const data = await response.json();
    
    if (!data.success) {
      throw new Error(data.msg);
    }
    
    return data.data;
  }
}

// 使用示例
const api = new NetdiskAPI('https://your-domain.com');

// 解析单个文件
api.parseLink('https://lanzoux.com/ia2cntg')
  .then(result => {
    console.log('下载链接:', result.directLink);
    console.log('缓存命中:', result.cacheHit);
  })
  .catch(error => console.error('解析失败:', error));

// 解析文件夹
api.getFileList('https://lanzoux.com/b12345678', '1234')
  .then(files => {
    files.forEach(file => {
      console.log(`${file.fileName} (${file.sizeStr})`);
    });
  })
  .catch(error => console.error('解析失败:', error));
```

### React 组件示例

```jsx
import React, { useState, useEffect } from 'react';

const NetdiskParser = () => {
  const [url, setUrl] = useState('');
  const [password, setPassword] = useState('');
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const api = new NetdiskAPI();

  const handleParse = async () => {
    if (!url.trim()) {
      setError('请输入分享链接');
      return;
    }

    setLoading(true);
    setError('');
    setResult(null);

    try {
      const data = await api.parseLink(url, password);
      setResult(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="netdisk-parser">
      <div className="form-group">
        <label>分享链接:</label>
        <input
          type="text"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          placeholder="输入网盘分享链接"
        />
      </div>
      
      <div className="form-group">
        <label>分享密码:</label>
        <input
          type="text"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="如有密码请输入"
        />
      </div>
      
      <button onClick={handleParse} disabled={loading}>
        {loading ? '解析中...' : '开始解析'}
      </button>
      
      {error && <div className="error">{error}</div>}
      
      {result && (
        <div className="result">
          <h3>解析成功!</h3>
          <p>直链地址: <a href={result.directLink} target="_blank">点击下载</a></p>
          <p>缓存状态: {result.cacheHit ? '缓存命中' : '实时解析'}</p>
          {result.expires && <p>过期时间: {result.expires}</p>}
        </div>
      )}
    </div>
  );
};

export default NetdiskParser;
```

## Python 示例

### 基础 API 类

```python
import requests
import json
from typing import Optional, Dict, List
from urllib.parse import urlencode

class NetdiskAPI:
    def __init__(self, base_url: str = 'http://localhost:6400'):
        self.base_url = base_url
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'NetdiskAPI/1.0'
        })
    
    def parse_link(self, url: str, password: str = '') -> Dict:
        """解析分享链接"""
        params = {'url': url}
        if password:
            params['pwd'] = password
        
        response = self.session.get(
            f'{self.base_url}/json/parser',
            params=params,
            timeout=30
        )
        
        data = response.json()
        if not data['success']:
            raise Exception(data['msg'])
        
        return data['data']
    
    def get_file_list(self, url: str, password: str = '') -> List[Dict]:
        """获取文件夹列表"""
        params = {'url': url}
        if password:
            params['pwd'] = password
        
        response = self.session.get(
            f'{self.base_url}/v2/getFileList',
            params=params,
            timeout=30
        )
        
        data = response.json()
        if not data['success']:
            raise Exception(data['msg'])
        
        return data['data']
    
    def get_statistics(self) -> Dict:
        """获取统计信息"""
        response = self.session.get(
            f'{self.base_url}/v2/statisticsInfo',
            timeout=10
        )
        
        data = response.json()
        if not data['success']:
            raise Exception(data['msg'])
        
        return data['data']
    
    def get_link_info(self, url: str) -> Dict:
        """获取链接详情"""
        params = {'url': url}
        response = self.session.get(
            f'{self.base_url}/v2/linkInfo',
            params=params,
            timeout=30
        )
        
        data = response.json()
        if not data['success']:
            raise Exception(data['msg'])
        
        return data['data']

# 使用示例
def main():
    api = NetdiskAPI('https://your-domain.com')
    
    try:
        # 解析单个文件
        result = api.parse_link('https://lanzoux.com/ia2cntg')
        print(f"下载链接: {result['directLink']}")
        print(f"缓存命中: {result['cacheHit']}")
        
        # 解析文件夹
        files = api.get_file_list('https://lanzoux.com/b12345678', '1234')
        for file in files:
            print(f"{file['fileName']} ({file['sizeStr']})")
        
        # 获取统计信息
        stats = api.get_statistics()
        print(f"总请求次数: {stats['total']:,}")
        print(f"缓存命中率: {stats['cacheTotal']/stats['total']*100:.2f}%")
        
    except Exception as e:
        print(f"错误: {e}")

if __name__ == '__main__':
    main()
```

### 异步版本 (asyncio)

```python
import asyncio
import aiohttp
from typing import Optional, Dict, List

class AsyncNetdiskAPI:
    def __init__(self, base_url: str = 'http://localhost:6400'):
        self.base_url = base_url
    
    async def parse_link(self, url: str, password: str = '') -> Dict:
        """异步解析分享链接"""
        params = {'url': url}
        if password:
            params['pwd'] = password
        
        async with aiohttp.ClientSession() as session:
            async with session.get(
                f'{self.base_url}/json/parser',
                params=params,
                timeout=aiohttp.ClientTimeout(total=30)
            ) as response:
                data = await response.json()
                
                if not data['success']:
                    raise Exception(data['msg'])
                
                return data['data']
    
    async def batch_parse(self, urls: List[str], passwords: List[str] = None) -> List[Dict]:
        """批量解析链接"""
        if passwords is None:
            passwords = [''] * len(urls)
        
        tasks = []
        for url, pwd in zip(urls, passwords):
            task = self.parse_link(url, pwd)
            tasks.append(task)
        
        results = await asyncio.gather(*tasks, return_exceptions=True)
        
        # 处理异常结果
        processed_results = []
        for i, result in enumerate(results):
            if isinstance(result, Exception):
                processed_results.append({
                    'url': urls[i],
                    'error': str(result)
                })
            else:
                processed_results.append({
                    'url': urls[i],
                    'result': result
                })
        
        return processed_results

# 异步使用示例
async def async_main():
    api = AsyncNetdiskAPI('https://your-domain.com')
    
    # 批量解析
    urls = [
        'https://lanzoux.com/ia2cntg',
        'https://cowtransfer.com/s/9a644fe3e3a748',
        'https://v2.fangcloud.com/sharing/e5079007dc31226096628870c7'
    ]
    
    results = await api.batch_parse(urls)
    
    for item in results:
        if 'error' in item:
            print(f"解析失败 {item['url']}: {item['error']}")
        else:
            print(f"解析成功 {item['url']}: {item['result']['directLink']}")

# 运行异步示例
# asyncio.run(async_main())
```

## Java 示例

### 基础 API 类

```java
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

public class NetdiskAPI {
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public NetdiskAPI(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 解析分享链接
     */
    public ParseResult parseLink(String url, String password) throws Exception {
        StringBuilder urlBuilder = new StringBuilder(baseUrl + "/json/parser?url=" + 
            URLEncoder.encode(url, "UTF-8"));
        
        if (password != null && !password.isEmpty()) {
            urlBuilder.append("&pwd=").append(URLEncoder.encode(password, "UTF-8"));
        }
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(urlBuilder.toString()))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        JsonNode jsonNode = objectMapper.readTree(response.body());
        
        if (!jsonNode.get("success").asBoolean()) {
            throw new Exception(jsonNode.get("msg").asText());
        }
        
        JsonNode data = jsonNode.get("data");
        return new ParseResult(
            data.get("shareKey").asText(),
            data.get("directLink").asText(),
            data.get("cacheHit").asBoolean(),
            data.get("expires").asText()
        );
    }
    
    /**
     * 获取统计信息
     */
    public Statistics getStatistics() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/v2/statisticsInfo"))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        JsonNode jsonNode = objectMapper.readTree(response.body());
        
        if (!jsonNode.get("success").asBoolean()) {
            throw new Exception(jsonNode.get("msg").asText());
        }
        
        JsonNode data = jsonNode.get("data");
        return new Statistics(
            data.get("parserTotal").asLong(),
            data.get("cacheTotal").asLong(),
            data.get("total").asLong()
        );
    }
    
    // 数据类
    public static class ParseResult {
        private final String shareKey;
        private final String directLink;
        private final boolean cacheHit;
        private final String expires;
        
        public ParseResult(String shareKey, String directLink, boolean cacheHit, String expires) {
            this.shareKey = shareKey;
            this.directLink = directLink;
            this.cacheHit = cacheHit;
            this.expires = expires;
        }
        
        // Getters
        public String getShareKey() { return shareKey; }
        public String getDirectLink() { return directLink; }
        public boolean isCacheHit() { return cacheHit; }
        public String getExpires() { return expires; }
    }
    
    public static class Statistics {
        private final long parserTotal;
        private final long cacheTotal;
        private final long total;
        
        public Statistics(long parserTotal, long cacheTotal, long total) {
            this.parserTotal = parserTotal;
            this.cacheTotal = cacheTotal;
            this.total = total;
        }
        
        // Getters
        public long getParserTotal() { return parserTotal; }
        public long getCacheTotal() { return cacheTotal; }
        public long getTotal() { return total; }
        
        public double getCacheHitRate() {
            return total > 0 ? (double) cacheTotal / total * 100 : 0;
        }
    }
}

// 使用示例
public class Example {
    public static void main(String[] args) {
        NetdiskAPI api = new NetdiskAPI("https://your-domain.com");
        
        try {
            // 解析单个文件
            NetdiskAPI.ParseResult result = api.parseLink("https://lanzoux.com/ia2cntg", "");
            System.out.println("下载链接: " + result.getDirectLink());
            System.out.println("缓存命中: " + result.isCacheHit());
            
            // 获取统计信息
            NetdiskAPI.Statistics stats = api.getStatistics();
            System.out.printf("总请求次数: %,d%n", stats.getTotal());
            System.out.printf("缓存命中率: %.2f%%%n", stats.getCacheHitRate());
            
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
        }
    }
}
```

## Go 示例

```go
package main

import (
    "encoding/json"
    "fmt"
    "io"
    "net/http"
    "net/url"
    "time"
)

type NetdiskAPI struct {
    BaseURL string
    Client  *http.Client
}

type ParseResult struct {
    ShareKey   string `json:"shareKey"`
    DirectLink string `json:"directLink"`
    CacheHit   bool   `json:"cacheHit"`
    Expires    string `json:"expires"`
}

type Statistics struct {
    ParserTotal int64 `json:"parserTotal"`
    CacheTotal  int64 `json:"cacheTotal"`
    Total       int64 `json:"total"`
}

type APIResponse struct {
    Code      int         `json:"code"`
    Msg       string      `json:"msg"`
    Success   bool        `json:"success"`
    Data      interface{} `json:"data"`
    Timestamp int64       `json:"timestamp"`
}

func NewNetdiskAPI(baseURL string) *NetdiskAPI {
    return &NetdiskAPI{
        BaseURL: baseURL,
        Client: &http.Client{
            Timeout: 30 * time.Second,
        },
    }
}

func (api *NetdiskAPI) ParseLink(shareURL, password string) (*ParseResult, error) {
    params := url.Values{}
    params.Add("url", shareURL)
    if password != "" {
        params.Add("pwd", password)
    }
    
    reqURL := fmt.Sprintf("%s/json/parser?%s", api.BaseURL, params.Encode())
    
    resp, err := api.Client.Get(reqURL)
    if err != nil {
        return nil, err
    }
    defer resp.Body.Close()
    
    body, err := io.ReadAll(resp.Body)
    if err != nil {
        return nil, err
    }
    
    var apiResp APIResponse
    if err := json.Unmarshal(body, &apiResp); err != nil {
        return nil, err
    }
    
    if !apiResp.Success {
        return nil, fmt.Errorf(apiResp.Msg)
    }
    
    dataBytes, _ := json.Marshal(apiResp.Data)
    var result ParseResult
    if err := json.Unmarshal(dataBytes, &result); err != nil {
        return nil, err
    }
    
    return &result, nil
}

func (api *NetdiskAPI) GetStatistics() (*Statistics, error) {
    reqURL := fmt.Sprintf("%s/v2/statisticsInfo", api.BaseURL)
    
    resp, err := api.Client.Get(reqURL)
    if err != nil {
        return nil, err
    }
    defer resp.Body.Close()
    
    body, err := io.ReadAll(resp.Body)
    if err != nil {
        return nil, err
    }
    
    var apiResp APIResponse
    if err := json.Unmarshal(body, &apiResp); err != nil {
        return nil, err
    }
    
    if !apiResp.Success {
        return nil, fmt.Errorf(apiResp.Msg)
    }
    
    dataBytes, _ := json.Marshal(apiResp.Data)
    var stats Statistics
    if err := json.Unmarshal(dataBytes, &stats); err != nil {
        return nil, err
    }
    
    return &stats, nil
}

func (s *Statistics) CacheHitRate() float64 {
    if s.Total == 0 {
        return 0
    }
    return float64(s.CacheTotal) / float64(s.Total) * 100
}

func main() {
    api := NewNetdiskAPI("https://your-domain.com")
    
    // 解析单个文件
    result, err := api.ParseLink("https://lanzoux.com/ia2cntg", "")
    if err != nil {
        fmt.Printf("解析失败: %v\n", err)
    } else {
        fmt.Printf("下载链接: %s\n", result.DirectLink)
        fmt.Printf("缓存命中: %t\n", result.CacheHit)
    }
    
    // 获取统计信息
    stats, err := api.GetStatistics()
    if err != nil {
        fmt.Printf("获取统计失败: %v\n", err)
    } else {
        fmt.Printf("总请求次数: %d\n", stats.Total)
        fmt.Printf("缓存命中率: %.2f%%\n", stats.CacheHitRate())
    }
}
```

## PHP 示例

```php
<?php

class NetdiskAPI {
    private $baseUrl;
    private $timeout;
    
    public function __construct($baseUrl = 'http://localhost:6400', $timeout = 30) {
        $this->baseUrl = rtrim($baseUrl, '/');
        $this->timeout = $timeout;
    }
    
    /**
     * 解析分享链接
     */
    public function parseLink($url, $password = '') {
        $params = ['url' => $url];
        if (!empty($password)) {
            $params['pwd'] = $password;
        }
        
        $apiUrl = $this->baseUrl . '/json/parser?' . http_build_query($params);
        
        $response = $this->makeRequest($apiUrl);
        
        if (!$response['success']) {
            throw new Exception($response['msg']);
        }
        
        return $response['data'];
    }
    
    /**
     * 获取文件夹列表
     */
    public function getFileList($url, $password = '') {
        $params = ['url' => $url];
        if (!empty($password)) {
            $params['pwd'] = $password;
        }
        
        $apiUrl = $this->baseUrl . '/v2/getFileList?' . http_build_query($params);
        
        $response = $this->makeRequest($apiUrl);
        
        if (!$response['success']) {
            throw new Exception($response['msg']);
        }
        
        return $response['data'];
    }
    
    /**
     * 获取统计信息
     */
    public function getStatistics() {
        $apiUrl = $this->baseUrl . '/v2/statisticsInfo';
        
        $response = $this->makeRequest($apiUrl);
        
        if (!$response['success']) {
            throw new Exception($response['msg']);
        }
        
        return $response['data'];
    }
    
    /**
     * 发送HTTP请求
     */
    private function makeRequest($url) {
        $context = stream_context_create([
            'http' => [
                'timeout' => $this->timeout,
                'user_agent' => 'NetdiskAPI-PHP/1.0'
            ]
        ]);
        
        $result = file_get_contents($url, false, $context);
        
        if ($result === false) {
            throw new Exception('请求失败');
        }
        
        $data = json_decode($result, true);
        
        if (json_last_error() !== JSON_ERROR_NONE) {
            throw new Exception('JSON解析失败');
        }
        
        return $data;
    }
}

// 使用示例
try {
    $api = new NetdiskAPI('https://your-domain.com');
    
    // 解析单个文件
    $result = $api->parseLink('https://lanzoux.com/ia2cntg');
    echo "下载链接: " . $result['directLink'] . "\n";
    echo "缓存命中: " . ($result['cacheHit'] ? '是' : '否') . "\n";
    
    // 获取统计信息
    $stats = $api->getStatistics();
    echo "总请求次数: " . number_format($stats['total']) . "\n";
    $hitRate = $stats['total'] > 0 ? ($stats['cacheTotal'] / $stats['total'] * 100) : 0;
    echo "缓存命中率: " . number_format($hitRate, 2) . "%\n";
    
} catch (Exception $e) {
    echo "错误: " . $e->getMessage() . "\n";
}
?>
```

这些示例涵盖了主要编程语言的 API 调用方法，包括错误处理、异步操作和批量处理等实用功能。您可以根据实际需求选择合适的语言和实现方式。