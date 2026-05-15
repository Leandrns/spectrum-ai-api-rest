package com.spectrumai.backend.vehicles.fipe.filter;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Filtra modelos da FIPE retendo apenas Picapes e SUVs do mercado brasileiro.
 *
 * <p>A API FIPE não expõe categoria — o filtro usa prefixos do nome do modelo
 * (case-insensitive). Prefixos multi-palavra (ex.: "COROLLA CROSS") são
 * verificados antes dos seus prefixos mais curtos para evitar falsos positivos.
 */
@Component
public class VehicleSegmentFilter {

    // -------------------------------------------------------------------------
    // Picapes
    // -------------------------------------------------------------------------
    private static final Set<String> PICKUP_PREFIXES = Set.of(
            // Toyota
            "HILUX",
            // Ford
            "RANGER", "MAVERICK", "F-250", "F-350",
            // Chevrolet
            "S10", "MONTANA",
            // Nissan
            "FRONTIER",
            // Volkswagen
            "AMAROK", "SAVEIRO",
            // Mitsubishi
            "L200", "TRITON", "CANNON",
            // Fiat
            "TORO", "STRADA",
            // Renault
            "OROCH", "ALASKAN"
    );

    // -------------------------------------------------------------------------
    // SUVs — prefixos multi-palavra declarados para ter prioridade na ordenação
    // -------------------------------------------------------------------------
    private static final Set<String> SUV_PREFIXES = Set.of(
            // Toyota
            "COROLLA CROSS", "YARIS CROSS", "LAND CRUISER PRADO", "LAND CRUISER",
            "HILUX SW4", "RAV4", "C-HR", "RUSH", "FORTUNER",
            // Honda
            "HR-V", "CR-V", "WR-V", "ZR-V",
            // Hyundai
            "SANTA FE", "CRETA", "TUCSON", "PALISADE",
            // Chevrolet
            "TRACKER", "TRAILBLAZER", "BLAZER", "EQUINOX",
            // Volkswagen
            "T-CROSS", "TIGUAN", "TAOS", "TOUAREG", "NIVUS",
            // Jeep
            "COMPASS", "RENEGADE", "COMMANDER", "WRANGLER", "GLADIATOR",
            // Ford
            "BRONCO SPORT", "BRONCO", "TERRITORY", "EDGE", "ECOSPORT",
            // Mitsubishi
            "ECLIPSE CROSS", "PAJERO FULL", "PAJERO SPORT", "PAJERO", "OUTLANDER", "ASX",
            // Renault
            "DUSTER", "CAPTUR", "KOLEOS", "KARDIAN",
            // Nissan
            "X-TRAIL", "KICKS", "PATHFINDER", "MURANO", "XTERRA",
            // Fiat
            "PULSE", "FASTBACK", "FREEMONT", "500X",
            // BMW
            "X1", "X2", "X3", "X4", "X5", "X6", "X7",
            // Mercedes-Benz
            "GLA", "GLB", "GLC", "GLE", "GLS",
            // Audi
            "Q3", "Q5", "Q7", "Q8", "E-TRON",
            // Land Rover
            "RANGE ROVER", "DISCOVERY SPORT", "DISCOVERY", "DEFENDER",
            // Porsche
            "CAYENNE", "MACAN",
            // Škoda
            "KAROQ", "KODIAQ",
            // Kia
            "SPORTAGE", "SORENTO", "TELLURIDE", "SELTOS",
            // Alfa Romeo
            "STELVIO", "TONALE",
            // Subaru
            "FORESTER", "OUTBACK", "CROSSTREK",
            // Volvo
            "XC40", "XC60", "XC90"
    );

    /**
     * Prefixos ordenados por comprimento decrescente: "COROLLA CROSS" é testado
     * antes de "COROLLA", "PAJERO SPORT" antes de "PAJERO", etc.
     */
    private final List<String> orderedPrefixes;

    public VehicleSegmentFilter() {
        this.orderedPrefixes = Stream
                .concat(PICKUP_PREFIXES.stream(), SUV_PREFIXES.stream())
                .map(String::toUpperCase)
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    /**
     * Retorna {@code true} se o modelo deve ser incluído no catálogo
     * (é uma Picape ou SUV reconhecido).
     *
     * @param modelName nome bruto retornado pela FIPE (ex.: "HILUX 4X4 SR 2.7 16V")
     */
    public boolean isIncluded(String modelName) {
        if (modelName == null || modelName.isBlank()) return false;
        String upper = modelName.toUpperCase().strip();
        for (String prefix : orderedPrefixes) {
            if (upper.startsWith(prefix)) {
                // Verifica limite de palavra: evita "X10" fazer match em "X1"
                if (upper.length() == prefix.length()
                        || !Character.isLetterOrDigit(upper.charAt(prefix.length()))) {
                    return true;
                }
            }
        }
        return false;
    }
}
