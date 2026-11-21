package com.looki.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.looki.common.ai.GroundingCheck;
import com.looki.common.ai.ModalClient;
import com.looki.common.exception.BadRequestException;
import com.looki.owner.insights.OwnerInsightsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI support assistant that answers only from the help articles.
 * Not transactional: the AI call can be slow.
 */
@Service
public class SupportService {

    static final int MAX_MESSAGES = 8;
    static final int MAX_MESSAGE_LENGTH = 500;
    static final int MAX_ANSWER_LENGTH = 1200;
    static final int USER_LIMIT_PER_MINUTE = 6;
    static final int USER_LIMIT_PER_DAY = 60;

    private static final Set<String> ROLES = Set.of("user", "assistant");
    private static final long MINUTE = 60_000L;
    private static final long DAY = 24 * 60 * MINUTE;

    private final HelpArticles helpArticles;
    private final ModalClient modalClient;
    private final String url;
    private final Map<UUID, Deque<Long>> requestTimes = new ConcurrentHashMap<>();

    public SupportService(HelpArticles helpArticles, ModalClient modalClient,
                          @Value("${app.support.modal-url:}") String url) {
        this.helpArticles = helpArticles;
        this.modalClient = modalClient;
        this.url = url;
    }

    public SupportChatResponse chat(UUID userId, boolean owner, SupportChatRequest request) {
        List<SupportChatRequest.Message> messages = validate(request);
        if (!modalClient.isEnabled(url)) {
            return SupportChatResponse.unanswered(false);
        }
        if (!tryAcquire(userId, System.currentTimeMillis())) {
            return SupportChatResponse.limited();
        }

        List<HelpArticle> articles = helpArticles.forRole(owner);
        String language = OwnerInsightsService.normalizeLanguage(request.language());
        Map<String, Object> payload = Map.of(
                "language", language,
                "terms", helpArticles.uiTerms(language),
                "articles", articles.stream()
                        .map(a -> Map.of("id", a.id(), "title", a.title(), "body", a.body()))
                        .toList(),
                "messages", messages.stream()
                        .map(m -> Map.of("role", m.role(), "text", shorten(m.text().strip())))
                        .toList());

        return modalClient.post(url, payload, ModalReply.class)
                .map(reply -> check(reply, articles, messages.get(messages.size() - 1).text()))
                .orElse(SupportChatResponse.unanswered(false));
    }

    /** Returns the most recent messages to send, or throws when the request is invalid. */
    static List<SupportChatRequest.Message> validate(SupportChatRequest request) {
        if (request == null || request.messages() == null || request.messages().isEmpty()) {
            throw new BadRequestException("At least one message is required");
        }
        for (SupportChatRequest.Message m : request.messages()) {
            if (m == null || m.role() == null || !ROLES.contains(m.role())
                    || m.text() == null || m.text().isBlank()) {
                throw new BadRequestException("Each message needs a role (user or assistant) and text");
            }
            int limit = "user".equals(m.role()) ? MAX_MESSAGE_LENGTH : MAX_ANSWER_LENGTH;
            if (m.text().length() > limit) {
                throw new BadRequestException("Messages must be at most " + limit + " characters");
            }
        }
        List<SupportChatRequest.Message> all = request.messages();
        if (!"user".equals(all.get(all.size() - 1).role())) {
            throw new BadRequestException("The last message must be from the user");
        }
        return all.subList(Math.max(0, all.size() - MAX_MESSAGES), all.size());
    }

    /** Earlier answers can be longer than the model accepts per message; keep their start. */
    private static String shorten(String text) {
        return text.length() > MAX_MESSAGE_LENGTH ? text.substring(0, MAX_MESSAGE_LENGTH) : text;
    }

    /** Accepts the AI reply only when it cites real articles and invents no numbers, emails or links. */
    static SupportChatResponse check(ModalReply reply, List<HelpArticle> articles, String question) {
        if (reply == null || !reply.parsed() || !reply.answered()
                || reply.sources() == null || reply.sources().isEmpty()) {
            return SupportChatResponse.unanswered(true);
        }
        String answer = reply.answer() == null ? "" : reply.answer().strip();
        if (answer.isEmpty() || answer.length() > MAX_ANSWER_LENGTH) {
            return SupportChatResponse.unanswered(true);
        }

        Map<String, HelpArticle> byId = new HashMap<>();
        articles.forEach(a -> byId.put(a.id(), a));
        List<String> sources = reply.sources().stream().distinct().toList();
        List<String> groundTruth = new ArrayList<>();
        for (String id : sources) {
            HelpArticle article = byId.get(id);
            if (article == null) {
                return SupportChatResponse.unanswered(true);
            }
            groundTruth.add(article.body());
        }
        groundTruth.add(question);

        if (!GroundingCheck.isGrounded(answer, groundTruth)) {
            return SupportChatResponse.unanswered(true);
        }
        return new SupportChatResponse(true, true, answer, sources, false);
    }

    boolean tryAcquire(UUID userId, long now) {
        Deque<Long> times = requestTimes.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() >= DAY) {
                times.pollFirst();
            }
            long lastMinute = times.stream().filter(t -> now - t < MINUTE).count();
            if (times.size() >= USER_LIMIT_PER_DAY || lastMinute >= USER_LIMIT_PER_MINUTE) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ModalReply(String answer, List<String> sources, boolean answered, boolean parsed) {}
}
