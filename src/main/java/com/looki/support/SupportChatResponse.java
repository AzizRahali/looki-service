package com.looki.support;

import java.util.List;

/**
 * @param aiUsed      false when the AI could not be reached
 * @param answered    true only when {@code answer} is grounded in the help articles
 * @param answer      the assistant's answer, in the requested language; null when not answered
 * @param sources     ids of the help articles the answer is based on
 * @param rateLimited true when the user sent too many messages
 */
public record SupportChatResponse(boolean aiUsed, boolean answered, String answer,
                                  List<String> sources, boolean rateLimited) {

    static SupportChatResponse unanswered(boolean aiUsed) {
        return new SupportChatResponse(aiUsed, false, null, List.of(), false);
    }

    static SupportChatResponse limited() {
        return new SupportChatResponse(false, false, null, List.of(), true);
    }
}
