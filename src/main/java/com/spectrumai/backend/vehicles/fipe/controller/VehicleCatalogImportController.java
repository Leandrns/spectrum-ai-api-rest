package com.spectrumai.backend.vehicles.fipe.controller;

import com.spectrumai.backend.vehicles.fipe.dto.ImportStatusResponse;
import com.spectrumai.backend.vehicles.fipe.dto.VehicleImportReport;
import com.spectrumai.backend.vehicles.fipe.service.VehicleCatalogImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Vehicles", description = "Operações administrativas do catálogo de veículos")
@RestController
@RequestMapping("/v1/admin/vehicles")
@RequiredArgsConstructor
public class VehicleCatalogImportController {

    private final VehicleCatalogImportService importService;

    @Operation(
            summary = "Inicia importação do catálogo a partir da FIPE",
            description = "Limpa vehicles_catalog e repopula com dados da FIPE. "
                    + "Requer FIPE_API_TOKEN configurado. Retorna 409 se já houver import em andamento."
    )
    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public VehicleImportReport importCatalog() {
        return importService.importFromFipe();
    }

    @Operation(
            summary = "Cancela o import em andamento",
            description = "Sinaliza parada ao import ativo. O loop aborta na próxima marca e faz "
                    + "rollback completo — os dados anteriores são preservados. Retorna 409 se não "
                    + "houver import em andamento."
    )
    @DeleteMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> cancelImport() {
        importService.cancel();
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Retorna o status atual do import")
    @GetMapping("/import/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ImportStatusResponse importStatus() {
        return new ImportStatusResponse(importService.isRunning());
    }
}
