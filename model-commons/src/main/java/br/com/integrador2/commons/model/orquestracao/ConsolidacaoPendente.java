package br.com.integrador2.commons.model.orquestracao;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Lob;
import javax.persistence.Table;

import br.com.integrador2.commons.model.EntidadeBase;
import br.com.integrador2.commons.model.OrigemProcessador;

/**
 * Estado de uma consolidacao em andamento (o "gather" do Scatter-Gather).
 *
 * As transicoes de status nunca sao feitas por save() incondicional: sao
 * UPDATEs condicionados ao status esperado, no repositorio. E isso que impede
 * publicar a mesma resposta duas vezes quando a ultima parte chega no exato
 * instante em que o job de timeout decide consolidar.
 */
@Entity
@Table(name = "consolidacao_pendente")
public class ConsolidacaoPendente extends EntidadeBase {

    private static final long serialVersionUID = 1L;

    private static final String SEPARADOR_PARTES = ",";

    @Column(name = "correlation_id", nullable = false, unique = true, length = 36)
    private String correlationId;

    @Column(name = "tipo_documento", nullable = false, length = 60)
    private String tipoDocumento;

    @Lob
    @Column(name = "payload_original", nullable = false)
    private String payloadOriginal;

    @Column(name = "partes_esperadas", nullable = false, length = 255)
    private String partesEsperadas;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusConsolidacao status;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ResultadoConsolidacao resultado;

    @Column(name = "data_criacao", nullable = false)
    private Instant dataCriacao;

    @Column(name = "data_limite", nullable = false)
    private Instant dataLimite;

    @Column(name = "data_consolidacao")
    private Instant dataConsolidacao;

    protected ConsolidacaoPendente() {
    }

    public ConsolidacaoPendente(String correlationId, String tipoDocumento, String payloadOriginal,
            Set<OrigemProcessador> partesEsperadas, Instant dataLimite) {
        this.correlationId = correlationId;
        this.tipoDocumento = tipoDocumento;
        this.payloadOriginal = payloadOriginal;
        this.partesEsperadas = partesEsperadas.stream()
                .map(Enum::name)
                .collect(Collectors.joining(SEPARADOR_PARTES));
        this.status = StatusConsolidacao.PENDENTE;
        this.dataCriacao = Instant.now();
        this.dataLimite = dataLimite;
    }

    public Set<OrigemProcessador> getPartesEsperadas() {
        return Arrays.stream(partesEsperadas.split(SEPARADOR_PARTES))
                .map(String::trim)
                .filter(nome -> !nome.isEmpty())
                .map(OrigemProcessador::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public String getPayloadOriginal() {
        return payloadOriginal;
    }

    public StatusConsolidacao getStatus() {
        return status;
    }

    public ResultadoConsolidacao getResultado() {
        return resultado;
    }

    public Instant getDataCriacao() {
        return dataCriacao;
    }

    public Instant getDataLimite() {
        return dataLimite;
    }

    public Instant getDataConsolidacao() {
        return dataConsolidacao;
    }
}
