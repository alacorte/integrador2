package br.com.integrador2.orquestrador.consolidacao;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.integrador2.commons.mensageria.Filas;
import br.com.integrador2.commons.mensageria.RespostaConsolidada;
import br.com.integrador2.commons.model.OrigemProcessador;
import br.com.integrador2.commons.model.orquestracao.ConsolidacaoParte;
import br.com.integrador2.commons.model.orquestracao.ConsolidacaoPendente;

/**
 * Unico ponto do Orquestrador que publica a resposta final.
 *
 * Publica antes de marcar CONSOLIDADA, e nao o contrario: se o processo cair
 * entre as duas coisas, a linha continua PRONTA e a proxima passada republica.
 * Isso troca "perder a resposta em silencio" por "talvez entregar duas vezes",
 * que e o lado certo do trade-off — o correlationId permite ao consumidor
 * descartar a duplicata.
 */
@Component
public class PublicacaoConsolidadaJob {

    private static final Logger log = LoggerFactory.getLogger(PublicacaoConsolidadaJob.class);
    private static final String MDC_CORRELATION_ID = "correlationId";

    private final ConsolidacaoService consolidacao;
    private final JmsTemplate jmsTemplate;

    public PublicacaoConsolidadaJob(ConsolidacaoService consolidacao, JmsTemplate jmsTemplate) {
        this.consolidacao = consolidacao;
        this.jmsTemplate = jmsTemplate;
    }

    @Scheduled(fixedDelayString = "${orquestrador.intervalo-publicacao-ms:2000}")
    public void executar() {
        consolidacao.promoverVencidas()
                .forEach(correlationId -> log.info("Prazo esgotado para {}, consolidando parcialmente",
                        correlationId));

        consolidacao.buscarProntasParaPublicar().forEach(this::publicar);
    }

    private void publicar(ConsolidacaoPendente pendente) {
        MDC.put(MDC_CORRELATION_ID, pendente.getCorrelationId());
        try {
            Map<OrigemProcessador, String> partes = new LinkedHashMap<>();
            for (ConsolidacaoParte parte : consolidacao.partesDe(pendente.getCorrelationId())) {
                partes.put(parte.getOrigem(), parte.getPayload());
            }

            jmsTemplate.convertAndSend(Filas.RESPOSTAS_FINAIS,
                    new RespostaConsolidada(pendente.getCorrelationId(), pendente.getTipoDocumento(),
                            partes, pendente.getResultado()));

            consolidacao.marcarComoPublicada(pendente.getCorrelationId());
            log.info("Resposta consolidada publicada ({} partes, resultado {})", partes.size(),
                    pendente.getResultado());
        } catch (RuntimeException e) {
            log.error("Falha ao publicar resposta consolidada; sera tentada de novo na proxima passada", e);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }
}
