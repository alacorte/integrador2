# integrador2

Parametrizador de integração: documentos JSON chegam de sistemas de origem via
IBM MQ, passam por um motor de regras (Drools) e a resposta consolidada volta
para a fila de resposta do tipo de documento.

Os processadores rodam em **processos separados**, ligados por um barramento
ActiveMQ — hoje só o Parametrizador, e uma análise por Machine Learning entra
como segundo processador sem mudar a mecânica de consolidação.

## Arquitetura

```
[IBM MQ externo] --> Integrador --> documentos-recebidos --> Orquestrador
                                                                  |
                                                    documentos-para-parametrizador
                                                                  |
                                                                  v
                                                           Parametrizador (Drools)
                                                                  |
                                                          respostas-parciais
                                                                  |
                                                                  v
                                                     Orquestrador (consolida)
                                                                  |
                                                          respostas-finais
                                                                  |
                                                                  v
                             Integrador --> [IBM MQ externo, fila de resposta do tipo]
```

O Orquestrador aplica o padrão **Scatter-Gather / Aggregator**: espalha o
documento, espera as respostas parciais correlacionadas e consolida quando
todas chegam — ou quando o prazo estoura, seguindo com o que tiver.

## Módulos

| Módulo | Processo próprio | Descrição |
|---|---|---|
| `model-commons` | não | Entidades JPA, contratos de mensagem e utilitários compartilhados |
| `integrador` | sim | Ponte entre o IBM MQ (origem) e o barramento interno |
| `orquestrador` | sim | Espalha, consolida com timeout e devolve a resposta final |
| `parametrizador` | sim | Motor de regras Drools + histórico de execuções |
| `frontend-desktop` | não | Telas Swing de administração (fatia 2), JDBC direto no MySQL |

Todos em **Java 8**. Os serviços usam Spring Boot 2.7 (última linha que suporta
Java 8) e Drools 7.74.1 (última linha que suporta Java 8).

## Como rodar

Infraestrutura (MySQL com os dois schemas + ActiveMQ):

```bash
podman-compose up -d      # ou: docker compose up -d
```

Cada serviço em um terminal:

```bash
mvn -pl parametrizador -am spring-boot:run
mvn -pl orquestrador   -am spring-boot:run
mvn -pl integrador     -am spring-boot:run
```

O Integrador sobe, em perfil `dev`, um broker embutido na porta **61617** que
faz o papel do IBM MQ. Para enviar um documento de teste, publique na fila
`DEV.QUEUE.PEDIDO.IN` desse broker; a resposta sai em `DEV.QUEUE.PEDIDO.OUT`.

Testes (não precisam de infraestrutura — usam broker em memória e H2):

```bash
mvn test
```

### Atenção ao JDK

O Drools 7.x **não roda em JDK 21**: o MVEL que ele usa referencia
`java.lang.Compiler`, removida nessa versão. O projeto fixa `mvel2 2.5.4.Final`
(também bytecode Java 8) justamente para destravar isso — mas o alvo real
continua sendo o **JDK 8**.

## Desenvolvimento

O projeto é desenvolvido por fatias verticais, com specs em Markdown
(`specs/`) e o Plan Mode do Claude Code: a spec descreve o objetivo e os
critérios de aceite, o plano de implementação é revisado antes de virar
código.
