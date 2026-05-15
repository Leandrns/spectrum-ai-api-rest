package com.spectrumai.backend.vehicles.service;

import com.spectrumai.backend.vehicles.dto.BrandsResponse;
import com.spectrumai.backend.vehicles.dto.ModelInfo;
import com.spectrumai.backend.vehicles.dto.ModelsResponse;
import com.spectrumai.backend.vehicles.dto.TrimsResponse;
import com.spectrumai.backend.vehicles.repository.VehicleCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehiclesServiceImpl implements VehiclesService {

    private final VehicleCatalogRepository repository;

    @Override
    public BrandsResponse searchBrands(String query) {
        return new BrandsResponse(repository.findDistinctBrands(normalize(query)));
    }

    @Override
    public ModelsResponse searchModels(String brand, String query) {
        List<Object[]> rows = repository.findModelsWithYearRange(brand, normalize(query));
        List<ModelInfo> models = rows.stream().map(r -> {
            String name = (String) r[0];
            Short yf = (Short) r[1];
            Short yt = (Short) r[2];
            List<Integer> years = IntStream.rangeClosed(yf, yt).boxed().toList();
            return new ModelInfo(name, years);
        }).toList();
        return new ModelsResponse(models);
    }

    @Override
    public TrimsResponse searchTrims(String brand, String model, Integer year) {
        short y = year == null ? (short) 0 : year.shortValue();
        return new TrimsResponse(repository.findDistinctTrims(brand, model, y));
    }

    private static String normalize(String q) {
        return q == null ? "" : q.trim();
    }
}
