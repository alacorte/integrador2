package br.com.integrador2.commons.model.orquestracao;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import br.com.integrador2.commons.model.EntidadeBase;
import br.com.integrador2.commons.model.OrigemProcessador;
import br.com.integrador2.commons.model.parametrizacao.StatusExecucao;

/**
 * Uma resposta parcial ja recebida para uma consolidacao.
 *
 * A constraint unica (correlation_id, origem) faz duas coisas de uma vez:
 * da idempotencia contra reentrega do JMS, e elimina o read-modify-write que
 * um campo JSON de "partes recebidas" exigiria (perderia atualizacao assim que
 * o branch de ML trouxer concorrencia real).
 */
@Entity
@Table(name = "consolidacao_parte",
        uniqueConstraints = @UniqueConstraint(name = "uk_consolidacao_parte",
                columnNames = { "correlation_id", "origem" }))
public class ConsolidacaoParte extends EntidadeBase {

    private static final long serialVersionUID = 1L;

    @Column(name = "correlation_id", nullable = false, length = 36)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrigemProcessador origem;

    @Lob
    @Column
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusExecucao status;

    @Column(name = "data_recebimento", nullable = false)
    private Instant dataRecebimento;

    protected ConsolidacaoParte() {
    }

    public ConsolidacaoParte(String correlationId, OrigemProcessador origem, String payload,
            StatusExecucao status) {
        this.correlationId = correlationId;
        this.origem = origem;
        this.payload = payload;
        this.status = status;
        this.dataRecebimento = Instant.now();
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public OrigemProcessador getOrigem() {
        return origem;
    }

    public String getPayload() {
        return payload;
    }

    public StatusExecucao getStatus() {
        return status;
    }

    public Instant getDataRecebimento() {
        return dataRecebimento;
    }
}
