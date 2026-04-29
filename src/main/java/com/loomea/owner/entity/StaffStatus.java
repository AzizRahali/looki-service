package com.loomea.owner.entity;

/**
 * Lifecycle status of a staff member.
 * - ACTIVE    : normal access
 * - SUSPENDED : temporarily blocked — existing bookings preserved, new ones blocked
 * - REMOVED   : soft-deleted — no access, existing bookings preserved
 */
public enum StaffStatus {
    ACTIVE,
    SUSPENDED,
    REMOVED
}
