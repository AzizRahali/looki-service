package com.loomea.owner.entity;

/**
 * Role a staff member holds within their salon.
 * ADMIN can manage the salon's staff (invite, suspend, assign roles).
 * STAFF can only view their own schedule.
 */
public enum StaffRole {
    ADMIN,
    STAFF
}
