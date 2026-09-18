package br.com.integrador2.orquestrador.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.integrador2.commons.model.orquestracao.ConsolidacaoPendente;
import br.com.integrador2.commons.model.orquestracao.ResultadoConsolidacao;
import br.com.integrador2.commons.model.orquestracao.StatusConsolidacao;

/**
 * As transicoes de status sao UPDATEs condicionados ao status esperado, nunca
 * save() incondicional. O numero de linhas afetadas e a resposta para "fui eu
 * quem fez essa transicao?" — e e o que garante que a ultima resposta parcial
 * e o job de timeout, disputando a mesma consolidacao, nao publiquem os dois.
 */
public interface ConsolidacaoPendenteRepository extends JpaRepository<ConsolidacaoPendente, Long> {

    Optional<ConsolidacaoPendente> findByCorrelationId(String correlationId);

    List<ConsolidacaoPendente> findByStatus(StatusConsolidacao status);

    @Query("select c.correlationId from ConsolidacaoPendente c "
            + "where c.status = :pendente and c.dataLimite < :agora")
    List<String> buscarVencidas(@Param("pendente") StatusConsolidacao pendente,
            @Param("agora") Instant agora);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ConsolidacaoPendente c set c.status = :pronta, c.resultado = :resultado "
            + "where c.correlationId = :correlationId and c.status = :pendente")
    int marcarPronta(@Param("correlationId") String correlationId,
            @Param("resultado") ResultadoConsolidacao resultado,
            @Param("pronta") StatusConsolidacao pronta,
            @Param("pendente") StatusConsolidacao pendente);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ConsolidacaoPendente c set c.status = :consolidada, c.dataConsolidacao = :agora "
            + "where c.correlationId = :correlationId and c.status = :pronta")
    int marcarConsolidada(@Param("correlationId") String correlationId,
            @Param("agora") Instant agora,
            @Param("consolidada") StatusConsolidacao consolidada,
            @Param("pronta") StatusConsolidacao pronta);
}
