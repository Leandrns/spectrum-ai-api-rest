package com.spectrumai.backend.vehicles.repository;

import com.spectrumai.backend.vehicles.model.VehicleCatalogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VehicleCatalogRepository extends JpaRepository<VehicleCatalogEntry, UUID> {

    @Query("""
            SELECT DISTINCT v.brand
              FROM VehicleCatalogEntry v
             WHERE LOWER(v.brand) LIKE LOWER(CONCAT('%', :q, '%'))
             ORDER BY v.brand
            """)
    List<String> findDistinctBrands(@Param("q") String q);

    /** Retorna {modelName, minYearFrom, maxYearTo}. */
    @Query("""
            SELECT v.model, MIN(v.yearFrom), MAX(v.yearTo)
              FROM VehicleCatalogEntry v
             WHERE LOWER(v.brand) = LOWER(:brand)
               AND LOWER(v.model) LIKE LOWER(CONCAT('%', :q, '%'))
             GROUP BY v.model
             ORDER BY v.model
            """)
    List<Object[]> findModelsWithYearRange(@Param("brand") String brand, @Param("q") String q);

    @Query("""
            SELECT DISTINCT v.trim
              FROM VehicleCatalogEntry v
             WHERE LOWER(v.brand) = LOWER(:brand)
               AND LOWER(v.model) = LOWER(:model)
               AND v.trim IS NOT NULL
               AND (:year = 0 OR (v.yearFrom <= :year AND v.yearTo >= :year))
             ORDER BY v.trim
            """)
    List<String> findDistinctTrims(
            @Param("brand") String brand,
            @Param("model") String model,
            @Param("year") short year);
}
