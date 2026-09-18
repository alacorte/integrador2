package br.com.integrador2.integrador;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.JmsTemplate;

import br.com.integrador2.commons.mensageria.DocumentoRecebido;
import br.com.integrador2.commons.mensageria.Filas;
import br.com.integrador2.commons.mensageria.RespostaConsolidada;
import br.com.integrador2.commons.model.OrigemProcessador;
import br.com.integrador2.commons.model.orquestracao.ResultadoConsolidacao;

@SpringBootTest
class IntegradorPonteTest {

    private static final long TIMEOUT_RECEBIMENTO_MS = 10_000;
    private static final String FILA_ENTRADA_ORIGEM = "DEV.QUEUE.PEDIDO.IN";
    private static final String FILA_RESPOSTA_ORIGEM = "DEV.QUEUE.PEDIDO.OUT";

    @Autowired
    @Qualifier("jmsTemplateInterno")
    private JmsTemplate interno;

    @Autowired
    @Qualifier("jmsTemplateExterno")
    private JmsTemplate externo;

    @Test
    void documentoDaOrigemViraDocumentoRecebidoNoBarramentoInterno() {
        externo.convertAndSend(FILA_ENTRADA_ORIGEM, "{\"valor\": 5000}");

        interno.setReceiveTimeout(TIMEOUT_RECEBIMENTO_MS);
        Object recebido = interno.receiveAndConvert(Filas.DOCUMENTOS_RECEBIDOS);

        assertThat(recebido).isInstanceOf(DocumentoRecebido.class);
        DocumentoRecebido documento = (DocumentoRecebido) recebido;
        assertThat(documento.getTipoDocumento()).isEqualTo("PEDIDO");
        assertThat(documento.getPayloadJson()).contains("5000");
        assertThat(documento.getCorrelationId()).isNotBlank();
    }

    /**
     * Para fora sai JSON puro: o sistema de origem nao deve precisar conhecer
     * os nomes das nossas classes Java.
     */
    @Test
    void respostaConsolidadaVoltaParaAOrigemComoJsonPuro() {
        Map<OrigemProcessador, String> partes = new LinkedHashMap<>();
        partes.put(OrigemProcessador.PARAMETRIZADOR, "{\"decisao\":\"APROVACAO_MANUAL\"}");

        interno.convertAndSend(Filas.RESPOSTAS_FINAIS, new RespostaConsolidada(UUID.randomUUID().toString(),
                "PEDIDO", partes, ResultadoConsolidacao.COMPLETO));

        externo.setReceiveTimeout(TIMEOUT_RECEBIMENTO_MS);
        Object devolvido = externo.receiveAndConvert(FILA_RESPOSTA_ORIGEM);

        assertThat(devolvido).isInstanceOf(String.class);
        assertThat((String) devolvido)
                .contains("APROVACAO_MANUAL")
                .contains("COMPLETO")
                .doesNotContain("_type");
    }
}
