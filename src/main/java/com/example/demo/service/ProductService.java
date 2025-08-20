package com.example.demo.service;

import com.example.demo.model.Product;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Product service with OpenTelemetry metrics instrumentation
 * 
 * This service manages product inventory and demonstrates custom metrics
 * following the Alibaba Cloud ARMS OpenTelemetry documentation.
 */
@Service
public class ProductService {
    
    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);
    
    // In-memory storage for demo purposes
    private final ConcurrentHashMap<Long, Product> productStorage = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicInteger> stockCounters = new ConcurrentHashMap<>();
    private final AtomicLong orderSequence = new AtomicLong(1);
    
    // OpenTelemetry metrics
    private final LongCounter purchaseCounter;
    private final LongCounter stockUpdateCounter; 
    private final ObservableLongGauge currentStockGauge;
    private final ObservableLongGauge totalProductsGauge;
    
    // Attribute keys for metrics
    private final AttributeKey<String> productIdKey = AttributeKey.stringKey("product_id");
    private final AttributeKey<String> productNameKey = AttributeKey.stringKey("product_name");
    private final AttributeKey<String> resultKey = AttributeKey.stringKey("result");
    private final AttributeKey<String> operationKey = AttributeKey.stringKey("operation");
    
    @Value("${app.product.initial-stock:100}")
    private int initialStock;
    
    public ProductService() {
        // Initialize OpenTelemetry metrics
        OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
        
        // Create meter with product_management scope
        Meter meter = openTelemetry.getMeter("product_management");
        
        // Counter for product purchases
        purchaseCounter = meter.counterBuilder("product_purchase_count")
                .setUnit("1")
                .setDescription("Number of product purchase attempts")
                .build();
        
        // Counter for stock updates
        stockUpdateCounter = meter.counterBuilder("product_stock_update_count")
                .setUnit("1") 
                .setDescription("Number of stock update operations")
                .build();
        
        // Gauge for current stock levels
        currentStockGauge = meter.gaugeBuilder("product_current_stock")
                .ofLongs()
                .setDescription("Current stock level for products")
                .buildWithCallback(measurement -> {
                    stockCounters.forEach((productId, stock) -> {
                        Product product = productStorage.get(productId);
                        if (product != null) {
                            measurement.record(stock.get(), 
                                Attributes.of(
                                    productIdKey, productId.toString(),
                                    productNameKey, product.getName()
                                ));
                        }
                    });
                });
        
        // Gauge for total number of products
        totalProductsGauge = meter.gaugeBuilder("product_total_count")
                .ofLongs()
                .setDescription("Total number of products in the system")
                .buildWithCallback(measurement -> {
                    measurement.record(productStorage.size());
                });
        
        logger.info("ProductService initialized with OpenTelemetry metrics");
        
        // Initialize with sample products
        initializeSampleProducts();
    }
    
    /**
     * Initialize some sample products for demo
     */
    private void initializeSampleProducts() {
        addProduct(new Product(1L, "iPhone 15 Pro", "Latest iPhone with advanced features", 
                              new BigDecimal("999.99"), initialStock, "Electronics"));
        addProduct(new Product(2L, "MacBook Pro M3", "High-performance laptop for professionals", 
                              new BigDecimal("1999.99"), initialStock, "Electronics"));
        addProduct(new Product(3L, "AirPods Pro", "Wireless earbuds with noise cancellation", 
                              new BigDecimal("249.99"), initialStock, "Electronics"));
        
        logger.info("Initialized {} sample products with stock {}", productStorage.size(), initialStock);
    }
    
    /**
     * Add a new product to the inventory
     */
    public void addProduct(Product product) {
        productStorage.put(product.getId(), product);
        stockCounters.put(product.getId(), new AtomicInteger(product.getStock()));
        
        stockUpdateCounter.add(1, Attributes.of(
            productIdKey, product.getId().toString(),
            productNameKey, product.getName(),
            operationKey, "add_product"
        ));
        
        logger.debug("Added product: {} with stock: {}", product.getName(), product.getStock());
    }
    
    /**
     * Get product by ID
     */
    public Product getProduct(Long productId) {
        return productStorage.get(productId);
    }
    
    /**
     * Get current stock for a product
     */
    public Integer getCurrentStock(Long productId) {
        AtomicInteger stock = stockCounters.get(productId);
        return stock != null ? stock.get() : 0;
    }
    
    /**
     * Update stock for a product
     */
    public void updateStock(Long productId, int newStock) {
        AtomicInteger stock = stockCounters.get(productId);
        Product product = productStorage.get(productId);
        
        if (stock != null && product != null) {
            int oldStock = stock.getAndSet(newStock);
            product.setStock(newStock);
            
            stockUpdateCounter.add(1, Attributes.of(
                productIdKey, productId.toString(),
                productNameKey, product.getName(),
                operationKey, "manual_update"
            ));
            
            logger.debug("Updated stock for product {}: {} -> {}", 
                        product.getName(), oldStock, newStock);
        }
    }
    
    /**
     * Attempt to purchase a product (reduce stock)
     * Returns true if successful, false if insufficient stock
     */
    public boolean purchaseProduct(Long productId, int quantity) {
        AtomicInteger stock = stockCounters.get(productId);
        Product product = productStorage.get(productId);
        
        if (stock == null || product == null) {
            purchaseCounter.add(1, Attributes.of(
                productIdKey, productId.toString(),
                resultKey, "product_not_found"
            ));
            return false;
        }
        
        // Atomic stock check and update
        int currentStock = stock.get();
        if (currentStock < quantity) {
            purchaseCounter.add(1, Attributes.of(
                productIdKey, productId.toString(),
                productNameKey, product.getName(),
                resultKey, "insufficient_stock"
            ));
            
            logger.warn("Insufficient stock for product {}: requested={}, available={}", 
                       product.getName(), quantity, currentStock);
            return false;
        }
        
        // Try to decrement stock atomically
        while (true) {
            int current = stock.get();
            if (current < quantity) {
                purchaseCounter.add(1, Attributes.of(
                    productIdKey, productId.toString(),
                    productNameKey, product.getName(),
                    resultKey, "insufficient_stock"
                ));
                return false;
            }
            
            if (stock.compareAndSet(current, current - quantity)) {
                product.setStock(current - quantity);
                
                purchaseCounter.add(1, Attributes.of(
                    productIdKey, productId.toString(),
                    productNameKey, product.getName(),
                    resultKey, "success"
                ));
                
                logger.info("Successfully purchased {} units of product {}, remaining stock: {}", 
                           quantity, product.getName(), current - quantity);
                return true;
            }
            // Retry if CAS failed due to concurrent modification
        }
    }
    
    /**
     * Generate a unique order ID
     */
    public String generateOrderId() {
        return "ORD" + System.currentTimeMillis() + "_" + orderSequence.getAndIncrement();
    }
    
    /**
     * Get all products for listing
     */
    public java.util.Collection<Product> getAllProducts() {
        // Update stock values in products before returning
        productStorage.values().forEach(product -> {
            AtomicInteger stock = stockCounters.get(product.getId());
            if (stock != null) {
                product.setStock(stock.get());
            }
        });
        return productStorage.values();
    }
    
    @PreDestroy
    public void cleanup() {
        if (currentStockGauge != null) {
            currentStockGauge.close();
        }
        if (totalProductsGauge != null) {
            totalProductsGauge.close();
        }
        logger.info("ProductService cleanup completed");
    }
}