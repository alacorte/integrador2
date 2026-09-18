package br.com.integrador2.parametrizador.regras;

import java.util.Map;

/**
 * O documento como o Drools o enxerga.
 *
 * Os campos do JSON ficam num Map em vez de virarem atributos tipados porque o
 * conjunto de campos varia por tipo de documento — e o objetivo do
 * parametrizador e justamente que regra nova nao exija classe nova. Um DRL
 * acessa assim: FatoDocumento(campos["valor"] > 1000).
 */
public class FatoDocumento {

    private final String tipoDocumento;
    private final Map<String, Object> campos;

    private String decisao;
    private String observacao;

    public FatoDocumento(String tipoDocumento, Map<String, Object> campos) {
        this.tipoDocumento = tipoDocumento;
        this.campos = campos;
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public Map<String, Object> getCampos() {
        return campos;
    }

    public String getDecisao() {
        return decisao;
    }

    public void setDecisao(String decisao) {
        this.decisao = decisao;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }
}
