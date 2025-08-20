package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Order request DTO for placing orders
 */
public class OrderRequest {
    
    @NotNull(message = "Product ID cannot be null")
    private Long productId;
    
    @NotNull(message = "Quantity cannot be null")
    @Positive(message = "Quantity must be positive")
    private Integer quantity;
    
    private String userId;
    private String remarks;
    
    // Default constructor
    public OrderRequest() {}
    
    // Parameterized constructor
    public OrderRequest(Long productId, Integer quantity, String userId) {
        this.productId = productId;
        this.quantity = quantity;
        this.userId = userId;
    }
    
    // Getters and Setters
    public Long getProductId() {
        return productId;
    }
    
    public void setProductId(Long productId) {
        this.productId = productId;
    }
    
    public Integer getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getRemarks() {
        return remarks;
    }
    
    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
    
    @Override
    public String toString() {
        return "OrderRequest{" +
                "productId=" + productId +
                ", quantity=" + quantity +
                ", userId='" + userId + '\'' +
                ", remarks='" + remarks + '\'' +
                '}';
    }
}