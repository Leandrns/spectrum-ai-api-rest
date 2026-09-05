package com.spectrumai.backend.export.bigquery.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Uma linha da tabela de fatos do BigQuery: o mesmo formato long/tidy do CSV
 * ({@link com.spectrumai.backend.export.dto.VehicleSpecRow}) mais as chaves
 * analíticas que o arquivo deliberadamente não carrega.
 *
 * <p>São dois tipos em vez de um porque servem a contratos diferentes: o CSV é
 * baixado por uma pessoa e tem oito colunas fixas por decisão de produto; a tabela
 * é consultada por uma ferramenta e precisa de identificadores para juntar dados,
 * medir cobertura e reconstruir o histórico.
 */
public record VehicleSpecFact(
        UUID tenantId,
        UUID searchId,
        UUID sessionId,
        String marca,
        String modelo,
        String versao,
        Integer anoModelo,
        String categoria,
        String campo,
        String valor,
        String fonte,
        boolean encontrado,
        Double valorNum,
        BigDecimal confianca,
        OffsetDateTime pesquisadoEm,
        OffsetDateTime ingeridoEm
) {

    /**
     * Separador entre campos no {@link #fingerprint()}. É o caractere de controle
     * <em>unit separator</em>, que não ocorre em texto vindo do modelo — com um
     * separador que pudesse aparecer no valor, dois conteúdos diferentes produziriam
     * a mesma string e o controle de mudanças passaria a errar.
     */
    private static final String FIELD_SEPARATOR = Character.toString(0x1F);

    /**
     * Representação estável da linha para efeito de comparação entre cargas.
     *
     * <p>Omite {@code ingeridoEm}, que muda a cada execução — incluí-lo faria o
     * hash de controle nunca coincidir e a mesma pesquisa seria reenviada a cada
     * chamada. Os campos derivados também ficam fora: são função de {@code valor}
     * e {@code fonte}, então nada acrescentam à comparação.
     */
    public String fingerprint() {
        StringJoiner joiner = new StringJoiner(FIELD_SEPARATOR);
        joiner.add(searchId.toString());
        joiner.add(sessionId == null ? "" : sessionId.toString());
        joiner.add(nullToEmpty(marca));
        joiner.add(nullToEmpty(modelo));
        joiner.add(nullToEmpty(versao));
        joiner.add(anoModelo == null ? "" : anoModelo.toString());
        joiner.add(nullToEmpty(categoria));
        joiner.add(nullToEmpty(campo));
        joiner.add(nullToEmpty(valor));
        joiner.add(nullToEmpty(fonte));
        joiner.add(confianca == null ? "" : confianca.toPlainString());
        joiner.add(pesquisadoEm == null ? "" : pesquisadoEm.toInstant().toString());
        return joiner.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
