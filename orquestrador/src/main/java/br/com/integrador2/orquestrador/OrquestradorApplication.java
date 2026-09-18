package br.com.integrador2.orquestrador;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EntityScan("br.com.integrador2.commons.model.orquestracao")
@EnableJpaRepositories("br.com.integrador2.orquestrador.persistence")
public class OrquestradorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrquestradorApplication.class, args);
    }
}
