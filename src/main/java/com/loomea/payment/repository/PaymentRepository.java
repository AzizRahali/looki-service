package com.loomea.payment.repository;

import com.loomea.payment.entity.PaymentRecord;
import com.loomea.payment.entity.PaymentRecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentRecord, UUID> {

    Optional<PaymentRecord> findByPaymentRef(String paymentRef);

    Optional<PaymentRecord> findByPaymentRefAndUserId(String paymentRef, UUID userId);

    boolean existsByReferenceIdAndStatus(String referenceId, PaymentRecordStatus status);
}
