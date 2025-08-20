package com.example.demo.service;

import com.example.demo.dto.OrderRequest;
import com.example.demo.dto.OrderResponse;
import com.example.demo.model.Product;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Order service with OpenTelemetry tracing instrumentation
 * 
 * This service handles order processing and demonstrates custom span creation
 * and baggage propagation following the Alibaba Cloud ARMS OpenTelemetry documentation.
 */
@Service
public class OrderService {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    
    private final ProductService productService;
    private final Tracer tracer;
    
    // Attribute keys for spans
    private final AttributeKey<String> orderIdKey = AttributeKey.stringKey("order.id");
    private final AttributeKey<Long> productIdKey = AttributeKey.longKey("product.id");
    private final AttributeKey<String> productNameKey = AttributeKey.stringKey("product.name");
    private final AttributeKey<Long> quantityKey = AttributeKey.longKey("order.quantity");
    private final AttributeKey<String> userIdKey = AttributeKey.stringKey("user.id");
    private final AttributeKey<String> operationResultKey = AttributeKey.stringKey("operation.result");
    private final AttributeKey<String> errorMessageKey = AttributeKey.stringKey("error.message");
    
    @Autowired
    public OrderService(ProductService productService, Tracer tracer) {
        this.productService = productService;
        this.tracer = tracer;
        logger.info("OrderService initialized with OpenTelemetry tracing");
    }
    
    /**
     * Process order with comprehensive tracing
     * 
     * @param orderRequest Order request details
     * @return OrderResponse with result
     */
    public OrderResponse processOrder(OrderRequest orderRequest) {
        // Create parent span for the entire order process
        Span orderSpan = tracer.spanBuilder("order.process")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();
        
        try (Scope orderScope = orderSpan.makeCurrent()) {
            // Add order information to span attributes
            orderSpan.setAttribute(productIdKey, orderRequest.getProductId())
                    .setAttribute(quantityKey, (long) orderRequest.getQuantity())
                    .setAttribute(userIdKey, orderRequest.getUserId() != null ? orderRequest.getUserId() : "anonymous");
            
            // Set baggage for cross-cutting concerns
            Baggage baggage = Baggage.current()
                    .toBuilder()
                    .put("user.id", orderRequest.getUserId() != null ? orderRequest.getUserId() : "anonymous")
                    .put("operation.type", "product_order")
                    .put("request.timestamp", String.valueOf(System.currentTimeMillis()))
                    .build();
            
            try (Scope baggageScope = baggage.storeInContext(Context.current()).makeCurrent()) {
                
                logger.info("Processing order for product ID: {}, quantity: {}, user: {}", 
                           orderRequest.getProductId(), orderRequest.getQuantity(), 
                           orderRequest.getUserId());
                
                // Step 1: Validate order request
                OrderResponse validationResult = validateOrder(orderRequest);
                if (!validationResult.isSuccess()) {
                    orderSpan.setAttribute(operationResultKey, "validation_failed")
                            .setAttribute(errorMessageKey, validationResult.getMessage());
                    orderSpan.setStatus(StatusCode.ERROR, validationResult.getMessage());
                    return validationResult;
                }
                
                // Step 2: Check product availability
                Product product = checkProductAvailability(orderRequest.getProductId());
                if (product == null) {
                    String errorMsg = "Product not found: " + orderRequest.getProductId();
                    orderSpan.setAttribute(operationResultKey, "product_not_found")
                            .setAttribute(errorMessageKey, errorMsg);
                    orderSpan.setStatus(StatusCode.ERROR, errorMsg);
                    return OrderResponse.failure(errorMsg);
                }
                
                // Add product info to span
                orderSpan.setAttribute(productNameKey, product.getName());
                
                // Step 3: Process payment (simulated)
                boolean paymentSuccess = processPayment(orderRequest, product);
                if (!paymentSuccess) {
                    String errorMsg = "Payment processing failed";
                    orderSpan.setAttribute(operationResultKey, "payment_failed")
                            .setAttribute(errorMessageKey, errorMsg);
                    orderSpan.setStatus(StatusCode.ERROR, errorMsg);
                    return OrderResponse.failure(errorMsg);
                }
                
                // Step 4: Reserve inventory
                boolean inventoryReserved = reserveInventory(orderRequest.getProductId(), 
                                                           orderRequest.getQuantity());
                if (!inventoryReserved) {
                    String errorMsg = "Insufficient inventory for product: " + product.getName();
                    orderSpan.setAttribute(operationResultKey, "insufficient_inventory")
                            .setAttribute(errorMessageKey, errorMsg);
                    orderSpan.setStatus(StatusCode.ERROR, errorMsg);
                    return OrderResponse.failure(errorMsg);
                }
                
                // Step 5: Generate order ID and create response
                String orderId = productService.generateOrderId();
                orderSpan.setAttribute(orderIdKey, orderId);
                
                BigDecimal totalAmount = product.getPrice()
                        .multiply(new BigDecimal(orderRequest.getQuantity()));
                
                Integer remainingStock = productService.getCurrentStock(orderRequest.getProductId());
                
                OrderResponse response = OrderResponse.success(
                    orderId, 
                    product.getId(), 
                    product.getName(),
                    orderRequest.getQuantity(), 
                    totalAmount, 
                    remainingStock
                );
                
                orderSpan.setAttribute(operationResultKey, "success")
                        .setAttribute(orderIdKey, orderId);
                
                logger.info("Order processed successfully: {}", orderId);
                return response;
                
            }
        } catch (Exception e) {
            orderSpan.setStatus(StatusCode.ERROR, e.getMessage());
            orderSpan.recordException(e);
            logger.error("Error processing order", e);
            return OrderResponse.failure("Internal server error: " + e.getMessage());
        } finally {
            orderSpan.end();
        }
    }
    
