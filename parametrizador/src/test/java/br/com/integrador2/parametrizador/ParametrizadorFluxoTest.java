package br.com.integrador2.parametrizador;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.JmsTemplate;

import br.com.integrador2.commons.mensageria.DocumentoRecebido;
import br.com.integrador2.commons.mensageria.Filas;
import br.com.integrador2.commons.mensageria.RespostaParcial;
import br.com.integrador2.commons.model.OrigemProcessador;
import br.com.integrador2.commons.model.parametrizacao.StatusExecucao;
import br.com.integrador2.parametrizador.persistence.ExecucaoDocumentoRepository;

@SpringBootTest
class ParametrizadorFluxoTest {

    private static final long TIMEOUT_RECEBIMENTO_MS = 10_000;

    @Autowired
    private JmsTemplate jmsTemplate;

    @Autowired
    private ExecucaoDocumentoRepository execucoes;

    @Test
    void aplicaRegraDeValorAltoEPublicaRespostaParcial() {
        String correlationId = UUID.randomUUID().toString();

        jmsTemplate.convertAndSend(Filas.DOCUMENTOS_PARA_PARAMETRIZADOR,
                new DocumentoRecebido(correlationId, "PEDIDO", "{\"valor\": 5000}", Instant.now()));

        RespostaParcial resposta = receberResposta();

        assertThat(resposta.getCorrelationId()).isEqualTo(correlationId);
        assertThat(resposta.getOrigem()).isEqualTo(OrigemProcessador.PARAMETRIZADOR);
        assertThat(resposta.getStatus()).isEqualTo(StatusExecucao.SUCESSO);
        assertThat(resposta.getPayloadResultado()).contains("APROVACAO_MANUAL");

        assertThat(execucoes.findByCorrelationId(correlationId)).isPresent();
    }

    @Test
    void aplicaRegraDeValorNormal() {
        String correlationId = UUID.randomUUID().toString();

        jmsTemplate.convertAndSend(Filas.DOCUMENTOS_PARA_PARAMETRIZADOR,
                new DocumentoRecebido(correlationId, "PEDIDO", "{\"valor\": 100}", Instant.now()));

        assertThat(receberResposta().getPayloadResultado()).contains("APROVADO_AUTOMATICO");
    }

    /**
     * Reentrega da mesma mensagem nao pode gerar uma segunda execucao — e o que
     * a constraint unica de correlationId garante.
     */
    @Test
    void reentregaDoMesmoDocumentoNaoDuplicaExecucao() {
        String correlationId = UUID.randomUUID().toString();
        DocumentoRecebido documento =
                new DocumentoRecebido(correlationId, "PEDIDO", "{\"valor\": 5000}", Instant.now());

        jmsTemplate.convertAndSend(Filas.DOCUMENTOS_PARA_PARAMETRIZADOR, documento);
        receberResposta();

        jmsTemplate.convertAndSend(Filas.DOCUMENTOS_PARA_PARAMETRIZADOR, documento);
        RespostaParcial reentrega = receberResposta();

        assertThat(reentrega.getPayloadResultado()).contains("APROVACAO_MANUAL");
        assertThat(execucoes.findAll())
                .filteredOn(execucao -> execucao.getCorrelationId().equals(correlationId))
                .hasSize(1);
    }

    private RespostaParcial receberResposta() {
        jmsTemplate.setReceiveTimeout(TIMEOUT_RECEBIMENTO_MS);
        Object recebido = jmsTemplate.receiveAndConvert(Filas.RESPOSTAS_PARCIAIS);
        assertThat(recebido).isInstanceOf(RespostaParcial.class);
        return (RespostaParcial) recebido;
    }
}
