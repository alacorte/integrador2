package br.com.integrador2.commons.mensageria;

import java.io.Serializable;
import java.time.Instant;

import br.com.integrador2.commons.model.OrigemProcessador;
import br.com.integrador2.commons.model.parametrizacao.StatusExecucao;

/**
 * Resultado de um processador especializado, devolvido ao Orquestrador.
 */
public class RespostaParcial implements Serializable {

    private static final long serialVersionUID = 1L;

    private String correlationId;
    private OrigemProcessador origem;
    private String payloadResultado;
    private StatusExecucao status;
    private String mensagemErro;
    private Instant dataResposta;

    public RespostaParcial() {
    }

    public RespostaParcial(String correlationId, OrigemProcessador origem, String payloadResultado,
            StatusExecucao status) {
        this.correlationId = correlationId;
        this.origem = origem;
        this.payloadResultado = payloadResultado;
        this.status = status;
        this.dataResposta = Instant.now();
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public OrigemProcessador getOrigem() {
        return origem;
    }

    public void setOrigem(OrigemProcessador origem) {
        this.origem = origem;
    }

    public String getPayloadResultado() {
        return payloadResultado;
    }

    public void setPayloadResultado(String payloadResultado) {
        this.payloadResultado = payloadResultado;
    }

    public StatusExecucao getStatus() {
        return status;
    }

    public void setStatus(StatusExecucao status) {
        this.status = status;
    }

    public String getMensagemErro() {
        return mensagemErro;
    }

    public void setMensagemErro(String mensagemErro) {
        this.mensagemErro = mensagemErro;
    }

    public Instant getDataResposta() {
        return dataResposta;
    }

    public void setDataResposta(Instant dataResposta) {
        this.dataResposta = dataResposta;
    }
}
