package com.consoleshop.payment.hospital.repository;

import com.consoleshop.payment.hospital.domain.postgres.HospitalPaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HospitalPaymentRecordRepository extends JpaRepository<HospitalPaymentRecord, String> {

    Optional<HospitalPaymentRecord> findByBookingNumber(String bookingNumber);

    Optional<HospitalPaymentRecord> findByChargeId(String chargeId);
}
