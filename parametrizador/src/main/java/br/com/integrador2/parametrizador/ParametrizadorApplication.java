package br.com.integrador2.parametrizador;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * As entidades vivem em model-commons, mas so as do proprio servico sao
 * mapeadas: o Orquestrador tem schema separado e suas tabelas nao existem aqui.
 */
@SpringBootApplication
@EntityScan("br.com.integrador2.commons.model.parametrizacao")
@EnableJpaRepositories("br.com.integrador2.parametrizador.persistence")
public class ParametrizadorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParametrizadorApplication.class, args);
    }
}
