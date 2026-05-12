package com.loomea.auth.repository;

import com.loomea.auth.entity.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, UUID> {

    /** Latest unused, non-expired OTP for a phone number. */
    @Query("SELECT o FROM PasswordResetOtp o WHERE o.phone = :phone AND o.used = false AND o.expiresAt > :now ORDER BY o.createdAt DESC LIMIT 1")
    Optional<PasswordResetOtp> findLatestValid(@Param("phone") String phone, @Param("now") Instant now);

    /** Invalidate all previous OTP sessions for a phone (called on resend). */
    @Modifying
    @Query("UPDATE PasswordResetOtp o SET o.used = true WHERE o.phone = :phone AND o.used = false")
    void invalidateAllForPhone(@Param("phone") String phone);

    /** How many OTP requests were made for this phone in the last hour. */
    @Query("SELECT COUNT(o) FROM PasswordResetOtp o WHERE o.phone = :phone AND o.createdAt > :since")
    long countRecentRequests(@Param("phone") String phone, @Param("since") Instant since);
}
