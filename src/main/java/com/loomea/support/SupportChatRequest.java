package com.loomea.support;

import java.util.List;

public record SupportChatRequest(String language, List<Message> messages) {

    /** @param role "user" or "assistant" */
    public record Message(String role, String text) {}
}
