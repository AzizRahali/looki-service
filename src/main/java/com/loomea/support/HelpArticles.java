package com.loomea.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads the help articles from {@code support/help-articles.md} and app UI names from {@code support/ui-terms.json}. */
@Component
public class HelpArticles {

    static final String RESOURCE = "support/help-articles.md";
    static final String TERMS_RESOURCE = "support/ui-terms.json";

    private static final Pattern HEADER = Pattern.compile("^## *([a-z0-9-]+) *\\| *(\\w+) *\\| *(.+?) *$");
    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    private final List<HelpArticle> articles;
    private final Map<String, Map<String, String>> terms;

    public HelpArticles() throws IOException {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            this.articles = parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        try (InputStream in = new ClassPathResource(TERMS_RESOURCE).getInputStream()) {
            Map<String, Object> raw = new ObjectMapper().readValue(in, new TypeReference<>() {});
            Map<String, Map<String, String>> parsed = new HashMap<>();
            raw.forEach((language, value) -> {
                if (value instanceof Map<?, ?> map) {
                    Map<String, String> byTerm = new LinkedHashMap<>();
                    map.forEach((k, v) -> byTerm.put(String.valueOf(k), String.valueOf(v)));
                    parsed.put(language, Map.copyOf(byTerm));
                }
            });
            this.terms = Map.copyOf(parsed);
        }
    }

    public List<HelpArticle> forRole(boolean owner) {
        return articles.stream().filter(a -> a.audience().includes(owner)).toList();
    }

    /** English app labels mapped to their translation in {@code language}; empty for English. */
    public Map<String, String> uiTerms(String language) {
        return terms.getOrDefault(language, Map.of());
    }

    static List<HelpArticle> parse(String markdown) {
        List<HelpArticle> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        String id = null;
        HelpArticle.Audience audience = null;
        String title = null;
        StringBuilder body = new StringBuilder();

        for (String line : COMMENT.matcher(markdown).replaceAll("").split("\\R")) {
            if (line.startsWith("## ")) {
                add(result, ids, id, audience, title, body);
                Matcher header = HEADER.matcher(line);
                if (!header.matches()) {
                    throw new IllegalStateException("Invalid help article header: " + line);
                }
                id = header.group(1);
                audience = HelpArticle.Audience.parse(header.group(2));
                title = header.group(3);
                body.setLength(0);
            } else if (id != null) {
                body.append(line).append('\n');
            }
        }
        add(result, ids, id, audience, title, body);
        if (result.isEmpty()) {
            throw new IllegalStateException("No help articles found in " + RESOURCE);
        }
        return List.copyOf(result);
    }

    private static void add(List<HelpArticle> result, Set<String> ids, String id,
                            HelpArticle.Audience audience, String title, StringBuilder body) {
        if (id == null) {
            return;
        }
        if (!ids.add(id)) {
            throw new IllegalStateException("Duplicate help article id: " + id);
        }
        String text = body.toString().strip();
        if (text.isEmpty()) {
            throw new IllegalStateException("Empty help article: " + id);
        }
        result.add(new HelpArticle(id, audience, title, text));
    }
}
