package br.com.integrador2.parametrizador.mensageria;

import org.slf4j.MDC;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import br.com.integrador2.commons.mensageria.DocumentoRecebido;
import br.com.integrador2.commons.mensageria.Filas;
import br.com.integrador2.commons.mensageria.RespostaParcial;
import br.com.integrador2.parametrizador.documento.ProcessadorDocumentoService;

@Component
public class DocumentoListener {

    private static final String MDC_CORRELATION_ID = "correlationId";

    private final ProcessadorDocumentoService processador;
    private final JmsTemplate jmsTemplate;

    public DocumentoListener(ProcessadorDocumentoService processador, JmsTemplate jmsTemplate) {
        this.processador = processador;
        this.jmsTemplate = jmsTemplate;
    }

    @JmsListener(destination = Filas.DOCUMENTOS_PARA_PARAMETRIZADOR,
            containerFactory = "jmsListenerContainerFactory")
    public void receber(DocumentoRecebido documento) {
        MDC.put(MDC_CORRELATION_ID, documento.getCorrelationId());
        try {
            RespostaParcial resposta = processador.processar(documento);
            jmsTemplate.convertAndSend(Filas.RESPOSTAS_PARCIAIS, resposta);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }
}
