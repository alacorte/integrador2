CREATE TABLE tipo_documento (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    codigo        VARCHAR(60)  NOT NULL,
    fila_entrada  VARCHAR(120) NOT NULL,
    fila_resposta VARCHAR(120) NOT NULL,
    descricao     VARCHAR(255),
    ativo         BIT(1)       NOT NULL DEFAULT b'1',
    PRIMARY KEY (id),
    CONSTRAINT uk_tipo_documento_codigo UNIQUE (codigo)
) ENGINE=InnoDB;

CREATE TABLE execucao_documento (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    correlation_id  VARCHAR(36)  NOT NULL,
    tipo_documento  VARCHAR(60)  NOT NULL,
    payload_entrada LONGTEXT     NOT NULL,
    payload_saida   LONGTEXT,
    status          VARCHAR(20)  NOT NULL,
    mensagem_erro   VARCHAR(1000),
    data_execucao   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    -- Idempotencia contra reentrega do JMS: o mesmo documento nunca gera duas execucoes.
    CONSTRAINT uk_execucao_documento_correlation UNIQUE (correlation_id)
) ENGINE=InnoDB;

INSERT INTO tipo_documento (codigo, fila_entrada, fila_resposta, descricao, ativo)
VALUES ('PEDIDO', 'DEV.QUEUE.PEDIDO.IN', 'DEV.QUEUE.PEDIDO.OUT',
        'Tipo de documento de exemplo da fatia 1', b'1');
