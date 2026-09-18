package br.com.integrador2.parametrizador.regras;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.kie.api.KieBase;
import org.kie.api.io.ResourceType;
import org.kie.internal.utils.KieHelper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * KieBase montado a mao, sem kie-spring (que e de uma geracao anterior do
 * Spring e nao se da bem com o Spring 5.3 do Boot 2.7).
 *
 * O DRL entra como String: e a mesma via que a fatia de regras dinamicas vai
 * usar quando o conteudo vier do banco em vez do classpath.
 */
@Configuration
public class DroolsConfig {

    private static final String RECURSO_DRL = "regras/documento.drl";

    @Bean
    public KieBase kieBase() throws IOException {
        return new KieHelper()
                .addContent(lerRecurso(RECURSO_DRL), ResourceType.DRL)
                .build();
    }

    private String lerRecurso(String caminho) throws IOException {
        try (InputStream entrada = new ClassPathResource(caminho).getInputStream()) {
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int lidos;
            while ((lidos = entrada.read(buffer)) != -1) {
                saida.write(buffer, 0, lidos);
            }
            return new String(saida.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
