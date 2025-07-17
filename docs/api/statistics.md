# 统计接口

统计接口提供系统的解析次数统计信息，包括总解析次数、缓存命中次数等。

## 接口说明

### 解析统计接口

```
GET /v2/statisticsInfo
```

### 请求参数

无需参数

### 响应格式

```json
{
  "code": 200,
  "msg": "success",
  "success": true,
  "count": 0,
  "data": {
    "parserTotal": 320508,
    "cacheTotal": 5957910,
    "total": 6278418
  },
  "timestamp": 1736489378770
}
```

## 字段说明

| 字段名 | 类型 | 说明 |
|--------|------|------|
| parserTotal | long | 实际解析次数（直接从网盘获取） |
| cacheTotal | long | 缓存命中次数（从缓存返回） |
| total | long | 总请求次数（parserTotal + cacheTotal） |

## 使用示例

### 1. 基础调用

```bash
# 获取统计信息
curl "http://localhost:6400/v2/statisticsInfo"
```

### 2. JavaScript 调用

```javascript
async function getStatistics() {
  try {
    const response = await fetch('/v2/statisticsInfo');
    const data = await response.json();
    
    if (data.success) {
      const stats = data.data;
      console.log(`总请求次数: ${stats.total.toLocaleString()}`);
      console.log(`实际解析次数: ${stats.parserTotal.toLocaleString()}`);
      console.log(`缓存命中次数: ${stats.cacheTotal.toLocaleString()}`);
      console.log(`缓存命中率: ${((stats.cacheTotal / stats.total) * 100).toFixed(2)}%`);
      
      return stats;
    } else {
      throw new Error(data.msg);
    }
  } catch (error) {
    console.error('获取统计信息失败:', error);
    throw error;
  }
}

// 使用示例
getStatistics()
  .then(stats => {
    // 处理统计数据
    displayStatistics(stats);
  })
  .catch(error => {
    console.error('Error:', error);
  });
```

### 3. Python 调用

```python
import requests
import json

def get_statistics():
    """获取解析统计信息"""
    try:
        response = requests.get(
            'http://localhost:6400/v2/statisticsInfo',
            timeout=10
        )
        data = response.json()
        
        if data['success']:
            stats = data['data']
            print(f"总请求次数: {stats['total']:,}")
            print(f"实际解析次数: {stats['parserTotal']:,}")
            print(f"缓存命中次数: {stats['cacheTotal']:,}")
            
            # 计算缓存命中率
            if stats['total'] > 0:
                hit_rate = (stats['cacheTotal'] / stats['total']) * 100
                print(f"缓存命中率: {hit_rate:.2f}%")
            
            return stats
        else:
            raise Exception(data['msg'])
            
    except requests.RequestException as e:
        raise Exception(f'请求失败: {e}')

# 使用示例
try:
    statistics = get_statistics()
except Exception as e:
    print(f'获取统计信息失败: {e}')
```

### 4. 定时监控示例

```javascript
class StatisticsMonitor {
  constructor(interval = 60000) { // 默认1分钟更新一次
    this.interval = interval;
    this.lastStats = null;
    this.isRunning = false;
  }
  
  async start() {
    if (this.isRunning) return;
    
    this.isRunning = true;
    console.log('开始监控统计信息...');
    
    while (this.isRunning) {
      try {
        const currentStats = await getStatistics();
        this.analyzeStats(currentStats);
        this.lastStats = currentStats;
      } catch (error) {
        console.error('监控失败:', error);
      }
      
      // 等待指定间隔
      await new Promise(resolve => setTimeout(resolve, this.interval));
    }
  }
  
  stop() {
    this.isRunning = false;
    console.log('停止监控统计信息');
  }
  
  analyzeStats(currentStats) {
    if (!this.lastStats) {
      console.log('初始统计:', currentStats);
      return;
    }
    
    const deltaTotal = currentStats.total - this.lastStats.total;
    const deltaParser = currentStats.parserTotal - this.lastStats.parserTotal;
    const deltaCache = currentStats.cacheTotal - this.lastStats.cacheTotal;
    
    if (deltaTotal > 0) {
      console.log(`新增请求: ${deltaTotal}`);
      console.log(`新增解析: ${deltaParser}`);
      console.log(`新增缓存命中: ${deltaCache}`);
      
      const recentHitRate = deltaCache / deltaTotal * 100;
      console.log(`最近缓存命中率: ${recentHitRate.toFixed(2)}%`);
    }
  }
}

// 使用示例
const monitor = new StatisticsMonitor(30000); // 30秒更新一次
monitor.start();

// 停止监控
// monitor.stop();
```

## 性能指标分析

### 缓存命中率分析

