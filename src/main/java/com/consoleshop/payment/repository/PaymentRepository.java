package com.consoleshop.payment.repository;

import com.consoleshop.payment.domain.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {

    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findByChargeId(String chargeId);
}
