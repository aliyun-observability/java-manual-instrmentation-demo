package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Order response DTO for order placement results
 */
public class OrderResponse {
    
    private boolean success;
    private String message;
    private String orderId;
    private Long productId;
    private String productName;
    private Integer quantityOrdered;
    private BigDecimal totalAmount;
    private Integer remainingStock;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime orderTime;
    
    // Default constructor
    public OrderResponse() {
        this.orderTime = LocalDateTime.now();
    }
    
    // Constructor for success response
    public OrderResponse(boolean success, String message, String orderId, Long productId, 
                        String productName, Integer quantityOrdered, BigDecimal totalAmount, 
                        Integer remainingStock) {
        this();
        this.success = success;
        this.message = message;
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.quantityOrdered = quantityOrdered;
        this.totalAmount = totalAmount;
        this.remainingStock = remainingStock;
    }
    
    // Constructor for error response
    public OrderResponse(boolean success, String message) {
        this();
        this.success = success;
        this.message = message;
    }
    
    // Static factory methods
    public static OrderResponse success(String orderId, Long productId, String productName, 
                                      Integer quantityOrdered, BigDecimal totalAmount, 
                                      Integer remainingStock) {
        return new OrderResponse(true, "Order placed successfully", orderId, productId, 
                               productName, quantityOrdered, totalAmount, remainingStock);
    }
    
    public static OrderResponse failure(String message) {
        return new OrderResponse(false, message);
    }
    
    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getOrderId() {
        return orderId;
    }
    
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
    
    public Long getProductId() {
        return productId;
    }
    
    public void setProductId(Long productId) {
        this.productId = productId;
    }
    
    public String getProductName() {
        return productName;
    }
    
    public void setProductName(String productName) {
        this.productName = productName;
    }
    
    public Integer getQuantityOrdered() {
        return quantityOrdered;
    }
    
    public void setQuantityOrdered(Integer quantityOrdered) {
        this.quantityOrdered = quantityOrdered;
    }
    
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
    
    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
    
    public Integer getRemainingStock() {
        return remainingStock;
    }
    
    public void setRemainingStock(Integer remainingStock) {
        this.remainingStock = remainingStock;
    }
    
    public LocalDateTime getOrderTime() {
        return orderTime;
    }
    
    public void setOrderTime(LocalDateTime orderTime) {
        this.orderTime = orderTime;
    }
    
    @Override
    public String toString() {
        return "OrderResponse{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", orderId='" + orderId + '\'' +
                ", productId=" + productId +
                ", productName='" + productName + '\'' +
                ", quantityOrdered=" + quantityOrdered +
                ", totalAmount=" + totalAmount +
                ", remainingStock=" + remainingStock +
                ", orderTime=" + orderTime +
                '}';
    }
}