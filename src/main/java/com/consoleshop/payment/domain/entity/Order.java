package com.consoleshop.payment.domain.entity;

import com.consoleshop.payment.domain.enums.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/**
 * Minimal Order entity — only fields the payment service needs.
 * Full order management remains in the NestJS backend.
 */
@Entity
@Table(name = "Order")
@Getter
@Setter
public class Order {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "userId", nullable = false, length = 36)
    private String userId;

    // PostgreSQL stores this as a native enum type ("OrderStatus"), not varchar.
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "createdAt")
    private Instant createdAt;

    @Column(name = "updatedAt")
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<OrderItem> items;

    @OneToOne(mappedBy = "order", fetch = FetchType.LAZY)
    private Payment payment;
}
