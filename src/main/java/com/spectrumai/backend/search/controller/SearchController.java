package com.spectrumai.backend.search.controller;

import com.spectrumai.backend.common.dto.PageResponse;
import com.spectrumai.backend.search.dto.SearchEnqueuedResponse;
import com.spectrumai.backend.search.dto.SearchExportResponse;
import com.spectrumai.backend.search.dto.SearchProgressEvent;
import com.spectrumai.backend.search.dto.SearchRequest;
import com.spectrumai.backend.search.dto.SearchResultResponse;
import com.spectrumai.backend.search.dto.SearchSummary;
import com.spectrumai.backend.search.service.SearchService;
import com.spectrumai.backend.search.service.SearchStreamService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Tag(name = "Searches", description = "Pesquisas competitivas de ve�culos")
@RestController
@RequestMapping("/v1/searches")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class SearchController {

    private final SearchService searchService;
    private final SearchStreamService streamService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public SearchEnqueuedResponse enqueue(@Valid @RequestBody SearchRequest request) {
        return searchService.enqueue(request);
    }

    @GetMapping("/{id}/result")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST','VIEWER')")
    public SearchResultResponse result(@PathVariable UUID id) {
        return searchService.getResult(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST','VIEWER')")
    public PageResponse<SearchSummary> history(
            @RequestParam(required = false) UUID sessionId,
            Pageable pageable
    ) {
        return PageResponse.of(searchService.history(sessionId, pageable));
    }

    @GetMapping(path = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST','VIEWER')")
    public Flux<ServerSentEvent<SearchProgressEvent>> stream(@PathVariable UUID id) {
        return streamService.subscribe(id);
    }

    @GetMapping("/{id}/export")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public SearchExportResponse export(@PathVariable UUID id) {
        return searchService.export(id);
    }
}
