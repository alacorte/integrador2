package br.com.integrador2.integrador.origem;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.integrador2.commons.mensageria.RespostaConsolidada;

@Component
public class OrigemJmsGateway implements OrigemGateway {

    private final JmsTemplate jmsTemplateExterno;
    private final ObjectMapper objectMapper;

    public OrigemJmsGateway(@Qualifier("jmsTemplateExterno") JmsTemplate jmsTemplateExterno,
            ObjectMapper objectMapper) {
        this.jmsTemplateExterno = jmsTemplateExterno;
        this.objectMapper = objectMapper;
    }

    /**
     * Serializa aqui, e nao via conversor de mensagem, para que o sistema de
     * origem receba JSON puro — sem a propriedade de tipo Java que o barramento
     * interno carrega.
     */
    @Override
    public void responder(String filaResposta, RespostaConsolidada resposta) {
        try {
            jmsTemplateExterno.convertAndSend(filaResposta, objectMapper.writeValueAsString(resposta));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Nao foi possivel serializar a resposta consolidada", e);
        }
    }
}