```javascript
function analyzePerformance(stats) {
  const hitRate = (stats.cacheTotal / stats.total) * 100;
  
  let performance = '';
  if (hitRate >= 80) {
    performance = '优秀';
  } else if (hitRate >= 60) {
    performance = '良好';
  } else if (hitRate >= 40) {
    performance = '一般';
  } else {
    performance = '需要优化';
  }
  
  return {
    hitRate: hitRate.toFixed(2),
    performance: performance,
    suggestions: getSuggestions(hitRate)
  };
}

function getSuggestions(hitRate) {
  const suggestions = [];
  
  if (hitRate < 40) {
    suggestions.push('考虑增加缓存时间');
    suggestions.push('检查是否有大量唯一链接请求');
  } else if (hitRate < 60) {
    suggestions.push('可以适当增加缓存时间');
  } else if (hitRate >= 80) {
    suggestions.push('缓存效果良好，保持当前配置');
  }
  
  return suggestions;
}
```

### 使用趋势监控

```javascript
class TrendAnalyzer {
  constructor() {
    this.history = [];
    this.maxHistory = 24; // 保留24个数据点
  }
  
  addDataPoint(stats) {
    const dataPoint = {
      timestamp: Date.now(),
      ...stats
    };
    
    this.history.push(dataPoint);
    
    // 保持历史数据在指定范围内
    if (this.history.length > this.maxHistory) {
      this.history.shift();
    }
  }
  
  getTrend() {
    if (this.history.length < 2) {
      return { trend: 'insufficient_data' };
    }
    
    const latest = this.history[this.history.length - 1];
    const previous = this.history[this.history.length - 2];
    
    const totalGrowth = latest.total - previous.total;
    const parserGrowth = latest.parserTotal - previous.parserTotal;
    const cacheGrowth = latest.cacheTotal - previous.cacheTotal;
    
    return {
      totalGrowth,
      parserGrowth,
      cacheGrowth,
      timestamp: latest.timestamp,
      trend: totalGrowth > 0 ? 'increasing' : 'stable'
    };
  }
  
  getHourlyAverage() {
    if (this.history.length < 2) return 0;
    
    const timeSpan = this.history[this.history.length - 1].timestamp - 
                    this.history[0].timestamp;
    const totalRequests = this.history[this.history.length - 1].total - 
                         this.history[0].total;
    
    // 转换为每小时请求数
    return (totalRequests / timeSpan) * 3600000;
  }
}
```

## 健康检查

统计接口也可以用作健康检查端点：

```javascript
async function healthCheck() {
  try {
    const response = await fetch('/v2/statisticsInfo', {
      timeout: 5000
    });
    
    if (response.ok) {
      const data = await response.json();
      if (data.success) {
        return {
          status: 'healthy',
          data: data.data
        };
      }
    }
    
    return { status: 'unhealthy', error: 'Invalid response' };
  } catch (error) {
    return { status: 'unhealthy', error: error.message };
  }
}

// 定期健康检查
setInterval(async () => {
  const health = await healthCheck();
  if (health.status === 'unhealthy') {
    console.error('服务健康检查失败:', health.error);
    // 可以在这里添加告警逻辑
  }
}, 30000); // 每30秒检查一次
```

## 数据可视化

### 简单的控制台图表

```javascript
function displayStatsChart(stats) {
  const total = stats.total;
  const cache = stats.cacheTotal;
  const parser = stats.parserTotal;
  
  const cachePercent = Math.round((cache / total) * 50); // 50字符宽度
  const parserPercent = 50 - cachePercent;
  
  console.log('请求类型分布:');
  console.log('缓存命中 |' + '█'.repeat(cachePercent) + ' ' + 
              cache.toLocaleString() + ' (' + ((cache/total)*100).toFixed(1) + '%)');
  console.log('实际解析 |' + '█'.repeat(parserPercent) + ' ' + 
              parser.toLocaleString() + ' (' + ((parser/total)*100).toFixed(1) + '%)');
  console.log('总计     |' + '█'.repeat(50) + ' ' + total.toLocaleString());
}
```

## 错误处理

### 常见错误

```json
{
  "code": 500,
  "msg": "统计服务暂时不可用",
  "success": false,
  "data": null,
  "timestamp": 1726637151902
}
```

### 错误处理示例

```javascript
async function getStatisticsWithRetry(maxRetries = 3) {
  for (let i = 0; i < maxRetries; i++) {
    try {
      return await getStatistics();
    } catch (error) {
      if (i === maxRetries - 1) {
        throw error;
      }
      
      console.warn(`获取统计信息失败，重试 ${i + 1}/${maxRetries}:`, error.message);
      await new Promise(resolve => setTimeout(resolve, 1000 * (i + 1)));
    }
  }
}
```

## 应用场景

1. **系统监控**: 监控服务运行状态和性能
2. **容量规划**: 根据使用趋势规划服务器资源
3. **性能优化**: 分析缓存效果，优化配置
4. **用户分析**: 了解用户使用模式
5. **SLA监控**: 监控服务可用性

::: tip 提示
- 统计接口响应很快，适合作为健康检查端点
- 可以结合监控系统（如Prometheus）采集指标
- 建议定期备份统计数据，用于长期趋势分析
:::