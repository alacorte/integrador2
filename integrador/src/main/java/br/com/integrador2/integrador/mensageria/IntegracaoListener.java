package br.com.integrador2.integrador.mensageria;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import br.com.integrador2.commons.mensageria.DocumentoRecebido;
import br.com.integrador2.commons.mensageria.Filas;
import br.com.integrador2.commons.mensageria.RespostaConsolidada;
import br.com.integrador2.integrador.config.RoteamentoProperties;
import br.com.integrador2.integrador.origem.OrigemGateway;

/**
 * As duas pontas da ponte: documento entrando do sistema de origem e resposta
 * consolidada voltando para ele.
 */
@Component
public class IntegracaoListener {

    private static final Logger log = LoggerFactory.getLogger(IntegracaoListener.class);
    private static final String MDC_CORRELATION_ID = "correlationId";

    /**
     * Um listener por tipo de documento enquanto existe uma rota so. Registrar
     * listeners dinamicamente a partir da parametrizacao e o que torna varios
     * tipos possiveis — trabalho de uma fatia posterior.
     */
    private static final String TIPO_DOCUMENTO = "PEDIDO";

    private final JmsTemplate jmsTemplateInterno;
    private final OrigemGateway origem;
    private final RoteamentoProperties roteamento;

    public IntegracaoListener(@Qualifier("jmsTemplateInterno") JmsTemplate jmsTemplateInterno,
            OrigemGateway origem, RoteamentoProperties roteamento) {
        this.jmsTemplateInterno = jmsTemplateInterno;
        this.origem = origem;
        this.roteamento = roteamento;
    }

    /**
     * O correlationId nasce aqui, no primeiro contato com o documento, e
     * acompanha todo o resto do caminho.
     */
    @JmsListener(destination = "${integrador.rotas.PEDIDO.fila-entrada}", containerFactory = "fabricaExterna")
    public void receberDaOrigem(String payloadJson) {
        String correlationId = UUID.randomUUID().toString();
        MDC.put(MDC_CORRELATION_ID, correlationId);
        try {
            log.info("Documento {} recebido da origem", TIPO_DOCUMENTO);
            jmsTemplateInterno.convertAndSend(Filas.DOCUMENTOS_RECEBIDOS,
                    new DocumentoRecebido(correlationId, TIPO_DOCUMENTO, payloadJson, Instant.now()));
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }

    @JmsListener(destination = Filas.RESPOSTAS_FINAIS, containerFactory = "fabricaInterna")
    public void devolverAOrigem(RespostaConsolidada resposta) {
        MDC.put(MDC_CORRELATION_ID, resposta.getCorrelationId());
        try {
            String filaResposta = roteamento.rotaDe(resposta.getTipoDocumento()).getFilaResposta();
            origem.responder(filaResposta, resposta);
            log.info("Resposta consolidada ({}) devolvida a origem em {}", resposta.getResultado(),
                    filaResposta);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }
}
