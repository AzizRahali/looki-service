package com.loomea.booking.repository;

import com.loomea.booking.entity.Appointment;
import com.loomea.booking.entity.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    // ── Client: upcoming = PENDING or CONFIRMED, future dates ────────────

    @Query("SELECT a FROM Appointment a WHERE a.user.id = :userId " +
            "AND a.date >= :today AND a.status IN ('PENDING', 'CONFIRMED') " +
            "ORDER BY a.date ASC, a.startTime ASC")
    List<Appointment> findUpcoming(@Param("userId") UUID userId,
                                   @Param("today") LocalDate today);

    // ── Client: past ──────────────────────────────────────────────────────

    @Query("SELECT a FROM Appointment a WHERE a.user.id = :userId " +
            "AND (a.date < :today OR a.status IN ('COMPLETED','CANCELLED','NO_SHOW')) " +
            "ORDER BY a.date DESC")
    List<Appointment> findPast(@Param("userId") UUID userId,
                               @Param("today") LocalDate today);

    // ── Client: one active booking per user per SALON per day ──────────────

    @Query("SELECT a FROM Appointment a WHERE a.user.id = :userId " +
            "AND a.salon.id = :salonId " +
            "AND a.date = :date AND a.status IN ('PENDING', 'CONFIRMED')")
    List<Appointment> findActiveByUserAndSalonAndDate(
            @Param("userId") UUID userId,
            @Param("salonId") UUID salonId,
            @Param("date") LocalDate date);

    // ── Atomic conflict COUNT — only CONFIRMED blocks new requests ────────

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.salon.id = :salonId " +
            "AND a.date = :date AND a.status = 'CONFIRMED' " +
            "AND a.startTime < :endTime AND a.endTime > :startTime")
    long countConflicts(
            @Param("salonId") UUID salonId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime);

    // ── Slot-conflict check: only CONFIRMED blocks slots ─────────────────

    @Query("SELECT a FROM Appointment a WHERE a.salon.id = :salonId " +
            "AND a.date = :date AND a.status = 'CONFIRMED' " +
            "AND a.startTime < :endTime AND a.endTime > :startTime")
    List<Appointment> findConflicts(
            @Param("salonId") UUID salonId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime);

    // ── Slot display: CONFIRMED + COMPLETED block slots ───────────────────
    // PENDING appointments are requests only; they don't block the slot display.

    @Query("SELECT a FROM Appointment a WHERE a.salon.id = :salonId " +
            "AND a.date = :date AND a.status IN ('CONFIRMED', 'COMPLETED')")
    List<Appointment> findActiveBySalonAndDate(
            @Param("salonId") UUID salonId,
            @Param("date") LocalDate date);

    // ── Auto-cancel: PENDING appointments overlapping a confirmed slot ────

    @Query("SELECT a FROM Appointment a WHERE a.salon.id = :salonId " +
            "AND a.date = :date AND a.status = 'PENDING' " +
            "AND a.startTime < :endTime AND a.endTime > :startTime " +
            "AND a.id != :excludeId")
    List<Appointment> findConflictingPending(
            @Param("salonId") UUID salonId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("excludeId") UUID excludeId);

    // ── Expiration: expire stale PENDING bookings ─────────────────────────

    @Modifying
    @Query("UPDATE Appointment a SET a.status = :newStatus " +
            "WHERE a.status = 'PENDING' AND a.createdAt < :cutoff")
    int expirePendingBefore(@Param("cutoff") Instant cutoff,
                            @Param("newStatus") AppointmentStatus newStatus);

    // ── Account deletion: fetch all appointments for a user ────────────────

    @Query("SELECT a FROM Appointment a WHERE a.user.id = :userId")
    List<Appointment> findByUserId(@Param("userId") UUID userId);

    // ── Reliability: count active bookings for a user ─────────────────────

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.user.id = :userId " +
            "AND a.status IN ('PENDING', 'CONFIRMED')")
    long countActiveByUserId(@Param("userId") UUID userId);

    // ── Confirmation sweep: unconfirmed appointments inside the window ─────

    @Query("SELECT a FROM Appointment a WHERE a.requiresClientConfirmation = true " +
            "AND a.clientConfirmedAt IS NULL " +
            "AND a.status = 'CONFIRMED' " +
            "AND a.date = :date " +
            "AND a.startTime > :now " +
            "AND a.startTime <= :deadline")
    List<Appointment> findUnconfirmedInWindow(
            @Param("date") LocalDate date,
            @Param("now") LocalTime now,
            @Param("deadline") LocalTime deadline);

    // ── Owner: upcoming = PENDING or CONFIRMED ────────────────────────────

    @Query("SELECT a FROM Appointment a WHERE a.salon.id = :salonId " +
            "AND a.date >= :today AND a.status IN ('PENDING', 'CONFIRMED') " +
            "ORDER BY a.date ASC, a.startTime ASC")
    List<Appointment> findUpcomingBySalon(@Param("salonId") UUID salonId,
                                          @Param("today") LocalDate today);

    // ── Owner: past ───────────────────────────────────────────────────────

    @Query("SELECT a FROM Appointment a WHERE a.salon.id = :salonId " +
            "AND (a.date < :today OR a.status IN ('COMPLETED','CANCELLED','NO_SHOW')) " +
            "ORDER BY a.date DESC")
    List<Appointment> findPastBySalon(@Param("salonId") UUID salonId,
                                      @Param("today") LocalDate today);

    // ── Owner: all ────────────────────────────────────────────────────────

    @Query("SELECT a FROM Appointment a WHERE a.salon.id = :salonId " +
            "ORDER BY a.date DESC, a.startTime DESC")
    List<Appointment> findAllBySalonId(@Param("salonId") UUID salonId);

    // ── Dashboard: today's schedule (PENDING + CONFIRMED + COMPLETED) ─────

    @Query("SELECT a FROM Appointment a WHERE a.salon.id = :salonId " +
            "AND a.date = :date AND a.status IN ('PENDING', 'CONFIRMED', 'COMPLETED') " +
            "ORDER BY a.startTime ASC")
    List<Appointment> findTodayBySalon(@Param("salonId") UUID salonId,
                                       @Param("date") LocalDate date);

    // ── Auto-flag: find CONFIRMED appointments whose slot has passed ──────

    /**
     * Returns all CONFIRMED appointments whose date is strictly before today,
     * OR whose date is today and whose end time is before {@code cutoffTime}.
     * Used by the no-show auto-flag job (grace period applied by caller).
     * JOIN FETCH user to avoid LazyInitializationException in the scheduled task.
     */
    @Query("SELECT a FROM Appointment a JOIN FETCH a.user " +
           "WHERE a.status = 'CONFIRMED' " +
           "AND (a.date < :today OR (a.date = :today AND a.endTime < :cutoffTime))")
    List<Appointment> findMissedConfirmedAppointments(
            @Param("today") LocalDate today,
            @Param("cutoffTime") LocalTime cutoffTime);

    // ── Dashboard: monthly appointments (for monthly revenue) ────────────

    @Query("SELECT a FROM Appointment a WHERE a.salon.id = :salonId " +
            "AND a.date >= :monthStart AND a.date <= :monthEnd " +
            "AND a.status = 'COMPLETED' " +
            "ORDER BY a.date DESC")
    List<Appointment> findCompletedInMonth(@Param("salonId") UUID salonId,
                                           @Param("monthStart") LocalDate monthStart,
                                           @Param("monthEnd") LocalDate monthEnd);
}
