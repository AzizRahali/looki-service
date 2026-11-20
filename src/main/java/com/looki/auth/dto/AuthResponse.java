package com.looki.auth.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AuthResponse {
    private String accessToken;
    private UserDto user;

    @Getter
    @Builder
    public static class UserDto {
        private String id;
        private String fullName;
        private String email;
        private String phone;
        private String avatarUrl;
        private String location;
        private String role;
        private List<String> savedSalonIds;
        /** Current reliability score [0–120]. */
        private int reliabilityScore;
        /** Tier name: TRUSTED, NORMAL, RISKY, or RESTRICTED. */
        private String reliabilityTier;
        /**
         * ISO-8601 timestamp of scheduled hard-deletion.
         * Non-null means the account is in a 30-day grace period.
         */
        private String scheduledDeletionAt;
    }

    /** Returned by /register before phone verification */
    @Getter
    @Builder
    public static class PendingResponse {
        private String email;
        private String role;
        private String message;
    }
}
