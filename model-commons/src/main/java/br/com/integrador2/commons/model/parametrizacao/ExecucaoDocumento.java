package br.com.integrador2.commons.model.parametrizacao;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Lob;
import javax.persistence.Table;

import br.com.integrador2.commons.model.EntidadeBase;

/**
 * Log de uma passagem de documento pelo motor de regras.
 *
 * A unicidade de correlationId e o que torna o processamento idempotente: se o
 * JMS reentregar a mesma mensagem, o Parametrizador reconhece que ja processou
 * e republica o resultado gravado em vez de rodar a regra de novo.
 */
@Entity
@Table(name = "execucao_documento")
public class ExecucaoDocumento extends EntidadeBase {

    private static final long serialVersionUID = 1L;

    @Column(name = "correlation_id", nullable = false, unique = true, length = 36)
    private String correlationId;

    @Column(name = "tipo_documento", nullable = false, length = 60)
    private String tipoDocumento;

    @Lob
    @Column(name = "payload_entrada", nullable = false)
    private String payloadEntrada;

    @Lob
    @Column(name = "payload_saida")
    private String payloadSaida;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusExecucao status;

    @Column(name = "mensagem_erro", length = 1000)
    private String mensagemErro;

    @Column(name = "data_execucao", nullable = false)
    private Instant dataExecucao;

    protected ExecucaoDocumento() {
    }

    public ExecucaoDocumento(String correlationId, String tipoDocumento, String payloadEntrada) {
        this.correlationId = correlationId;
        this.tipoDocumento = tipoDocumento;
        this.payloadEntrada = payloadEntrada;
        this.dataExecucao = Instant.now();
    }

    public void concluirComSucesso(String payloadSaida) {
        this.payloadSaida = payloadSaida;
        this.status = StatusExecucao.SUCESSO;
        this.mensagemErro = null;
    }

    public void concluirComErro(String mensagemErro) {
        this.status = StatusExecucao.ERRO;
        this.mensagemErro = mensagemErro;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public String getPayloadEntrada() {
        return payloadEntrada;
    }

    public String getPayloadSaida() {
        return payloadSaida;
    }

    public StatusExecucao getStatus() {
        return status;
    }

    public String getMensagemErro() {
        return mensagemErro;
    }

    public Instant getDataExecucao() {
        return dataExecucao;
    }
}
