package com.spectrumai.backend.export.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrumai.backend.export.dto.VehicleSpecRow;
import com.spectrumai.backend.search.model.Search;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SpecsFlattenerTest {

    private SpecsFlattener flattener;

    @BeforeEach
    void setUp() {
        flattener = new SpecsFlattener(new ObjectMapper());
    }

    private Search searchWithSpecs(String specs) {
        return Search.builder()
                .id(UUID.randomUUID())
                .brand("Toyota")
                .model("Corolla")
                .trim("Altis Hybrid")
                .year((short) 2024)
                .specs(specs)
                .build();
    }

    @Test
    @DisplayName("achata categorias e campos preservando a ordem do JSON")
    void flattensCategoriesAndFields() {
        List<VehicleSpecRow> rows = flattener.flatten(searchWithSpecs("""
                {
                  "Motor e Transmissão": {
                    "Potência": {"value": "122 cv", "source": "OFFICIAL"},
                    "Torque": {"value": "14,5 kgfm", "source": "REVIEW"}
                  },
                  "Rodas": {
                    "Aro (polegadas)": {"value": "17", "source": "OFFICIAL"}
                  }
                }"""));

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).isEqualTo(new VehicleSpecRow(
                "Toyota", "Corolla", "Altis Hybrid", 2024,
                "Motor e Transmissão", "Potência", "122 cv", "OFFICIAL"));
        assertThat(rows).extracting(VehicleSpecRow::campo)
                .containsExactly("Potência", "Torque", "Aro (polegadas)");
    }

    @Test
    @DisplayName("ignora a chave 'sources', que não é uma categoria de ficha técnica")
    void skipsSourcesKey() {
        List<VehicleSpecRow> rows = flattener.flatten(searchWithSpecs("""
                {
                  "Rodas": {
                    "Rodas de Liga Leve": {"value": "Sim", "source": "OFFICIAL"}
                  },
                  "sources": {
                    "1": {"value": "https://toyota.com.br", "source": "OFFICIAL"}
                  }
                }"""));

        assertThat(rows).hasSize(1);
        assertThat(rows).extracting(VehicleSpecRow::categoria).containsExactly("Rodas");
    }

    @Test
    @DisplayName("mantém os campos não encontrados — o BI usa isso para medir cobertura")
    void keepsNotFoundFields() {
        List<VehicleSpecRow> rows = flattener.flatten(searchWithSpecs("""
                {"Rodas": {"Pneus Run-Flat": {"value": "Dado não encontrado", "source": "ESTIMATED"}}}"""));

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.valor()).isEqualTo("Dado não encontrado");
            assertThat(row.fonte()).isEqualTo("ESTIMATED");
        });
    }

    @Test
    @DisplayName("aceita campo fora do schema {value, source} sem quebrar a exportação")
    void toleratesFieldOutsideSchema() {
        List<VehicleSpecRow> rows = flattener.flatten(searchWithSpecs("""
                {
                  "Rodas": {
                    "Aro (polegadas)": 17,
                    "Pneus ATR (50/50)": "Sim",
                    "Estepe Temporário": null,
                    "Lista": ["a", "b"]
                  }
                }"""));

        assertThat(rows).extracting(VehicleSpecRow::valor)
                .containsExactly("17", "Sim", "", "[\"a\",\"b\"]");
        // Sem o objeto {value, source} não há como inferir a origem do dado.
        assertThat(rows).extracting(VehicleSpecRow::fonte).containsOnly("");
    }

    @Test
    @DisplayName("devolve lista vazia para pesquisa sem specs (ex.: FAILED)")
    void emptyForMissingSpecs() {
        assertThat(flattener.flatten(searchWithSpecs(null))).isEmpty();
        assertThat(flattener.flatten(searchWithSpecs("   "))).isEmpty();
    }

    @Test
    @DisplayName("devolve lista vazia — em vez de estourar — quando o JSON é inválido")
    void emptyForInvalidJson() {
        assertThat(flattener.flatten(searchWithSpecs("{isso não é json"))).isEmpty();
        assertThat(flattener.flatten(searchWithSpecs("\"uma string solta\""))).isEmpty();
    }

    @Test
    @DisplayName("ignora categoria que não seja um objeto de campos")
    void skipsNonObjectCategory() {
        List<VehicleSpecRow> rows = flattener.flatten(searchWithSpecs("""
                {
                  "Rodas": "categoria inteira veio como texto",
                  "Bancos": {"Banco Traseiro Aquecido": {"value": "Não", "source": "OFFICIAL"}}
                }"""));

        assertThat(rows).extracting(VehicleSpecRow::categoria).containsExactly("Bancos");
    }

    @Test
    @DisplayName("veículo sem versão ou ano não gera célula nula")
    void handlesNullVehicleFields() {
        Search search = Search.builder()
                .id(UUID.randomUUID())
                .brand("Fiat")
                .model("Toro")
                .trim(null)
                .year(null)
                .specs("{\"Rodas\": {\"Aro (polegadas)\": {\"value\": \"18\", \"source\": \"OFFICIAL\"}}}")
                .build();

        assertThat(flattener.flatten(search)).singleElement().satisfies(row -> {
            assertThat(row.versao()).isEmpty();
            assertThat(row.anoModelo()).isNull();
        });
    }
}
