package br.com.integrador2.parametrizador.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.integrador2.commons.model.parametrizacao.ExecucaoDocumento;

public interface ExecucaoDocumentoRepository extends JpaRepository<ExecucaoDocumento, Long> {

    Optional<ExecucaoDocumento> findByCorrelationId(String correlationId);
}
