package br.com.integrador2.parametrizador.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.integrador2.commons.model.parametrizacao.TipoDocumento;

public interface TipoDocumentoRepository extends JpaRepository<TipoDocumento, Long> {

    Optional<TipoDocumento> findByCodigoAndAtivoTrue(String codigo);
}
