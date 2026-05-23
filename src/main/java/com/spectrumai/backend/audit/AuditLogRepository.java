package com.spectrumai.backend.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :before")
    int deleteOlderThan(@Param("before") OffsetDateTime before);
}
