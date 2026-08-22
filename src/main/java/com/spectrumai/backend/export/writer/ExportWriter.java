package com.spectrumai.backend.export.writer;

import com.spectrumai.backend.export.ExportFormat;
import com.spectrumai.backend.export.dto.VehicleSpecRow;

import java.util.List;

/**
 * Serializa as linhas achatadas de ficha técnica no formato de arquivo final.
 *
 * <p>Ponto de extensão do PDF: basta uma implementação nova declarando
 * {@link ExportFormat#PDF} em {@link #format()} — o {@link ExportWriterResolver}
 * a encontra sozinho, sem tocar no serviço de exportação.
 */
public interface ExportWriter {

    ExportFormat format();

    byte[] write(List<VehicleSpecRow> rows);
}
