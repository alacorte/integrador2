package br.com.integrador2.integrador.config;

import org.apache.activemq.broker.BrokerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Broker embutido que faz o papel do IBM MQ enquanto ele nao existe.
 *
 * Ele expoe um conector TCP proprio, numa porta diferente da do barramento
 * interno, por dois motivos: manter a fronteira "externo x interno" real em vez
 * de decorativa, e permitir publicar um documento de teste de fora do processo
 * (um broker apenas vm:// so seria alcancavel de dentro desta JVM).
 *
 * No perfil que usar o IBM MQ de verdade este bean simplesmente nao existe.
 */
@Configuration
@Profile("dev")
public class BrokerExternoConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    public BrokerService brokerExterno(
            @Value("${integrador.broker-externo.conector:tcp://0.0.0.0:61617}") String conector) throws Exception {
        BrokerService broker = new BrokerService();
        broker.setBrokerName("origem-externa");
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.setUseShutdownHook(false);
        broker.addConnector(conector);
        return broker;
    }
}
