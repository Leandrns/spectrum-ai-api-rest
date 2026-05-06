package com.spectrumai.backend.vehicles.service;

import com.spectrumai.backend.vehicles.dto.BrandsResponse;
import com.spectrumai.backend.vehicles.dto.ModelsResponse;
import com.spectrumai.backend.vehicles.dto.TrimsResponse;

public interface VehiclesService {

    BrandsResponse searchBrands(String query);

    ModelsResponse searchModels(String brand, String query);

    TrimsResponse searchTrims(String brand, String model, Integer year);
}
