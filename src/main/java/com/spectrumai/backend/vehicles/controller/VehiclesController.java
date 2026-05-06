package com.spectrumai.backend.vehicles.controller;

import com.spectrumai.backend.vehicles.dto.BrandsResponse;
import com.spectrumai.backend.vehicles.dto.ModelsResponse;
import com.spectrumai.backend.vehicles.dto.TrimsResponse;
import com.spectrumai.backend.vehicles.service.VehiclesService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Vehicles", description = "Autocomplete de marcas, modelos e versões")
@RestController
@RequestMapping("/v1/vehicles")
@RequiredArgsConstructor
public class VehiclesController {

    private final VehiclesService vehiclesService;

    @GetMapping("/brands")
    public BrandsResponse brands(@RequestParam(required = false) String q) {
        return vehiclesService.searchBrands(q);
    }

    @GetMapping("/models")
    public ModelsResponse models(
            @RequestParam String brand,
            @RequestParam(required = false) String q
    ) {
        return vehiclesService.searchModels(brand, q);
    }

    @GetMapping("/trims")
    public TrimsResponse trims(
            @RequestParam String brand,
            @RequestParam String model,
            @RequestParam(required = false) Integer year
    ) {
        return vehiclesService.searchTrims(brand, model, year);
    }
}
