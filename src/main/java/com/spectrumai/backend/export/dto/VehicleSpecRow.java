package com.spectrumai.backend.export.dto;

/**
 * Uma linha do arquivo exportado, no formato long/tidy: cada campo da ficha
 * técnica vira uma linha independente, identificada pelo veículo a que pertence.
 *
 * <p>O layout é intencionalmente estável — as specs têm 14 categorias canônicas e
 * mais de 250 campos possíveis, e novos campos entram a cada revisão do prompt.
 * Em formato wide isso viraria uma planilha esparsa cujas colunas mudam sozinhas,
 * quebrando dashboards já montados. Aqui o schema nunca muda: a ferramenta de BI
 * faz o pivot de {@code categoria}/{@code campo} por conta própria.
 */
public record VehicleSpecRow(
        String marca,
        String modelo,
        String versao,
        Integer anoModelo,
        String categoria,
        String campo,
        String valor,
        String fonte
) {}
