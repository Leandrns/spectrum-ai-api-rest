package com.spectrumai.backend.search.service;

import com.spectrumai.backend.search.dto.SearchEnqueuedResponse;
import com.spectrumai.backend.search.dto.SearchExportResponse;
import com.spectrumai.backend.search.dto.SearchRequest;
import com.spectrumai.backend.search.dto.SearchResultResponse;
import com.spectrumai.backend.search.dto.SearchSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SearchServiceImpl implements SearchService {

    @Override
    public SearchEnqueuedResponse enqueue(SearchRequest request) {
        throw new UnsupportedOperationException("SearchService.enqueue ainda não implementado");
    }

    @Override
    public SearchResultResponse getResult(UUID searchId) {
        throw new UnsupportedOperationException("SearchService.getResult ainda não implementado");
    }

    @Override
    public Page<SearchSummary> history(UUID sessionId, Pageable pageable) {
        throw new UnsupportedOperationException("SearchService.history ainda não implementado");
    }

    @Override
    public SearchExportResponse export(UUID searchId) {
        throw new UnsupportedOperationException("SearchService.export ainda não implementado");
    }
}
