CREATE TABLE consolidacao_pendente (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    correlation_id    VARCHAR(36)  NOT NULL,
    tipo_documento    VARCHAR(60)  NOT NULL,
    payload_original  LONGTEXT     NOT NULL,
    partes_esperadas  VARCHAR(255) NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    resultado         VARCHAR(30),
    data_criacao      DATETIME(6)  NOT NULL,
    data_limite       DATETIME(6)  NOT NULL,
    data_consolidacao DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_consolidacao_pendente_correlation UNIQUE (correlation_id)
) ENGINE=InnoDB;

-- O job de publicacao varre por status e por prazo vencido a cada poucos segundos.
CREATE INDEX ix_consolidacao_pendente_status ON consolidacao_pendente (status);
CREATE INDEX ix_consolidacao_pendente_prazo ON consolidacao_pendente (status, data_limite);

CREATE TABLE consolidacao_parte (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    correlation_id   VARCHAR(36) NOT NULL,
    origem           VARCHAR(30) NOT NULL,
    payload          LONGTEXT,
    status           VARCHAR(20) NOT NULL,
    data_recebimento DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- Idempotencia contra reentrega do JMS e ausencia de read-modify-write:
    -- cada processador contribui no maximo uma linha por consolidacao.
    CONSTRAINT uk_consolidacao_parte UNIQUE (correlation_id, origem)
) ENGINE=InnoDB;
