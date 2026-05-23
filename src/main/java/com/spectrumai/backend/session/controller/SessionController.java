package com.spectrumai.backend.session.controller;

import com.spectrumai.backend.common.dto.PageResponse;
import com.spectrumai.backend.session.dto.CreateSessionRequest;
import com.spectrumai.backend.session.dto.SessionResponse;
import com.spectrumai.backend.session.service.SessionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Sessions", description = "Sessões de análise competitiva")
@RestController
@RequestMapping("/v1/sessions")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class SessionController {

    private final SessionService sessionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public SessionResponse create(@Valid @RequestBody CreateSessionRequest request) {
        return SessionResponse.from(sessionService.create(request.name(), request.description()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST','VIEWER')")
    public SessionResponse getById(@PathVariable UUID id) {
        return SessionResponse.from(sessionService.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST','VIEWER')")
    public PageResponse<SessionResponse> list(Pageable pageable) {
        return PageResponse.of(sessionService.list(pageable).map(SessionResponse::from));
    }
}
