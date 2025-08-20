# Spring Boot OpenTelemetry Demo

一个使用Spring Boot和OpenTelemetry手动埋点的商品下单系统演示项目。

## 项目简介

本项目演示了如何在Spring Boot应用中使用OpenTelemetry Java SDK进行手动埋点，实现自定义Span和指标监控。项目模拟了一个简单的电商下单流程，包括商品管理、库存控制和订单处理。

## 技术栈

- **Java 17**
- **Spring Boot 3.2.0**
- **OpenTelemetry 1.23.0**
- **Maven 3.6+**

## 功能特性

### 📊 OpenTelemetry 埋点功能

1. **自定义Span追踪**
   - 订单处理全链路追踪
   - 商品查询和库存检查
   - 支付处理模拟
   - 库存预留操作

2. **自定义指标监控**
   - 商品购买次数计数器（Counter）
   - 库存更新操作计数器（Counter）
   - 当前库存水平仪表（Gauge）
   - 商品总数仪表（Gauge）

3. **上下文传播**
   - Baggage在调用链中的透传
   - TraceId和SpanId的日志关联
   - 异步操作的上下文传播

### 🛒 业务功能

1. **商品管理**
   - 获取所有商品
   - 按ID查询商品
   - 更新商品库存

2. **订单处理**
   - 同步下单
   - 异步下单
   - 订单验证
   - 库存预留
   - 支付处理（模拟）

## 快速开始

### 前提条件

- Java 17或更高版本
- Maven 3.6或更高版本

### 运行项目

1. **克隆项目**
   ```bash
   git clone git@github.com:aliyun-observability/java-manual-instrmentation-demo.git
   cd java-manual-Instrumentation-demo
   ```

2. **打包项目**
   ```bash
   mvn clean package
   ```

3. **运行应用**
   ```bash
   java -javaagent:path/to/aliyun-java-agent.jar 
   -Darms.licenseKey=${replace with you licenseKey} 
   -Darms.appName=${replace with you appName}
   -Daliyun.javaagent.regionId=${replace with your region}
   -Dapsara.apm.metric.custom.include_scope_list=product_managementv
   -jar target/spring-boot-otel-demo-1.0.0-SNAPSHOT.jar
   ```

   应用将在 `http://localhost:8080` 启动。

### API 接口

#### 1. 获取所有商品
```http
GET /api/orders/products
```

#### 2. 获取指定商品
```http
GET /api/orders/products/{productId}
```

#### 3. 下单接口
```http
POST /api/orders
Content-Type: application/json

{
  "productId": 1,
  "quantity": 2,
  "userId": "user123",
  "remarks": "急单处理"
}
```

#### 4. 异步下单接口
```http
POST /api/orders/async
Content-Type: application/json

{
  "productId": 1,
  "quantity": 1,
  "userId": "user456"
}
```

#### 5. 更新库存
```http
PUT /api/orders/products/{productId}/stock?stock=100
```

#### 6. 健康检查
```http
GET /api/orders/health
```

## OpenTelemetry 配置说明

### Span 配置

项目中创建的主要Span类型：

1. **HTTP请求Span** (`SpanKind.SERVER`)
   - `POST /orders` - 下单请求
   - `GET /orders/products` - 获取商品列表
   - `GET /orders/products/{id}` - 获取单个商品

2. **业务逻辑Span** (`SpanKind.INTERNAL`)
   - `order.process` - 订单处理主流程
   - `order.validate` - 订单验证
   - `product.check_availability` - 商品可用性检查
   - `payment.process` - 支付处理
   - `inventory.reserve` - 库存预留

### 指标配置

项目定义的自定义指标：

1. **Counter 指标**
   ```java
   // 商品购买计数器
   product_purchase_count{product_id="1", product_name="iPhone 15 Pro", result="success"}
   
   // 库存更新计数器  
   product_stock_update_count{product_id="1", product_name="iPhone 15 Pro", operation="manual_update"}
   ```

2. **Gauge 指标**
   ```java
   // 当前库存水平
   product_current_stock{product_id="1", product_name="iPhone 15 Pro"}
   
   // 商品总数
   product_total_count
   ```

## 测试示例

### 1. 查看商品信息
```bash
curl -X GET http://localhost:8080/api/orders/products
```

### 2. 下单测试
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "X-User-ID: testuser123" \
  -d '{
    "productId": 1,
    "quantity": 2,
    "remarks": "测试订单"
  }'
```

### 3. 查看应用健康状态
```bash
curl -X GET http://localhost:8080/api/orders/health
```

## 监控数据查看

### 1. 日志输出

应用启动后，您可以在控制台日志中看到：

- **Span信息**: 包含TraceId、SpanId和操作详情
- **指标数据**: 15秒间隔输出的自定义指标
- **业务日志**: 包含TraceId关联的业务操作日志

### 2. 日志格式

```
2024-01-20 10:30:15.123 [http-nio-8080-exec-1] INFO  [1a2b3c4d5e6f7g8h,9i0j1k2l3m4n5o6p] com.example.demo.controller.OrderController - Received order request: OrderRequest{productId=1, quantity=2, userId='testuser123', remarks='测试订单'}
```

### 3. OpenTelemetry 输出示例

**Span 输出**:
```
'order.process' : 1a2b3c4d5e6f7g8h 9i0j1k2l3m4n5o6p SERVER [tracer: spring-boot-otel-demo:1.0.0] AttributesMap{data={operation.result=success, order.id=ORD1642665015123_1, product.id=1, product.name=iPhone 15 Pro, order.quantity=2, user.id=testuser123}}
```

**Metrics 输出**:
```
'product_purchase_count' : 1 {product_id="1", product_name="iPhone 15 Pro", result="success"}
'product_current_stock' : 98 {product_id="1", product_name="iPhone 15 Pro"}
```

## 配置说明

### application.yml 关键配置

```yaml
# 应用配置
app:
  product:
    initial-stock: 100     # 初始库存
    max-stock: 1000       # 最大库存

# 日志配置
logging:
  level:
    com.example: DEBUG    # 应用日志级别
    io.opentelemetry: INFO # OpenTelemetry日志级别
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId},%X{spanId}] %logger{36} - %msg%n"
```

## 扩展说明

### 接入ARMS监控

如需接入阿里云ARMS监控，请参考：

1. [OpenTelemetry Java SDK手动埋点文档](https://help.aliyun.com/zh/arms/application-monitoring/use-cases/use-opentelemetry-sdk-for-java-to-manually-instrument-applications)

2. [自定义指标文档](https://help.aliyun.com/zh/arms/application-monitoring/use-cases/customize-metrics-by-using-the-opentelemetry-java-sdk)

### 生产环境配置

生产环境建议：

1. 使用OTLP Exporter替代Logging Exporter
2. 配置适当的采样率
3. 设置批量处理参数
4. 配置资源属性标识

## 故障排查

### 常见问题

1. **TraceId不连续**: 确保使用`GlobalOpenTelemetry.get()`获取OpenTelemetry实例
2. **指标未上报**: 检查Meter名称配置和MetricReader设置
3. **Span丢失**: 确认Span正确结束（调用`span.end()`）
4. **Baggage传播失败**: 检查Context传播和Scope管理

### 调试建议

1. 启用DEBUG日志查看详细信息
2. 检查OpenTelemetry初始化日志
3. 验证Span和Metric的属性设置
4. 确认异常处理中的状态设置

## 许可证

MIT License

## 联系方式

如有问题请提交Issue或联系开发团队。
