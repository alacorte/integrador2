package br.com.integrador2.commons.mensageria;

/**
 * Nomes das filas do barramento interno. Compartilhados porque um erro de
 * digitacao aqui nao quebra o build de ninguem — a mensagem simplesmente vai
 * parar numa fila que ninguem consome.
 */
public final class Filas {

    /** Integrador -> Orquestrador. */
    public static final String DOCUMENTOS_RECEBIDOS = "documentos-recebidos";

    /** Orquestrador -> Parametrizador. */
    public static final String DOCUMENTOS_PARA_PARAMETRIZADOR = "documentos-para-parametrizador";

    /** Processadores -> Orquestrador. */
    public static final String RESPOSTAS_PARCIAIS = "respostas-parciais";

    /** Orquestrador -> Integrador. */
    public static final String RESPOSTAS_FINAIS = "respostas-finais";

    private Filas() {
    }
}
