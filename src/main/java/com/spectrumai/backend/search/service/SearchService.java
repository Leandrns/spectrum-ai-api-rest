package com.spectrumai.backend.search.service;

import com.spectrumai.backend.search.dto.SearchEnqueuedResponse;
import com.spectrumai.backend.search.dto.SearchExportResponse;
import com.spectrumai.backend.search.dto.SearchRequest;
import com.spectrumai.backend.search.dto.SearchResultResponse;
import com.spectrumai.backend.search.dto.SearchSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SearchService {

    SearchEnqueuedResponse enqueue(SearchRequest request);

    SearchResultResponse getResult(UUID searchId);

    Page<SearchSummary> history(UUID sessionId, Pageable pageable);

    SearchExportResponse export(UUID searchId);
}
