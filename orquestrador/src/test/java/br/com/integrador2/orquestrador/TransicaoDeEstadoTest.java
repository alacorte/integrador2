package br.com.integrador2.orquestrador;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import br.com.integrador2.commons.model.OrigemProcessador;
import br.com.integrador2.commons.model.orquestracao.ConsolidacaoPendente;
import br.com.integrador2.commons.model.orquestracao.ResultadoConsolidacao;
import br.com.integrador2.commons.model.orquestracao.StatusConsolidacao;
import br.com.integrador2.orquestrador.persistence.ConsolidacaoPendenteRepository;

/**
 * Prova deterministica da protecao contra publicacao dupla: quando a ultima
 * resposta parcial e o job de timeout disputam a mesma consolidacao, o UPDATE
 * condicional faz exatamente um dos dois vencer. O perdedor recebe 0 linhas
 * afetadas e nao publica nada.
 */
@SpringBootTest
@Transactional
class TransicaoDeEstadoTest {

    @Autowired
    private ConsolidacaoPendenteRepository pendentes;

    @Test
    void apenasAPrimeiraTransicaoParaProntaVence() {
        String correlationId = novaConsolidacaoPendente();

        int primeira = pendentes.marcarPronta(correlationId, ResultadoConsolidacao.COMPLETO,
                StatusConsolidacao.PRONTA, StatusConsolidacao.PENDENTE);
        int segunda = pendentes.marcarPronta(correlationId, ResultadoConsolidacao.PARCIAL_POR_TIMEOUT,
                StatusConsolidacao.PRONTA, StatusConsolidacao.PENDENTE);

        assertThat(primeira).isEqualTo(1);
        assertThat(segunda).isZero();
        assertThat(pendentes.findByCorrelationId(correlationId))
                .get()
                .satisfies(c -> assertThat(c.getResultado()).isEqualTo(ResultadoConsolidacao.COMPLETO));
    }

    @Test
    void apenasAPrimeiraTransicaoParaConsolidadaVence() {
        String correlationId = novaConsolidacaoPendente();
        pendentes.marcarPronta(correlationId, ResultadoConsolidacao.COMPLETO, StatusConsolidacao.PRONTA,
                StatusConsolidacao.PENDENTE);

        int primeira = pendentes.marcarConsolidada(correlationId, Instant.now(),
                StatusConsolidacao.CONSOLIDADA, StatusConsolidacao.PRONTA);
        int segunda = pendentes.marcarConsolidada(correlationId, Instant.now(),
                StatusConsolidacao.CONSOLIDADA, StatusConsolidacao.PRONTA);

        assertThat(primeira).isEqualTo(1);
        assertThat(segunda).isZero();
    }

    @Test
    void consolidacaoDentroDoPrazoNaoEhPromovidaPorTimeout() {
        novaConsolidacaoPendente();

        assertThat(pendentes.buscarVencidas(StatusConsolidacao.PENDENTE, Instant.now())).isEmpty();
    }

    private String novaConsolidacaoPendente() {
        String correlationId = UUID.randomUUID().toString();
        pendentes.saveAndFlush(new ConsolidacaoPendente(correlationId, "PEDIDO", "{}",
                EnumSet.of(OrigemProcessador.PARAMETRIZADOR),
                Instant.now().plus(1, ChronoUnit.HOURS)));
        return correlationId;
    }
}
