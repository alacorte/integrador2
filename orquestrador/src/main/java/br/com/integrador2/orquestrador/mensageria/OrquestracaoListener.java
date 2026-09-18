package br.com.integrador2.orquestrador.mensageria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import br.com.integrador2.commons.mensageria.DocumentoRecebido;
import br.com.integrador2.commons.mensageria.Filas;
import br.com.integrador2.commons.mensageria.RespostaParcial;
import br.com.integrador2.commons.model.OrigemProcessador;
import br.com.integrador2.orquestrador.consolidacao.ConsolidacaoService;

/**
 * Espalha o documento para os processadores e recolhe as respostas parciais.
 * Nenhum dos dois metodos publica a resposta final — isso e do job agendado.
 */
@Component
public class OrquestracaoListener {

    private static final Logger log = LoggerFactory.getLogger(OrquestracaoListener.class);
    private static final String MDC_CORRELATION_ID = "correlationId";

    private final ConsolidacaoService consolidacao;
    private final JmsTemplate jmsTemplate;

    public OrquestracaoListener(ConsolidacaoService consolidacao, JmsTemplate jmsTemplate) {
        this.consolidacao = consolidacao;
        this.jmsTemplate = jmsTemplate;
    }

    @JmsListener(destination = Filas.DOCUMENTOS_RECEBIDOS, containerFactory = "jmsListenerContainerFactory")
    public void receberDocumento(DocumentoRecebido documento) {
        MDC.put(MDC_CORRELATION_ID, documento.getCorrelationId());
        try {
            if (!consolidacao.registrarDocumento(documento)) {
                return;
            }
            espalhar(documento);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }

    @JmsListener(destination = Filas.RESPOSTAS_PARCIAIS, containerFactory = "jmsListenerContainerFactory")
    public void receberRespostaParcial(RespostaParcial resposta) {
        MDC.put(MDC_CORRELATION_ID, resposta.getCorrelationId());
        try {
            consolidacao.registrarParte(resposta);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }

    private void espalhar(DocumentoRecebido documento) {
        for (OrigemProcessador destino : consolidacao.partesEsperadas()) {
            jmsTemplate.convertAndSend(filaDe(destino), documento);
            log.info("Documento despachado para {}", destino);
        }
    }

    private String filaDe(OrigemProcessador origem) {
        switch (origem) {
            case PARAMETRIZADOR:
                return Filas.DOCUMENTOS_PARA_PARAMETRIZADOR;
            default:
                throw new IllegalStateException("Sem fila configurada para o processador " + origem);
        }
    }
}
