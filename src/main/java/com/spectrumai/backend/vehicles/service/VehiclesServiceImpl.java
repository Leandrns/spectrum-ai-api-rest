package com.spectrumai.backend.vehicles.service;

import com.spectrumai.backend.vehicles.dto.BrandsResponse;
import com.spectrumai.backend.vehicles.dto.ModelsResponse;
import com.spectrumai.backend.vehicles.dto.TrimsResponse;
import org.springframework.stereotype.Service;

@Service
public class VehiclesServiceImpl implements VehiclesService {

    @Override
    public BrandsResponse searchBrands(String query) {
        throw new UnsupportedOperationException("VehiclesService.searchBrands ainda não implementado");
    }

    @Override
    public ModelsResponse searchModels(String brand, String query) {
        throw new UnsupportedOperationException("VehiclesService.searchModels ainda não implementado");
    }

    @Override
    public TrimsResponse searchTrims(String brand, String model, Integer year) {
        throw new UnsupportedOperationException("VehiclesService.searchTrims ainda não implementado");
    }
}
