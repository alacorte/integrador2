package br.com.integrador2.orquestrador.consolidacao;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.integrador2.commons.mensageria.DocumentoRecebido;
import br.com.integrador2.commons.mensageria.RespostaParcial;
import br.com.integrador2.commons.model.OrigemProcessador;
import br.com.integrador2.commons.model.orquestracao.ConsolidacaoParte;
import br.com.integrador2.commons.model.orquestracao.ConsolidacaoPendente;
import br.com.integrador2.commons.model.orquestracao.ResultadoConsolidacao;
import br.com.integrador2.commons.model.orquestracao.StatusConsolidacao;
import br.com.integrador2.orquestrador.persistence.ConsolidacaoParteRepository;
import br.com.integrador2.orquestrador.persistence.ConsolidacaoPendenteRepository;

/**
 * Regras de estado da consolidacao. Nada aqui publica mensagem: publicar e
 * responsabilidade exclusiva do job agendado, lendo as linhas que ficaram
 * PRONTA. E essa separacao que evita o dual-write — se o processo cair entre
 * gravar e publicar, a linha continua PRONTA e a proxima passada publica.
 */
@Service
public class ConsolidacaoService {

    private static final Logger log = LoggerFactory.getLogger(ConsolidacaoService.class);

    /** Enquanto so existe o Parametrizador; o branch de ML entra aqui. */
    private static final Set<OrigemProcessador> PARTES_ESPERADAS =
            EnumSet.of(OrigemProcessador.PARAMETRIZADOR);

    private final ConsolidacaoPendenteRepository pendentes;
    private final ConsolidacaoParteRepository partes;
    private final Duration prazoConsolidacao;

    public ConsolidacaoService(ConsolidacaoPendenteRepository pendentes, ConsolidacaoParteRepository partes,
            @Value("${orquestrador.prazo-consolidacao:PT30S}") Duration prazoConsolidacao) {
        this.pendentes = pendentes;
        this.partes = partes;
        this.prazoConsolidacao = prazoConsolidacao;
    }

    /**
     * @return false se o documento ja estava em consolidacao (reentrega do JMS),
     *         caso em que ele nao deve ser reespalhado aos processadores.
     */
    @Transactional
    public boolean registrarDocumento(DocumentoRecebido documento) {
        if (pendentes.findByCorrelationId(documento.getCorrelationId()).isPresent()) {
            log.info("Documento ja em consolidacao, ignorando reentrega");
            return false;
        }

        pendentes.save(new ConsolidacaoPendente(documento.getCorrelationId(), documento.getTipoDocumento(),
                documento.getPayloadJson(), PARTES_ESPERADAS, Instant.now().plus(prazoConsolidacao)));
        return true;
    }

    public Set<OrigemProcessador> partesEsperadas() {
        return PARTES_ESPERADAS;
    }

    /**
     * Grava a parte recebida e, se ela completou o conjunto esperado, promove a
     * consolidacao a PRONTA.
     */
    @Transactional
    public void registrarParte(RespostaParcial resposta) {
        String correlationId = resposta.getCorrelationId();

        ConsolidacaoPendente consolidacao = pendentes.findByCorrelationId(correlationId).orElse(null);
        if (consolidacao == null) {
            log.warn("Resposta parcial sem consolidacao correspondente, descartando");
            return;
        }

        if (partes.existsByCorrelationIdAndOrigem(correlationId, resposta.getOrigem())) {
            log.info("Parte de {} ja registrada, ignorando reentrega", resposta.getOrigem());
            return;
        }

        partes.save(new ConsolidacaoParte(correlationId, resposta.getOrigem(),
                resposta.getPayloadResultado(), resposta.getStatus()));

        if (!todasAsPartesChegaram(consolidacao, correlationId)) {
            return;
        }

        int afetadas = pendentes.marcarPronta(correlationId, ResultadoConsolidacao.COMPLETO,
                StatusConsolidacao.PRONTA, StatusConsolidacao.PENDENTE);
        if (afetadas == 0) {
            // O job de timeout chegou primeiro: a consolidacao ja saiu de PENDENTE.
            // Esta parte fica gravada para auditoria, mas nao dispara publicacao.
            log.info("Consolidacao ja havia saido de PENDENTE (timeout); parte registrada sem republicar");
        }
    }

    private boolean todasAsPartesChegaram(ConsolidacaoPendente consolidacao, String correlationId) {
        List<ConsolidacaoParte> recebidas = partes.findByCorrelationId(correlationId);
        return recebidas.size() >= consolidacao.getPartesEsperadas().size();
    }

    /**
     * Promove a PRONTA as consolidacoes cujo prazo estourou.
     *
     * @return os correlationIds que esta chamada promoveu — apenas eles, para
     *         que duas instancias do Orquestrador nao publiquem a mesma resposta.
     */
    @Transactional
    public List<String> promoverVencidas() {
        List<String> vencidas = pendentes.buscarVencidas(StatusConsolidacao.PENDENTE, Instant.now());
        vencidas.removeIf(correlationId -> pendentes.marcarPronta(correlationId,
                ResultadoConsolidacao.PARCIAL_POR_TIMEOUT, StatusConsolidacao.PRONTA,
                StatusConsolidacao.PENDENTE) == 0);
        return vencidas;
    }

    public List<ConsolidacaoPendente> buscarProntasParaPublicar() {
        return pendentes.findByStatus(StatusConsolidacao.PRONTA);
    }

    public List<ConsolidacaoParte> partesDe(String correlationId) {
        return partes.findByCorrelationId(correlationId);
    }

    @Transactional
    public void marcarComoPublicada(String correlationId) {
        pendentes.marcarConsolidada(correlationId, Instant.now(), StatusConsolidacao.CONSOLIDADA,
                StatusConsolidacao.PRONTA);
    }
}
