package com.spectrumai.backend.session.service;

import com.spectrumai.backend.session.model.AnalysisSession;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SessionServiceImpl implements SessionService {

    @Override
    public AnalysisSession create(String name, String description) {
        throw new UnsupportedOperationException("SessionService.create ainda não implementado");
    }

    @Override
    public AnalysisSession getById(UUID id) {
        throw new UnsupportedOperationException("SessionService.getById ainda não implementado");
    }
}
