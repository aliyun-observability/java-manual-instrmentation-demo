package com.example.demo.controller;

import com.example.demo.dto.OrderRequest;
import com.example.demo.dto.OrderResponse;
import com.example.demo.model.Product;
import com.example.demo.service.OrderService;
import com.example.demo.service.ProductService;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Order controller with OpenTelemetry instrumentation
 * 
 * This controller provides REST APIs for product ordering and demonstrates
 * how to integrate OpenTelemetry tracing with Spring Boot web endpoints.
 */
@RestController
@RequestMapping("/orders")
public class OrderController {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    
    private final OrderService orderService;
    private final ProductService productService;
    private final Tracer tracer;
    
    @Autowired
    public OrderController(OrderService orderService, ProductService productService, Tracer tracer) {
        this.orderService = orderService;
        this.productService = productService;
        this.tracer = tracer;
        logger.info("OrderController initialized");
    }
    
    /**
     * Place a new order
     * 
     * @param orderRequest Order details
     * @return Order response with result
     */
    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody OrderRequest orderRequest,
                                                   @RequestHeader(value = "X-User-ID", required = false) String userId) {
        
        // Create span for the HTTP request
        Span httpSpan = tracer.spanBuilder("POST /orders")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();
        
        try (Scope scope = httpSpan.makeCurrent()) {
            // Set trace ID in MDC for log correlation
            MDC.put("traceId", Span.current().getSpanContext().getTraceId());
            MDC.put("spanId", Span.current().getSpanContext().getSpanId());
            
            // Add HTTP attributes to span
            httpSpan.setAttribute(io.opentelemetry.api.common.AttributeKey.stringKey("http.method"), "POST")
                   .setAttribute(io.opentelemetry.api.common.AttributeKey.stringKey("http.route"), "/orders")
                   .setAttribute(io.opentelemetry.api.common.AttributeKey.stringKey("http.user_agent"), "Spring-Boot-Demo/1.0.0");
            
            // Set baggage with request information
            Baggage baggage = Baggage.current()
                    .toBuilder()
                    .put("user.id", userId != null ? userId : "anonymous")
                    .put("endpoint", "POST /orders")
                    .put("client.type", "rest_api")
                    .build();
            
            try (Scope baggageScope = baggage.storeInContext(Context.current()).makeCurrent()) {
                
                // Override user ID from header if provided
                if (userId != null) {
                    orderRequest.setUserId(userId);
                    httpSpan.setAttribute("user.id", userId);
                }
                
                logger.info("Received order request: {}", orderRequest);
                logger.debug("Current traceId: {}", Span.current().getSpanContext().getTraceId());
                
                // Process the order
                OrderResponse response = orderService.processOrder(orderRequest);
                
                // Set response attributes
                if (response.isSuccess()) {
                    httpSpan.setStatus(StatusCode.OK);
                    httpSpan.setAttribute("order.id", response.getOrderId());
                    httpSpan.setAttribute("http.status_code", 200);
                    
                    logger.info("Order placed successfully: {}", response.getOrderId());
                    return ResponseEntity.ok(response);
                } else {
                    httpSpan.setStatus(StatusCode.ERROR, response.getMessage());
                    httpSpan.setAttribute("http.status_code", 400);
                    httpSpan.setAttribute("error.message", response.getMessage());
                    
                    logger.warn("Order placement failed: {}", response.getMessage());
                    return ResponseEntity.badRequest().body(response);
                }
            }
            
        } catch (Exception e) {
            httpSpan.setStatus(StatusCode.ERROR, e.getMessage());
            httpSpan.recordException(e);
            httpSpan.setAttribute("http.status_code", 500);
            
            logger.error("Error processing order request", e);
            
            OrderResponse errorResponse = OrderResponse.failure("Internal server error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            
        } finally {
            httpSpan.end();
            MDC.clear();
        }
    }
    
    /**
     * Place order asynchronously
     * 
     * @param orderRequest Order details
     * @return Async order response
     */
    @PostMapping("/async")
    public CompletableFuture<ResponseEntity<OrderResponse>> placeOrderAsync(
            @Valid @RequestBody OrderRequest orderRequest,
            @RequestHeader(value = "X-User-ID", required = false) String userId) {
        
        Span asyncSpan = tracer.spanBuilder("POST /orders/async")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();
        
        try (Scope scope = asyncSpan.makeCurrent()) {
            if (userId != null) {
                orderRequest.setUserId(userId);
            }
            
            logger.info("Received async order request: {}", orderRequest);
            
            return orderService.processOrderAsync(orderRequest)
                    .thenApply(response -> {
                        if (response.isSuccess()) {
                            asyncSpan.setStatus(StatusCode.OK);
                            asyncSpan.setAttribute("order.id", response.getOrderId());
                            return ResponseEntity.ok(response);
                        } else {
                            asyncSpan.setStatus(StatusCode.ERROR, response.getMessage());
                            return ResponseEntity.badRequest().body(response);
                        }
                    })
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            asyncSpan.setStatus(StatusCode.ERROR, throwable.getMessage());
                            asyncSpan.recordException(throwable);
                        }
                        asyncSpan.end();
                    });
                    
        } catch (Exception e) {
            asyncSpan.setStatus(StatusCode.ERROR, e.getMessage());
            asyncSpan.recordException(e);
            asyncSpan.end();
            
            OrderResponse errorResponse = OrderResponse.failure("Internal server error");
            return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
        }
    }
    
    /**
     * Get all products
     * 
     * @return List of all products
     */
    @GetMapping("/products")
    public ResponseEntity<Collection<Product>> getAllProducts() {
        Span span = tracer.spanBuilder("GET /orders/products")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            logger.debug("Fetching all products");
            
            Collection<Product> products = productService.getAllProducts();
            
            span.setAttribute(io.opentelemetry.api.common.AttributeKey.longKey("products.count"), (long) products.size())
                .setAttribute(io.opentelemetry.api.common.AttributeKey.stringKey("http.method"), "GET")
                .setAttribute(io.opentelemetry.api.common.AttributeKey.stringKey("http.route"), "/orders/products");
            
            logger.debug("Retrieved {} products", products.size());
            return ResponseEntity.ok(products);
            
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            logger.error("Error fetching products", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            span.end();
        }
    }
    
    /**
     * Get product by ID
     * 
     * @param productId Product ID
     * @return Product details
     */
    @GetMapping("/products/{productId}")
    public ResponseEntity<Product> getProduct(@PathVariable Long productId) {
        Span span = tracer.spanBuilder("GET /orders/products/{productId}")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute("product.id", productId);
            
            logger.debug("Fetching product: {}", productId);
            
            Product product = productService.getProduct(productId);
            
            if (product != null) {
                span.setAttribute("product.name", product.getName());
                logger.debug("Product found: {}", product.getName());
                return ResponseEntity.ok(product);
            } else {
                span.setStatus(StatusCode.ERROR, "Product not found");
                logger.warn("Product not found: {}", productId);
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            logger.error("Error fetching product: {}", productId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            span.end();
        }
    }
    
    /**
     * Update product stock (for testing)
     * 
     * @param productId Product ID
     * @param stock New stock value
     * @return Success response
     */
    @PutMapping("/products/{productId}/stock")
    public ResponseEntity<Map<String, Object>> updateProductStock(
            @PathVariable Long productId, 
            @RequestParam Integer stock) {
        
        Span span = tracer.spanBuilder("PUT /orders/products/{productId}/stock")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute(io.opentelemetry.api.common.AttributeKey.longKey("product.id"), productId)
                .setAttribute(io.opentelemetry.api.common.AttributeKey.longKey("stock.new_value"), (long) stock);
            
            logger.info("Updating stock for product {}: {}", productId, stock);
            
            Product product = productService.getProduct(productId);
            if (product == null) {
                span.setStatus(StatusCode.ERROR, "Product not found");
                return ResponseEntity.notFound().build();
            }
            
            productService.updateStock(productId, stock);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("productId", productId);
            response.put("productName", product.getName());
            response.put("newStock", stock);
            response.put("message", "Stock updated successfully");
            
            span.setAttribute("product.name", product.getName());
            logger.info("Stock updated successfully for product: {}", product.getName());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            logger.error("Error updating product stock", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error updating stock: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            span.end();
        }
    }
    
    /**
     * Health check endpoint with tracing
     * 
     * @return Health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Span span = tracer.spanBuilder("GET /orders/health")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("timestamp", System.currentTimeMillis());
            health.put("traceId", Span.current().getSpanContext().getTraceId());
            
            // Add some business metrics
            Collection<Product> products = productService.getAllProducts();
            health.put("totalProducts", products.size());
            
            int totalStock = products.stream()
                    .mapToInt(Product::getStock)
                    .sum();
            health.put("totalStock", totalStock);
            
            logger.debug("Health check completed: total products={}, total stock={}", 
                        products.size(), totalStock);
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            logger.error("Health check failed", e);
            
            Map<String, Object> errorHealth = new HashMap<>();
            errorHealth.put("status", "DOWN");
            errorHealth.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorHealth);
        } finally {
            span.end();
        }
    }
}