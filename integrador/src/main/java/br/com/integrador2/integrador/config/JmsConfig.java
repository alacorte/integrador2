package br.com.integrador2.integrador.config;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;
import org.springframework.jms.support.converter.SimpleMessageConverter;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * O Integrador e o unico servico com dois brokers: o externo (sistemas de
 * origem — hoje um ActiveMQ embutido no lugar do IBM MQ) e o interno (o
 * barramento entre os processos).
 *
 * O Spring Boot so autoconfigura UM ConnectionFactory, entao tudo aqui e
 * declarado a mao — e por isso todo @JmsListener e todo JmsTemplate deste
 * modulo diz explicitamente com qual dos dois esta falando. Trocar o externo
 * pelo IBM MQ real e trocar um bean: connectionFactoryExterna passa a devolver
 * um MQConnectionFactory.
 */
@Configuration
public class JmsConfig {

    private static final String PROPRIEDADE_TIPO = "_type";

    @Bean
    public MessageConverter jacksonJmsMessageConverter(ObjectMapper objectMapper) {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName(PROPRIEDADE_TIPO);
        converter.setObjectMapper(objectMapper);
        return converter;
    }

    @Bean
    @Primary
    public ConnectionFactory connectionFactoryInterna(
            @Value("${spring.activemq.broker-url}") String url) {
        return new ActiveMQConnectionFactory(url);
    }

    @Bean
    public ConnectionFactory connectionFactoryExterna(
            @Value("${integrador.broker-externo.url}") String url) {
        return new ActiveMQConnectionFactory(url);
    }

    @Bean
    public JmsTemplate jmsTemplateInterno(
            @Qualifier("connectionFactoryInterna") ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    /**
     * Sem o conversor Jackson de proposito: para fora trafega JSON puro, sem a
     * propriedade de tipo Java que o barramento interno usa. O sistema de
     * origem nao tem que conhecer os nomes das nossas classes.
     */
    @Bean
    public JmsTemplate jmsTemplateExterno(
            @Qualifier("connectionFactoryExterna") ConnectionFactory connectionFactory) {
        return new JmsTemplate(connectionFactory);
    }

    @Bean
    public DefaultJmsListenerContainerFactory fabricaInterna(
            @Qualifier("connectionFactoryInterna") ConnectionFactory connectionFactory,
            DefaultJmsListenerContainerFactoryConfigurer configurer,
            MessageConverter messageConverter) {
        return montarFabrica(connectionFactory, configurer, messageConverter);
    }

    /**
     * O documento chega do sistema de origem como texto JSON cru, sem
     * metadado de tipo — por isso esta fabrica fica com o conversor padrao
     * (TextMessage vira String) em vez do Jackson tipado.
     */
    /**
     * O documento chega do sistema de origem como texto JSON cru, sem metadado
     * de tipo. O conversor precisa ser reposto explicitamente para
     * SimpleMessageConverter: o configurer do Boot aplica o bean Jackson do
     * contexto a qualquer fabrica que passe por ele.
     */
    @Bean
    public DefaultJmsListenerContainerFactory fabricaExterna(
            @Qualifier("connectionFactoryExterna") ConnectionFactory connectionFactory,
            DefaultJmsListenerContainerFactoryConfigurer configurer) {
        return montarFabrica(connectionFactory, configurer, new SimpleMessageConverter());
    }

    private DefaultJmsListenerContainerFactory montarFabrica(ConnectionFactory connectionFactory,
            DefaultJmsListenerContainerFactoryConfigurer configurer, MessageConverter messageConverter) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setSessionTransacted(true);
        return factory;
    }
}
