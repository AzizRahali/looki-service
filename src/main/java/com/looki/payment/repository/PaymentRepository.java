package com.looki.payment.repository;

import com.looki.payment.entity.PaymentRecord;
import com.looki.payment.entity.PaymentRecordStatus;
import com.looki.payment.entity.PaymentType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentRecord, UUID> {

    Optional<PaymentRecord> findByPaymentRef(String paymentRef);

    Optional<PaymentRecord> findByPaymentRefAndUserId(String paymentRef, UUID userId);

    boolean existsByReferenceIdAndStatus(String referenceId, PaymentRecordStatus status);

    /**
     * Row-locks the record so the webhook and the status poll can't fulfil
     * the same payment twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentRecord p WHERE p.paymentRef = :ref")
    Optional<PaymentRecord> findForUpdate(@Param("ref") String paymentRef);

    List<PaymentRecord> findByTypeAndReferenceId(PaymentType type, String referenceId);

    List<PaymentRecord> findByUserIdAndStatusInOrderByCreatedAtDesc(
            UUID userId, Collection<PaymentRecordStatus> statuses);
}
