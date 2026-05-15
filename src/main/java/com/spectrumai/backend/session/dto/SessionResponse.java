package com.spectrumai.backend.session.dto;

import com.spectrumai.backend.session.model.AnalysisSession;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        String name,
        String description,
        UUID ownerId,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static SessionResponse from(AnalysisSession session) {
        return new SessionResponse(
                session.getId(),
                session.getName(),
                session.getDescription(),
                session.getOwner().getId(),
                session.isActive(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }
}
