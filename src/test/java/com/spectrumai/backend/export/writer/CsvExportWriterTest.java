package com.spectrumai.backend.export.writer;

import com.spectrumai.backend.export.ExportFormat;
import com.spectrumai.backend.export.dto.VehicleSpecRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvExportWriterTest {

    private static final String HEADER = "marca,modelo,versao,ano_modelo,categoria,campo,valor,fonte";

    private final CsvExportWriter writer = new CsvExportWriter();

    private VehicleSpecRow row(String valor) {
        return new VehicleSpecRow("Toyota", "Corolla", "Altis", 2024, "Rodas", "Aro", valor, "OFFICIAL");
    }

    /** Texto do arquivo já sem o BOM, para facilitar as asserções de conteúdo. */
    private String text(List<VehicleSpecRow> rows) {
        byte[] bytes = writer.write(rows);
        return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
    }

    @Test
    void declaresCsvFormat() {
        assertThat(writer.format()).isEqualTo(ExportFormat.CSV);
    }

    @Test
    @DisplayName("começa com BOM UTF-8 — sem ele o Excel destrói os acentos")
    void startsWithUtf8Bom() {
        byte[] bytes = writer.write(List.of(row("17")));

        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
    }

    @Test
    @DisplayName("usa CRLF como terminador de linha, conforme a RFC 4180")
    void usesCrlfLineEndings() {
        String csv = text(List.of(row("17")));

        assertThat(csv).isEqualTo(HEADER + "\r\nToyota,Corolla,Altis,2024,Rodas,Aro,17,OFFICIAL\r\n");
    }

    @Test
    @DisplayName("escreve só o cabeçalho quando não há linhas")
    void writesHeaderOnlyWhenEmpty() {
        assertThat(text(List.of())).isEqualTo(HEADER + "\r\n");
    }

    @Test
    @DisplayName("envolve em aspas valores com vírgula, aspas ou quebra de linha")
    void quotesSpecialCharacters() {
        String csv = text(List.of(
                row("1,6 litros"),
                row("Aro 17\""),
                row("linha 1\nlinha 2"),
                row("  espaços nas bordas  ")
        ));

        assertThat(csv).contains(",\"1,6 litros\",OFFICIAL");
        // Aspas internas são escapadas dobrando o caractere.
        assertThat(csv).contains(",\"Aro 17\"\"\",OFFICIAL");
        assertThat(csv).contains(",\"linha 1\nlinha 2\",OFFICIAL");
        assertThat(csv).contains(",\"  espaços nas bordas  \",OFFICIAL");
    }

    @Test
    @DisplayName("não envolve em aspas valores simples")
    void doesNotQuotePlainValues() {
        assertThat(text(List.of(row("Sim")))).contains(",Sim,OFFICIAL");
    }

    @Test
    @DisplayName("ano nulo vira célula vazia, não a string 'null'")
    void nullYearBecomesEmptyCell() {
        String csv = text(List.of(
                new VehicleSpecRow("Fiat", "Toro", "", null, "Rodas", "Aro", "18", "OFFICIAL")));

        assertThat(csv).contains("Fiat,Toro,,,Rodas,Aro,18,OFFICIAL");
    }

    @Test
    @DisplayName("preserva acentos ao decodificar como UTF-8")
    void preservesAccents() {
        byte[] bytes = writer.write(List.of(
                new VehicleSpecRow("Toyota", "Corolla", "Altis", 2024,
                        "Motor e Transmissão", "Potência", "Dado não encontrado", "NOT_FOUND")));

        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertThat(csv).contains("Motor e Transmissão,Potência,Dado não encontrado,NOT_FOUND");
    }
}
