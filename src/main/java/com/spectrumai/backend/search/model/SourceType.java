package com.spectrumai.backend.search.model;

/**
 * Classificação da procedência de cada campo da ficha técnica, gravada em
 * {@code specs.<categoria>.<campo>.source}.
 *
 * <p>As quatro tags são mutuamente exclusivas e definidas pelo prompt
 * {@code vehicle_spec_search} (v2 em diante). Até a v1 não havia
 * {@link #NOT_FOUND}: dado ausente e dado inferido dividiam {@link #ESTIMATED},
 * o que impedia distinguir uma lacuna real de uma inferência.
 */
public enum SourceType {

    /**
     * Fonte de nível 1 — site da montadora, PBEV/INMETRO, PROCONVE/IBAMA, hub de
     * imprensa da marca ou manual do proprietário — para a versão e o ano-modelo
     * exatos pesquisados.
     */
    OFFICIAL,

    /**
     * Compilador técnico ou publicação especializada (carrosnaweb.com.br,
     * quatrorodas.abril.com.br e afins), para a versão e o ano-modelo pesquisados.
     */
    REVIEW,

    /**
     * Dado inferido de base verificável — versão irmã, ano-modelo adjacente ou
     * motorização idêntica em outro trim. O {@code value} carrega a base da
     * inferência entre parênteses.
     */
    ESTIMATED,

    /**
     * Dado realmente indisponível após a pesquisa hierárquica. O {@code value}
     * é exatamente {@code "Dado não encontrado"}.
     */
    NOT_FOUND
}
