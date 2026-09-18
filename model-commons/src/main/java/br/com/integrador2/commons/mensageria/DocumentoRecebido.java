package br.com.integrador2.commons.mensageria;

import java.io.Serializable;
import java.time.Instant;

/**
 * Documento como ele entra no sistema. O correlationId e gerado pelo Integrador
 * no momento em que recebe do IBM MQ e acompanha o documento por todos os
 * processos — tambem no header JMSCorrelationID, para permitir rastrear a
 * mensagem pelo console do broker.
 */
public class DocumentoRecebido implements Serializable {

    private static final long serialVersionUID = 1L;

    private String correlationId;
    private String tipoDocumento;
    private String payloadJson;
    private Instant dataRecebimento;

    public DocumentoRecebido() {
    }

    public DocumentoRecebido(String correlationId, String tipoDocumento, String payloadJson,
            Instant dataRecebimento) {
        this.correlationId = correlationId;
        this.tipoDocumento = tipoDocumento;
        this.payloadJson = payloadJson;
        this.dataRecebimento = dataRecebimento;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public void setTipoDocumento(String tipoDocumento) {
        this.tipoDocumento = tipoDocumento;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public Instant getDataRecebimento() {
        return dataRecebimento;
    }

    public void setDataRecebimento(Instant dataRecebimento) {
        this.dataRecebimento = dataRecebimento;
    }
}
