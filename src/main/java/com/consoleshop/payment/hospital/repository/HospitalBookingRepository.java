package com.consoleshop.payment.hospital.repository;

import com.consoleshop.payment.hospital.domain.mongo.HospitalBookingDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HospitalBookingRepository extends MongoRepository<HospitalBookingDocument, String> {

    Optional<HospitalBookingDocument> findByBookingNumber(String bookingNumber);

    Optional<HospitalBookingDocument> findByPaymentTransactionId(String chargeId);
}
