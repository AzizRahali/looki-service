package com.looki.owner.insights;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class OwnerInsightsDto {

    private InsightsMetrics metrics;

    /** True when {@link #summary} or {@link #suggestions} were written by AI. */
    private boolean aiUsed;
    private String summary;
    private List<String> suggestions;
}
