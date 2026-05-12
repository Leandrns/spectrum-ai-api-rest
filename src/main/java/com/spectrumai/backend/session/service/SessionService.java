package com.spectrumai.backend.session.service;

import com.spectrumai.backend.session.model.AnalysisSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SessionService {

    AnalysisSession create(String name, String description);

    AnalysisSession getById(UUID id);

    Page<AnalysisSession> list(Pageable pageable);
}
