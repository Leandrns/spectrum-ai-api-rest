package com.spectrumai.backend.insights.controller;

import com.spectrumai.backend.insights.service.InsightsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Insights", description = "Assistente de insights competitivos")
@RestController
@RequestMapping("/v1/insights")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
public class InsightsController {

    private final InsightsService insightsService;
}
