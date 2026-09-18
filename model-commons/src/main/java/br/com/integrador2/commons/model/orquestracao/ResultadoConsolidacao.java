package br.com.integrador2.commons.model.orquestracao;

public enum ResultadoConsolidacao {
    /** Todas as partes esperadas responderam dentro do prazo. */
    COMPLETO,
    /** O prazo estourou e a consolidacao seguiu com as partes disponiveis. */
    PARCIAL_POR_TIMEOUT
}
