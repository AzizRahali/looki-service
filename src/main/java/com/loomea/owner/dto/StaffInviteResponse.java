package com.loomea.owner.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StaffInviteResponse {
    private String staffId;
    private String inviteToken;
    private String deepLink;
    private String expiresAt;
}
