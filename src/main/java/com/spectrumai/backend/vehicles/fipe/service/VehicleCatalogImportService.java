package com.spectrumai.backend.vehicles.fipe.service;

import com.spectrumai.backend.vehicles.fipe.dto.VehicleImportReport;

public interface VehicleCatalogImportService {

    VehicleImportReport importFromFipe();

    void cancel();

    boolean isRunning();
}
