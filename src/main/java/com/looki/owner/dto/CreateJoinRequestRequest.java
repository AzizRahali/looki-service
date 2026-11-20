package com.looki.owner.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateJoinRequestRequest {
    /** Optional message to the salon owner (max 500 chars). */
    private String message;
}
