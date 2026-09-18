package br.com.integrador2.commons.model;

/**
 * Cada processador especializado que o Orquestrador aciona. Novos processadores
 * entram aqui e em ConsolidacaoPendente.partesEsperadas — a mecanica de
 * correlacao e timeout nao muda.
 */
public enum OrigemProcessador {
    PARAMETRIZADOR,
    ML
}
