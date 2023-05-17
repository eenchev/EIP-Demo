package dev.evgeni.integrationsandbox.model;

import java.util.Date;

public class Item {
    
    public Item(String itemNo, String description, Integer inventoryStatus, Date createdAt) {
        this.itemNo = itemNo;
        this.description = description;
        this.inventoryStatus = inventoryStatus;
        this.createdAt = createdAt;
    }
    
    private String itemNo;
    private String description;
    private Integer inventoryStatus;
    private Date createdAt;

    public String getItemNo() {
        return itemNo;
    }
    public void setItemNo(String itemNo) {
        this.itemNo = itemNo;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public Integer getInventoryStatus() {
        return inventoryStatus;
    }
    public void setInventoryStatus(Integer inventoryStatus) {
        this.inventoryStatus = inventoryStatus;
    }
    public Date getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
