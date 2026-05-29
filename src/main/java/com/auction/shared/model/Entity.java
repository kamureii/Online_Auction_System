package com.auction.shared.model;

import java.sql.Timestamp;

/**
 * Lớp nền cho các model có id và thời điểm tạo.
 * OOP: dùng abstraction để gom phần chung, còn cách hiển thị do từng lớp con quyết định.
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
     * Mỗi lớp con trả về mô tả riêng, đây là điểm thể hiện polymorphism.
     */
    public abstract String getDisplayInfo();
}
