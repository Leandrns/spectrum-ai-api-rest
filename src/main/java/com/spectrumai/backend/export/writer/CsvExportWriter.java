package com.spectrumai.backend.export.writer;

import com.spectrumai.backend.export.ExportFormat;
import com.spectrumai.backend.export.dto.VehicleSpecRow;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Escreve o CSV conforme a RFC 4180: vírgula como delimitador, CRLF como
 * terminador de linha e aspas duplas escapadas dobrando o caractere.
 *
 * <p>O arquivo começa com o BOM UTF-8. Ele é opcional na RFC e ignorado pelas
 * ferramentas de BI, mas sem ele o Excel lê o arquivo como Windows-1252 e destrói
 * todos os acentos — e os dados aqui são inteiramente em português.
 */
@Component
public class CsvExportWriter implements ExportWriter {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String DELIMITER = ",";
    private static final String LINE_END = "\r\n";
    private static final String HEADER =
            "marca,modelo,versao,ano_modelo,categoria,campo,valor,fonte";

    @Override
    public ExportFormat format() {
        return ExportFormat.CSV;
    }

    @Override
    public byte[] write(List<VehicleSpecRow> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append(LINE_END);

        for (VehicleSpecRow row : rows) {
            sb.append(escape(row.marca())).append(DELIMITER)
              .append(escape(row.modelo())).append(DELIMITER)
              .append(escape(row.versao())).append(DELIMITER)
              .append(row.anoModelo() == null ? "" : row.anoModelo().toString()).append(DELIMITER)
              .append(escape(row.categoria())).append(DELIMITER)
              .append(escape(row.campo())).append(DELIMITER)
              .append(escape(row.valor())).append(DELIMITER)
              .append(escape(row.fonte()))
              .append(LINE_END);
        }

        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(UTF8_BOM.length + body.length);
        out.writeBytes(UTF8_BOM);
        out.writeBytes(body);
        return out.toByteArray();
    }

    /**
     * Envolve em aspas apenas quando necessário. Espaços nas bordas também exigem
     * aspas: sem elas, parsers que fazem trim silenciosamente alteram o dado.
     */
    private String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean needsQuoting = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0
                || !value.equals(value.strip());
        if (!needsQuoting) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
