package com.consoleshop.payment.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Minimal OrderItem — only used to restore stock on payment failure.
 */
@Entity
@Table(name = "OrderItem")
@Getter
@Setter
public class OrderItem {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "orderId", length = 36)
    private String orderId;

    @Column(name = "productId", length = 36)
    private String productId;

    @Column(name = "eventId", length = 36)
    private String eventId;

    @Column(name = "quantity")
    private int quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orderId", insertable = false, updatable = false)
    private Order order;
}
