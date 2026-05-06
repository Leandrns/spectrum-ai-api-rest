package com.spectrumai.backend.insights.dto;

import java.util.List;
import java.util.UUID;

public record InsightsReport(
        UUID sessionId,
        Swot swot,
        List<String> differentiators,
        List<String> vulnerabilities,
        List<String> sellingArguments
) {
    public record Swot(
            List<String> strengths,
            List<String> weaknesses,
            List<String> opportunities,
            List<String> threats
    ) {}
}
