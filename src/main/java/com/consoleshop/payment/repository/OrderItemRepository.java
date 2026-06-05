package com.consoleshop.payment.repository;

import com.consoleshop.payment.domain.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, String> {

    List<OrderItem> findByOrderId(String orderId);
}
