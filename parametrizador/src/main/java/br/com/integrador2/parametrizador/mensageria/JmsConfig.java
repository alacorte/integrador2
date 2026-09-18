package br.com.integrador2.parametrizador.mensageria;

import javax.jms.ConnectionFactory;

import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON como TextMessage, em vez do ObjectMessage (serializacao Java) que o
 * Spring usa por padrao: mensagem legivel no console do broker, e sem a
 * whitelist de trustedPackages do ActiveMQ no caminho.
 *
 * sessionTransacted garante que a politica de redelivery do ActiveMQ valha —
 * sem isso, uma mensagem que sempre falha fica girando no listener para sempre
 * em vez de ir parar na DLQ.
 */
@Configuration
public class JmsConfig {

    static final String PROPRIEDADE_TIPO = "_type";

    @Bean
    public MessageConverter jacksonJmsMessageConverter(ObjectMapper objectMapper) {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName(PROPRIEDADE_TIPO);
        converter.setObjectMapper(objectMapper);
        return converter;
    }

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            ConnectionFactory connectionFactory,
            DefaultJmsListenerContainerFactoryConfigurer configurer,
            MessageConverter messageConverter) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setSessionTransacted(true);
        return factory;
    }
}
