package br.com.integrador2.commons.model.orquestracao;

/**
 * Ciclo de vida de uma consolidacao. E o que separa "decidiu consolidar" de
 * "publicou a resposta": o handler de mensagem so leva ate PRONTA, e apenas o
 * job agendado publica e marca CONSOLIDADA. Se a publicacao falhar, a linha
 * continua PRONTA e a proxima passada do job tenta de novo — em vez de a
 * mensagem sumir em silencio.
 */
public enum StatusConsolidacao {
    PENDENTE,
    PRONTA,
    CONSOLIDADA
}