    /**
     * Validate order request with tracing
     */
    private OrderResponse validateOrder(OrderRequest orderRequest) {
        Span span = tracer.spanBuilder("order.validate")
                .setParent(Context.current())
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            logger.debug("Validating order request: {}", orderRequest);
            
            if (orderRequest.getProductId() == null) {
                span.setStatus(StatusCode.ERROR, "Product ID is required");
                return OrderResponse.failure("Product ID is required");
            }
            
            if (orderRequest.getQuantity() == null || orderRequest.getQuantity() <= 0) {
                span.setStatus(StatusCode.ERROR, "Valid quantity is required");
                return OrderResponse.failure("Valid quantity is required");
            }
            
            if (orderRequest.getQuantity() > 100) {
                span.setStatus(StatusCode.ERROR, "Quantity exceeds maximum limit");
                return OrderResponse.failure("Quantity cannot exceed 100 items per order");
            }
            
            span.setAttribute(operationResultKey, "success");
            logger.debug("Order validation passed");
            return OrderResponse.success(null, null, null, null, null, null);
            
        } finally {
            span.end();
        }
    }
    
    /**
     * Check product availability with tracing
     */
    private Product checkProductAvailability(Long productId) {
        Span span = tracer.spanBuilder("product.check_availability")
                .setParent(Context.current())
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute(productIdKey, productId);
            
            Product product = productService.getProduct(productId);
            
            if (product != null) {
                span.setAttribute(productNameKey, product.getName())
                    .setAttribute(operationResultKey, "found");
                
                Integer currentStock = productService.getCurrentStock(productId);
                logger.debug("Product found: {} with stock: {}", product.getName(), currentStock);
                
                // Log baggage information
                String userId = Baggage.current().getEntryValue("user.id");
                logger.debug("Current user from baggage: {}", userId);
                
            } else {
                span.setAttribute(operationResultKey, "not_found");
                logger.warn("Product not found: {}", productId);
            }
            
            return product;
            
        } finally {
            span.end();
        }
    }
    
    /**
     * Simulate payment processing with tracing
     */
    private boolean processPayment(OrderRequest orderRequest, Product product) {
        Span span = tracer.spanBuilder("payment.process")
                .setParent(Context.current())
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            BigDecimal amount = product.getPrice()
                    .multiply(new BigDecimal(orderRequest.getQuantity()));
            
            span.setAttribute(productIdKey, product.getId())
                .setAttribute(quantityKey, (long) orderRequest.getQuantity())
                .setAttribute(AttributeKey.stringKey("payment.amount"), amount.toString())
                .setAttribute(AttributeKey.stringKey("payment.currency"), "USD");
            
            // Simulate payment processing delay
            simulateProcessingTime(200, 500);
            
            // Simulate 95% success rate
            boolean success = ThreadLocalRandom.current().nextDouble() < 0.95;
            
            span.setAttribute(operationResultKey, success ? "success" : "failed");
            
            if (success) {
                logger.info("Payment processed successfully for amount: {}", amount);
            } else {
                span.setStatus(StatusCode.ERROR, "Payment processing failed");
                logger.warn("Payment processing failed for amount: {}", amount);
            }
            
            return success;
            
        } finally {
            span.end();
        }
    }
    
    /**
     * Reserve inventory with tracing
     */
    private boolean reserveInventory(Long productId, Integer quantity) {
        Span span = tracer.spanBuilder("inventory.reserve")
                .setParent(Context.current())
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute(productIdKey, productId)
                .setAttribute(quantityKey, (long) quantity);
            
            // Simulate inventory processing time
            simulateProcessingTime(100, 300);
            
            boolean reserved = productService.purchaseProduct(productId, quantity);
            
            span.setAttribute(operationResultKey, reserved ? "success" : "failed");
            
            if (reserved) {
                logger.info("Successfully reserved {} units for product ID: {}", quantity, productId);
            } else {
                span.setStatus(StatusCode.ERROR, "Insufficient inventory");
                logger.warn("Failed to reserve {} units for product ID: {}", quantity, productId);
            }
            
            return reserved;
            
        } finally {
            span.end();
        }
    }
    
    /**
     * Simulate processing time
     */
    private void simulateProcessingTime(int minMs, int maxMs) {
        try {
            int delay = ThreadLocalRandom.current().nextInt(minMs, maxMs);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Get current trace ID for logging correlation
     */
    public String getCurrentTraceId() {
        return Span.current().getSpanContext().getTraceId();
    }
    
    /**
     * Async order processing example
     */
    public CompletableFuture<OrderResponse> processOrderAsync(OrderRequest orderRequest) {
        return CompletableFuture.supplyAsync(() -> {
            // The current context (including baggage and span) is automatically propagated
            return processOrder(orderRequest);
        });
    }
}