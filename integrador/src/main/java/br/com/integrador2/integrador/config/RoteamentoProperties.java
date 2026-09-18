package br.com.integrador2.integrador.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Mapeia cada tipo de documento as suas filas no sistema de origem.
 *
 * Esta configuracao espelha as colunas fila_entrada/fila_resposta da tabela
 * tipo_documento do Parametrizador. A duplicidade e consciente: o Integrador
 * nao acessa banco nesta fatia (e uma ponte de protocolo, sem estado). Unificar
 * as duas pontas e trabalho da fatia que liga a tela de administracao ao
 * roteamento.
 */
@Component
@ConfigurationProperties(prefix = "integrador")
public class RoteamentoProperties {

    private Map<String, Rota> rotas = new LinkedHashMap<>();

    public Map<String, Rota> getRotas() {
        return rotas;
    }

    public void setRotas(Map<String, Rota> rotas) {
        this.rotas = rotas;
    }

    public Rota rotaDe(String tipoDocumento) {
        Rota rota = rotas.get(tipoDocumento);
        if (rota == null) {
            throw new IllegalStateException("Sem rota configurada para o tipo de documento " + tipoDocumento);
        }
        return rota;
    }

    public static class Rota {

        private String filaEntrada;
        private String filaResposta;

        public String getFilaEntrada() {
            return filaEntrada;
        }

        public void setFilaEntrada(String filaEntrada) {
            this.filaEntrada = filaEntrada;
        }

        public String getFilaResposta() {
            return filaResposta;
        }

        public void setFilaResposta(String filaResposta) {
            this.filaResposta = filaResposta;
        }
    }
}
