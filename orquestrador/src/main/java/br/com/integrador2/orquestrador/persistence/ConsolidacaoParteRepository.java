package br.com.integrador2.orquestrador.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.integrador2.commons.model.OrigemProcessador;
import br.com.integrador2.commons.model.orquestracao.ConsolidacaoParte;

public interface ConsolidacaoParteRepository extends JpaRepository<ConsolidacaoParte, Long> {

    List<ConsolidacaoParte> findByCorrelationId(String correlationId);

    boolean existsByCorrelationIdAndOrigem(String correlationId, OrigemProcessador origem);
}
