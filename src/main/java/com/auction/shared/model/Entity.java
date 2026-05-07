package com.auction.shared.model;

import java.sql.Timestamp;

/**
 * Lớp trừu tượng cơ sở cho tất cả entity trong hệ thống.
 * Áp dụng nguyên tắc Abstraction của OOP.
 */
public abstract class Entity {
    protected int id;
    protected Timestamp createdAt;

    public Entity() {}

    public Entity(int id) {
        this.id = id;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    /**
     * Phương thức trừu tượng - mỗi entity con phải tự triển khai.
     * Áp dụng Polymorphism.
     */
    public abstract String getDisplayInfo();
}
