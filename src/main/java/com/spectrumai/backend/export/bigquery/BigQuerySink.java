package com.spectrumai.backend.export.bigquery;

import com.spectrumai.backend.export.bigquery.dto.VehicleSpecFact;

import java.util.List;

/**
 * Destino das fichas técnicas no data warehouse. Espelha o papel de
 * {@link com.spectrumai.backend.export.storage.ExportStorage} na exportação em
 * arquivo: isola a API do provedor do serviço que decide o que enviar.
 */
public interface BigQuerySink {

    /**
     * Garante que a tabela de fatos e a view de leitura existam, com o
     * particionamento e o clustering definidos em {@link BigQuerySchema}.
     *
     * <p>Idempotente e barata a partir da segunda chamada: o resultado é memorizado
     * por instância, então o custo de API acontece uma vez por processo.
     */
    void ensureSchema();

    /**
     * Insere um lote em uma única requisição.
     *
     * <p>O dimensionamento do lote é do chamador ({@code spectrum.bigquery.batch-size}):
     * a API rejeita requisições acima de 10 MB, e é o serviço que conhece o volume
     * que está processando.
     */
    void insertAll(List<VehicleSpecFact> facts);
}
