package br.com.integrador2.orquestrador;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.JmsTemplate;

import br.com.integrador2.commons.mensageria.DocumentoRecebido;
import br.com.integrador2.commons.mensageria.Filas;
import br.com.integrador2.commons.mensageria.RespostaConsolidada;
import br.com.integrador2.commons.mensageria.RespostaParcial;
import br.com.integrador2.commons.model.OrigemProcessador;
import br.com.integrador2.commons.model.orquestracao.ResultadoConsolidacao;
import br.com.integrador2.commons.model.orquestracao.StatusConsolidacao;
import br.com.integrador2.commons.model.parametrizacao.StatusExecucao;
import br.com.integrador2.orquestrador.persistence.ConsolidacaoPendenteRepository;

@SpringBootTest
class OrquestracaoFluxoTest {

    private static final long TIMEOUT_RECEBIMENTO_MS = 15_000;

    @Autowired
    private JmsTemplate jmsTemplate;

    @Autowired
    private ConsolidacaoPendenteRepository pendentes;

    @Test
    void espalhaDocumentoEConsolidaQuandoAParteChega() {
        String correlationId = enviarDocumento();

        DocumentoRecebido despachado = receber(Filas.DOCUMENTOS_PARA_PARAMETRIZADOR, DocumentoRecebido.class);
        assertThat(despachado.getCorrelationId()).isEqualTo(correlationId);

        responderParcial(correlationId, "{\"decisao\":\"APROVADO_AUTOMATICO\"}");

        RespostaConsolidada consolidada = receber(Filas.RESPOSTAS_FINAIS, RespostaConsolidada.class);
        assertThat(consolidada.getCorrelationId()).isEqualTo(correlationId);
        assertThat(consolidada.getResultado()).isEqualTo(ResultadoConsolidacao.COMPLETO);
        assertThat(consolidada.getPartes()).containsKey(OrigemProcessador.PARAMETRIZADOR);

        assertThat(pendentes.findByCorrelationId(correlationId))
                .get()
                .satisfies(pendente -> assertThat(pendente.getStatus()).isEqualTo(StatusConsolidacao.CONSOLIDADA));
    }

    /**
     * Ninguem responde: o prazo estoura e a consolidacao sai mesmo assim,
     * marcada como parcial. E o caminho que impede o documento de ficar preso
     * para sempre quando um processador esta fora do ar.
     */
    @Test
    void consolidaParcialmenteQuandoOPrazoEstoura() {
        String correlationId = enviarDocumento();
        receber(Filas.DOCUMENTOS_PARA_PARAMETRIZADOR, DocumentoRecebido.class);

        RespostaConsolidada consolidada = receber(Filas.RESPOSTAS_FINAIS, RespostaConsolidada.class);

        assertThat(consolidada.getCorrelationId()).isEqualTo(correlationId);
        assertThat(consolidada.getResultado()).isEqualTo(ResultadoConsolidacao.PARCIAL_POR_TIMEOUT);
        assertThat(consolidada.getPartes()).isEmpty();
    }

    /**
     * Resposta que chega depois da consolidacao por timeout: fica registrada,
     * mas nao pode gerar uma segunda resposta final.
     */
    @Test
    void respostaAtrasadaNaoGeraSegundaConsolidacao() {
        String correlationId = enviarDocumento();
        receber(Filas.DOCUMENTOS_PARA_PARAMETRIZADOR, DocumentoRecebido.class);

        RespostaConsolidada porTimeout = receber(Filas.RESPOSTAS_FINAIS, RespostaConsolidada.class);
        assertThat(porTimeout.getResultado()).isEqualTo(ResultadoConsolidacao.PARCIAL_POR_TIMEOUT);

        responderParcial(correlationId, "{\"decisao\":\"CHEGUEI_TARDE\"}");

        jmsTemplate.setReceiveTimeout(3_000);
        assertThat(jmsTemplate.receiveAndConvert(Filas.RESPOSTAS_FINAIS)).isNull();
    }

    private String enviarDocumento() {
        String correlationId = UUID.randomUUID().toString();
        jmsTemplate.convertAndSend(Filas.DOCUMENTOS_RECEBIDOS,
                new DocumentoRecebido(correlationId, "PEDIDO", "{\"valor\": 100}", Instant.now()));
        return correlationId;
    }

    private void responderParcial(String correlationId, String payload) {
        jmsTemplate.convertAndSend(Filas.RESPOSTAS_PARCIAIS, new RespostaParcial(correlationId,
                OrigemProcessador.PARAMETRIZADOR, payload, StatusExecucao.SUCESSO));
    }

    private <T> T receber(String fila, Class<T> tipo) {
        jmsTemplate.setReceiveTimeout(TIMEOUT_RECEBIMENTO_MS);
        Object recebido = jmsTemplate.receiveAndConvert(fila);
        assertThat(recebido).isInstanceOf(tipo);
        return tipo.cast(recebido);
    }
}
