package br.com.integrador2.commons.mensageria;

import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import br.com.integrador2.commons.model.OrigemProcessador;
import br.com.integrador2.commons.model.orquestracao.ResultadoConsolidacao;

/**
 * Resposta final que o Integrador devolve ao sistema de origem, juntando o que
 * cada processador produziu.
 */
public class RespostaConsolidada implements Serializable {

    private static final long serialVersionUID = 1L;

    private String correlationId;
    private String tipoDocumento;
    private Map<OrigemProcessador, String> partes = new LinkedHashMap<>();
    private ResultadoConsolidacao resultado;
    private Instant dataConsolidacao;

    public RespostaConsolidada() {
    }

    public RespostaConsolidada(String correlationId, String tipoDocumento,
            Map<OrigemProcessador, String> partes, ResultadoConsolidacao resultado) {
        this.correlationId = correlationId;
        this.tipoDocumento = tipoDocumento;
        this.partes = partes;
        this.resultado = resultado;
        this.dataConsolidacao = Instant.now();
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

    public Map<OrigemProcessador, String> getPartes() {
        return partes;
    }

    public void setPartes(Map<OrigemProcessador, String> partes) {
        this.partes = partes;
    }

    public ResultadoConsolidacao getResultado() {
        return resultado;
    }

    public void setResultado(ResultadoConsolidacao resultado) {
        this.resultado = resultado;
    }

    public Instant getDataConsolidacao() {
        return dataConsolidacao;
    }

    public void setDataConsolidacao(Instant dataConsolidacao) {
        this.dataConsolidacao = dataConsolidacao;
    }
}
