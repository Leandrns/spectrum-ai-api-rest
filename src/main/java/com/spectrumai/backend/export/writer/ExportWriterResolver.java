package com.spectrumai.backend.export.writer;

import com.spectrumai.backend.common.exception.BusinessException;
import com.spectrumai.backend.common.exception.ErrorCode;
import com.spectrumai.backend.export.ExportFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Localiza o {@link ExportWriter} de um formato entre os beans registrados —
 * mesmo padrão do {@code AiProviderResolver}.
 */
@Component
public class ExportWriterResolver {

    private final List<ExportWriter> writers;

    public ExportWriterResolver(List<ExportWriter> writers) {
        this.writers = writers;
    }

    public ExportWriter resolve(ExportFormat format) {
        return writers.stream()
                .filter(w -> w.format() == format)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "Exportação em " + format + " ainda não está disponível.",
                        HttpStatus.NOT_IMPLEMENTED,
                        ErrorCode.NOT_IMPLEMENTED));
    }
}
