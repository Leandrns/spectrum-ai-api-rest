package com.spectrumai.backend.vehicles.controller;

import com.spectrumai.backend.vehicles.dto.BrandsResponse;
import com.spectrumai.backend.vehicles.dto.ModelsResponse;
import com.spectrumai.backend.vehicles.dto.TrimsResponse;
import com.spectrumai.backend.vehicles.service.VehiclesService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Vehicles", description = "Autocomplete de marcas, modelos e vers�es")
@RestController
@RequestMapping("/v1/vehicles")
@RequiredArgsConstructor
@Validated
public class VehiclesController {

    private static final String SAFE_TEXT = "^[A-Za-z0-9\\u00C0-\\u017F\\s\\-./&()]*$";

    private final VehiclesService vehiclesService;

    @GetMapping("/brands")
    public BrandsResponse brands(
            @RequestParam(required = false)
            @Size(max = 60) @Pattern(regexp = SAFE_TEXT) String q
    ) {
        return vehiclesService.searchBrands(q);
    }

    @GetMapping("/models")
    public ModelsResponse models(
            @RequestParam @NotBlank @Size(max = 80) @Pattern(regexp = SAFE_TEXT) String brand,
            @RequestParam(required = false) @Size(max = 80) @Pattern(regexp = SAFE_TEXT) String q
    ) {
        return vehiclesService.searchModels(brand, q);
    }

    @GetMapping("/trims")
    public TrimsResponse trims(
            @RequestParam @NotBlank @Size(max = 80) @Pattern(regexp = SAFE_TEXT) String brand,
            @RequestParam @NotBlank @Size(max = 80) @Pattern(regexp = SAFE_TEXT) String model,
            @RequestParam(required = false) @Min(1990) @Max(2100) Integer year
    ) {
        return vehiclesService.searchTrims(brand, model, year);
    }
}
