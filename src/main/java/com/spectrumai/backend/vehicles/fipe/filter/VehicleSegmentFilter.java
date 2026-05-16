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
 *
 * <p>O método {@link #split(String)} divide o nome bruto da FIPE em
 * {@code model} (nome base) e {@code trim} (versão/motorização), alimentando
 * diretamente as colunas da tabela {@code vehicles_catalog}.
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

    // -------------------------------------------------------------------------
    // API pública
    // -------------------------------------------------------------------------

    /**
     * Retorna {@code true} se o modelo pertence a um segmento aceito
     * (Picape ou SUV).
     */
    public boolean isIncluded(String modelName) {
        return findMatchingPrefix(modelName) != null;
    }

    /**
     * Divide o nome bruto da FIPE em {@link ModelTrimSplit#model()} (nome base)
     * e {@link ModelTrimSplit#trim()} (versão/motorização).
     *
     * <p>Exemplos:
     * <pre>
     *   "MAVERICK LARIAT HYBRID 2.5 FWD AUT." → model="MAVERICK", trim="LARIAT HYBRID 2.5 FWD AUT."
     *   "Corolla Cross XR 2.0 16V Flex Aut."  → model="Corolla Cross", trim="XR 2.0 16V Flex Aut."
     *   "HILUX SW4 3.0 TD 4X4 AT"             → model="HILUX SW4", trim="3.0 TD 4X4 AT"
     * </pre>
     *
     * <p>Se nenhum prefixo casar (modo sem filtro de segmento), o nome completo
     * fica em {@code model} e {@code trim} é {@code null}.
     */
    public ModelTrimSplit split(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return new ModelTrimSplit(modelName, null);
        }
        String original = modelName.strip();
        String matched = findMatchingPrefix(original);
        if (matched == null) {
            return new ModelTrimSplit(original, null);
        }
        // toUpperCase() não altera o comprimento para os caracteres usados em
        // nomes de veículos (ASCII/Latin-1), então podemos usar matched.length()
        // diretamente como índice no original.
        String model = original.substring(0, matched.length()).strip();
        String trim  = original.substring(matched.length()).strip();
        return new ModelTrimSplit(model, trim.isEmpty() ? null : trim);
    }

    // -------------------------------------------------------------------------
    // Interno
    // -------------------------------------------------------------------------

    private String findMatchingPrefix(String modelName) {
        if (modelName == null || modelName.isBlank()) return null;
        String upper = modelName.toUpperCase().strip();
        for (String prefix : orderedPrefixes) {
            if (upper.startsWith(prefix)) {
                // Verifica limite de palavra: evita "X10" fazer match em "X1"
                if (upper.length() == prefix.length()
                        || !Character.isLetterOrDigit(upper.charAt(prefix.length()))) {
                    return prefix;
                }
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Resultado do split
    // -------------------------------------------------------------------------

    /**
     * Resultado da divisão de um nome FIPE em nome base e versão.
     *
     * @param model nome base do veículo (ex.: "Maverick", "Corolla Cross")
     * @param trim  versão/motorização (ex.: "Lariat Hybrid 2.5 FWD Aut."),
     *              ou {@code null} quando não há informação de versão
     */
    public record ModelTrimSplit(String model, String trim) {}
}
