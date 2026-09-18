# Arquitetura do integrador2

Documento vivo: registra **o que foi decidido e por quê**. As mensagens de
commit contam o que mudou; este arquivo conta o raciocínio por trás — que é o
que se perde quando ninguém escreve.

---

## 1. O domínio

Documentos JSON chegam de sistemas de origem via **IBM MQ**. O **Integrador**
recebe e repassa ao **Orquestrador**, que distribui o documento para
processadores especializados rodando em **processos separados** — hoje só o
**Parametrizador** (motor de regras Drools), futuramente também uma **análise
por Machine Learning**. O Orquestrador espera as respostas parciais, consolida
(com timeout: se um processador não responder a tempo, segue com o que tiver) e
devolve ao Integrador, que envia de volta à fila de resposta do tipo de
documento.

É o padrão **Scatter-Gather / Aggregator** dos Enterprise Integration Patterns.

### Glossário

| Termo | Significado |
|---|---|
| **Integrador** | Processo que faz a ponte entre o IBM MQ (externo) e o barramento interno. Não conhece regra de negócio. |
| **Orquestrador** | Processo que espalha o documento, correlaciona as respostas e consolida. Não conhece Drools nem o conteúdo das regras. |
| **Parametrizador** | Processo que aplica o motor de regras. Não conhece mensageria externa. |
| **correlationId** | UUID gerado pelo Integrador no primeiro contato com o documento. Amarra todo o resto do caminho, em todos os processos, nos logs e no banco. |
| **Resposta parcial** | O que um processador produz sozinho. |
| **Resposta consolidada** | A junção das respostas parciais, devolvida à origem. |
| **Parte esperada** | Cada processador de quem o Orquestrador aguarda resposta antes de consolidar. |
| **Fatia** | Incremento vertical de funcionalidade (ver seção 10). |

---

## 2. Visão de processos

```
                    ┌──────────────────────────────────────────┐
                    │        Sistemas de origem (IBM MQ)       │
                    │   DEV.QUEUE.PEDIDO.IN / .OUT (stand-in)  │
                    └────────────┬───────────────▲─────────────┘
                                 │               │
                        documento│               │resposta consolidada
                          (JSON) │               │     (JSON puro)
                    ┌────────────▼───────────────┴─────────────┐
                    │              INTEGRADOR                  │
                    │  gera correlationId; dois ConnectionFactory│
                    └────────────┬───────────────▲─────────────┘
                                 │               │
       barramento interno        │               │
       (ActiveMQ, 61616)  documentos-       respostas-
                          recebidos          finais
                                 │               │
                    ┌────────────▼───────────────┴─────────────┐
                    │             ORQUESTRADOR                 │
                    │   cria ConsolidacaoPendente (PENDENTE)   │
                    │   job agendado publica e consolida       │
                    └────────┬──────────────────▲──────────────┘
                             │                  │
              documentos-para-           respostas-parciais
              parametrizador                    │
                             │                  │
                    ┌────────▼──────────────────┴──────────────┐
                    │            PARAMETRIZADOR                │
                    │      Drools + histórico de execução      │
                    └──────────────────────────────────────────┘

        (futuro) documentos-para-ml ──► ML ──► respostas-parciais
```

Cada caixa é um **processo Spring Boot independente**, com sua própria JVM.
Podem rodar em máquinas diferentes: a única coisa que os liga é o ActiveMQ.

### Filas (constantes em `Filas.java`)

| Fila | De | Para | Payload |
|---|---|---|---|
| `documentos-recebidos` | Integrador | Orquestrador | `DocumentoRecebido` |
| `documentos-para-parametrizador` | Orquestrador | Parametrizador | `DocumentoRecebido` |
| `respostas-parciais` | Processadores | Orquestrador | `RespostaParcial` |
| `respostas-finais` | Orquestrador | Integrador | `RespostaConsolidada` |

As filas externas (`DEV.QUEUE.PEDIDO.IN` / `.OUT`) **não** são constantes: vêm
da configuração do Integrador, porque dependem do tipo de documento.

---

## 3. Contratos de mensagem

Em `model-commons`, pacote `br.com.integrador2.commons.mensageria`. São POJOs
puros (sem JPA), serializados como JSON.

**`DocumentoRecebido`** — o documento entrando no sistema.

| Campo | Tipo | Observação |
|---|---|---|
| `correlationId` | `String` (UUID) | Nasce no Integrador; também vai no header `JMSCorrelationID` |
| `tipoDocumento` | `String` | Ex: `PEDIDO`. Determina regra e fila de resposta |
| `payloadJson` | `String` | O documento cru, como veio da origem |
| `dataRecebimento` | `Instant` | |

**`RespostaParcial`** — o que um processador produziu.

| Campo | Tipo | Observação |
|---|---|---|
| `correlationId` | `String` | Amarra à consolidação |
| `origem` | `OrigemProcessador` | `PARAMETRIZADOR` ou `ML` |
| `payloadResultado` | `String` | JSON com o resultado do processador |
| `status` | `StatusExecucao` | `SUCESSO` / `ERRO` |
| `mensagemErro` | `String` | Preenchido só em erro |
| `dataResposta` | `Instant` | |

**`RespostaConsolidada`** — o que volta para a origem.

| Campo | Tipo | Observação |
|---|---|---|
| `correlationId` | `String` | Permite ao consumidor descartar duplicata |
| `tipoDocumento` | `String` | O Integrador usa para achar a fila de resposta |
| `partes` | `Map<OrigemProcessador, String>` | Resultado de cada processador |
| `resultado` | `ResultadoConsolidacao` | `COMPLETO` / `PARCIAL_POR_TIMEOUT` |
| `dataConsolidacao` | `Instant` | |

### Serialização: tipada dentro, pura fora

No barramento interno, `MappingJackson2MessageConverter` com `TextMessage` e a
propriedade de tipo `_type` — o consumidor desserializa direto na classe certa.

Na borda externa, o Integrador serializa à mão (`OrigemJmsGateway`) e envia
texto puro, **sem `_type`**: o sistema de origem não deve depender dos nomes
das nossas classes Java.

Usar JSON como `TextMessage` (em vez do `ObjectMessage` padrão do Spring, que é
serialização Java) também evita de vez a whitelist `trustedPackages` do
ActiveMQ, e deixa a mensagem legível no console do broker.

---

## 4. Modelo de dados

Dois schemas MySQL separados — ver seção 6 para o motivo.

### Schema `parametrizador_db`

**`tipo_documento`** — a parametrização de um tipo.

| Coluna | Tipo | Observação |
|---|---|---|
| `id` | BIGINT AI | |
| `codigo` | VARCHAR(60) | **único** |
| `fila_entrada` | VARCHAR(120) | Fila no IBM MQ |
| `fila_resposta` | VARCHAR(120) | Fila no IBM MQ |
| `descricao` | VARCHAR(255) | |
| `ativo` | BIT | |

**`execucao_documento`** — histórico de cada passagem pelo motor de regras.

| Coluna | Tipo | Observação |
|---|---|---|
| `id` | BIGINT AI | |
| `correlation_id` | VARCHAR(36) | **único** — é a idempotência |
| `tipo_documento` | VARCHAR(60) | |
| `payload_entrada` | LONGTEXT | |
| `payload_saida` | LONGTEXT | Resultado das regras |
| `status` | VARCHAR(20) | `SUCESSO` / `ERRO` |
| `mensagem_erro` | VARCHAR(1000) | |
| `data_execucao` | DATETIME(6) | |

### Schema `orquestrador_db`

**`consolidacao_pendente`** — o estado do "gather".

| Coluna | Tipo | Observação |
|---|---|---|
| `id` | BIGINT AI | |
| `correlation_id` | VARCHAR(36) | **único** |
| `tipo_documento` | VARCHAR(60) | |
| `payload_original` | LONGTEXT | Guardado para depurar e permitir redespacho |
| `partes_esperadas` | VARCHAR(255) | Nomes separados por vírgula |
| `status` | VARCHAR(20) | `PENDENTE` / `PRONTA` / `CONSOLIDADA` |
| `resultado` | VARCHAR(30) | `COMPLETO` / `PARCIAL_POR_TIMEOUT` |
| `data_criacao` | DATETIME(6) | |
| `data_limite` | DATETIME(6) | Deadline do timeout |
| `data_consolidacao` | DATETIME(6) | |

Índices em `(status)` e `(status, data_limite)` — são exatamente as varreduras
do job agendado, que roda a cada poucos segundos.

**`consolidacao_parte`** — cada resposta parcial já recebida.

| Coluna | Tipo | Observação |
|---|---|---|
| `id` | BIGINT AI | |
| `correlation_id` | VARCHAR(36) | |
| `origem` | VARCHAR(30) | |
| `payload` | LONGTEXT | |
| `status` | VARCHAR(20) | |
| `data_recebimento` | DATETIME(6) | |

**Único em `(correlation_id, origem)`.** Essa constraint faz três coisas de uma
vez: dá idempotência contra reentrega do JMS, elimina o read-modify-write que um
campo JSON de "partes recebidas" exigiria (que perderia atualização assim que o
branch de ML trouxer concorrência real), e é o formato natural para N partes.

---

## 5. A máquina de estados do Orquestrador

### O problema que ela resolve

Se o handler que recebe uma `RespostaParcial` gravasse no banco **e**
publicasse a `RespostaConsolidada` na mesma operação, uma falha entre as duas
(broker fora do ar, processo derrubado) perderia a mensagem **em silêncio** — o
job de timeout só varre `PENDENTE`, então nunca a resgataria. O documento
sumiria sem erro, sem timeout, sem nada além de um stack trace que ninguém lê.

### O desenho

```
   chega DocumentoRecebido
            │
            ▼
      ┌───────────┐   última parte esperada chega       ┌──────────┐
      │ PENDENTE  │ ──────────────────────────────────► │  PRONTA  │
      │           │   (resultado = COMPLETO)            │          │
      │           │                                     │          │
      │           │   prazo estoura (job agendado)      │          │
      │           │ ──────────────────────────────────► │          │
      └───────────┘   (resultado = PARCIAL_POR_TIMEOUT) └────┬─────┘
                                                              │
                                        job publica em respostas-finais
                                                              │
                                                              ▼
                                                      ┌──────────────┐
                                                      │ CONSOLIDADA  │
                                                      └──────────────┘
```

**O handler de mensagem só grava; quem publica é exclusivamente o job
agendado** (`PublicacaoConsolidadaJob`). Se a publicação falhar, a linha
continua `PRONTA` e a próxima passada tenta de novo.

O job publica **antes** de marcar `CONSOLIDADA`, nunca o contrário: isso troca
"perder a resposta" por "talvez entregar duas vezes", que é o lado certo do
trade-off — o `correlationId` permite ao consumidor descartar a duplicata.

### Toda transição é um UPDATE condicional

```sql
UPDATE consolidacao_pendente
   SET status = 'PRONTA', resultado = ?
 WHERE correlation_id = ? AND status = 'PENDENTE'
```

E só age quem afetou **1 linha**. Sem isso, quando a última resposta parcial
chega no exato instante em que o job de timeout decide consolidar, os dois
publicam — e o sistema de origem recebe **duas respostas para um documento**,
uma `COMPLETO` e outra `PARCIAL_POR_TIMEOUT`. Com timeout ajustado perto da
latência real do processador, isso não é raro: acontece.

É também o que sustenta a promessa de escalar o Orquestrador horizontalmente:
duas instâncias, dois jobs, mesma trava.

Coberto por teste determinístico em `TransicaoDeEstadoTest` — a segunda
tentativa de transição recebe 0 linhas afetadas.

### Resposta atrasada

Se um processador responde depois da consolidação por timeout, o `UPDATE ...
WHERE status = 'PENDENTE'` não afeta nenhuma linha. A parte fica **gravada para
auditoria**, mas não dispara publicação. Logado e descartado explicitamente —
não é exceção, é um caminho previsto.

---

## 6. Decisões e o porquê

### Processos separados, não módulos no mesmo JVM

Permite escalar cada parte separadamente, e faz o processo de ML (que pode nem
ser Java) entrar como mais um consumidor do barramento, sem mudança nenhuma na
mecânica de consolidação.

O IBM MQ é só a **borda externa**, tocada exclusivamente pelo Integrador.
Internamente, ninguém mais sabe que IBM MQ existe.

### Idempotência vem de constraint, não de lógica

- `execucao_documento.correlation_id` único → reentrega do JMS não roda a regra
  de novo; o resultado gravado é republicado.
- `consolidacao_parte (correlation_id, origem)` único → cada processador
  contribui no máximo uma linha.

Colocar isso no banco em vez de num `if` da aplicação significa que a garantia
vale mesmo com duas instâncias do mesmo serviço rodando.

### Um schema MySQL por serviço

Cada um com seu `flyway_schema_history`. Num schema compartilhado, o segundo
serviço a subir encontraria migrations aplicadas que não consegue resolver
localmente e **falharia no start** com *"Detected applied migration not resolved
locally"*. Não é sutil — é erro garantido no dia um.

### Entidades JPA em model-commons

Decisão do time: entidades anotadas direto nos POJOs compartilhados, sem
duplicar entidade JPA + DTO.

A coordenada Maven é `jakarta.persistence:jakarta.persistence-api:2.2.3` —
**não** `javax.persistence:javax.persistence-api`. O pacote Java continua
`javax.persistence.*`; o que muda é o artefato. Usar o antigo coloca duas jars
com `javax.persistence.Entity` no classpath, porque o
`spring-boot-starter-data-jpa` 2.7 exclui explicitamente essa coordenada.

Cada serviço faz `@EntityScan` só do seu subpacote — o Parametrizador não mapeia
as tabelas do Orquestrador, que nem existem no schema dele.

A classe base chama-se `EntidadeBase`, e não `Entity`: uma classe `Entity` no
mesmo pacote das entidades JPA seria sombreada por `import
javax.persistence.Entity`, quebrando a compilação com uma mensagem que não
aponta para a causa.

### O motor de regras é uma interface

`MotorRegras` isola o Drools. A fatia de regras dinâmicas troca a implementação
(DRL vindo do banco, `KieBase` recarregado) sem tocar em mensageria nem no
serviço de processamento.

O `KieBase` é montado à mão em `DroolsConfig` via `KieHelper.addContent(String,
DRL)` — com o DRL como **String**, que é exatamente a via que a fatia de regras
dinâmicas vai usar.

### O fato do Drools usa um Map, não campos tipados

`FatoDocumento` carrega `Map<String, Object> campos` em vez de atributos
tipados, porque o conjunto de campos varia por tipo de documento — e o objetivo
de um parametrizador é justamente que regra nova não exija classe nova. Um DRL
acessa assim:

```java
FatoDocumento(tipoDocumento == "PEDIDO", ((Number) campos["valor"]).doubleValue() > 1000)
```

### Os dois ConnectionFactory do Integrador

O Spring Boot autoconfigura **um** `ConnectionFactory`. O Integrador precisa de
dois (externo e interno), então tudo em `JmsConfig` é declarado à mão — e por
isso todo `@JmsListener` e todo `JmsTemplate` do módulo diz explicitamente com
qual dos dois está falando (`containerFactory = "fabricaExterna"` / `"fabricaInterna"`,
`@Qualifier("jmsTemplateExterno")` / `"jmsTemplateInterno"`).

Detalhe que custa tempo: o `DefaultJmsListenerContainerFactoryConfigurer` do
Boot aplica o bean `MessageConverter` do contexto a **qualquer** fábrica que
passe por ele. A fábrica externa precisa repor explicitamente o
`SimpleMessageConverter`, senão recebe o Jackson tipado e quebra ao receber
JSON cru sem `_type`.

### Entrega confiável

`sessionTransacted = true` em todos os listener containers, para que a política
de redelivery padrão do ActiveMQ (6 tentativas → `ActiveMQ.DLQ`) valha. Sem
isso, uma mensagem malformada gira para sempre no listener, queimando CPU.

### Observabilidade mínima

`correlationId` no MDC do SLF4J em todo listener, com `try/finally`. O padrão de
log de cada serviço já inclui `[%X{correlationId}]`, então dá para seguir um
documento pelos três processos com um `grep`.

---

## 7. Configuração

### Portas

| Porta | O quê |
|---|---|
| 61616 | ActiveMQ — barramento interno (container) |
| 61617 | Broker embutido no Integrador — stand-in do IBM MQ (perfil `dev`) |
| 8161 | Console web do ActiveMQ (`admin`/`admin`) |
| 3306 | MySQL |

### Propriedades que importam

| Propriedade | Serviço | Padrão | O quê |
|---|---|---|---|
| `orquestrador.prazo-consolidacao` | orquestrador | `PT30S` | Quanto esperar por todas as partes |
| `orquestrador.intervalo-publicacao-ms` | orquestrador | `2000` | Frequência do job de publicação/timeout |
| `integrador.broker-externo.url` | integrador | `tcp://localhost:61617` | Onde fica o broker da origem |
| `integrador.broker-externo.conector` | integrador | `tcp://0.0.0.0:61617` | Conector do broker embutido (perfil `dev`) |
| `integrador.rotas.<TIPO>.fila-entrada` | integrador | — | Fila de entrada no IBM MQ |
| `integrador.rotas.<TIPO>.fila-resposta` | integrador | — | Fila de resposta no IBM MQ |

### Perfis

- **`dev`** (padrão do Integrador): sobe o broker embutido que faz o papel do
  IBM MQ, com conector TCP próprio.
- **`mq`** (futuro): o bean `connectionFactoryExterna` passa a devolver um
  `MQConnectionFactory`, e `BrokerExternoConfig` simplesmente não existe.

---

## 8. Como adicionar um novo processador (ex: o de ML)

A mecânica de consolidação já é genérica para N partes. Os passos:

1. **`OrigemProcessador`**: o valor `ML` já existe no enum.
2. **`ConsolidacaoService.PARTES_ESPERADAS`**: incluir `OrigemProcessador.ML`.
3. **`OrquestracaoListener.filaDe(...)`**: mapear `ML` para uma nova fila
   (`documentos-para-ml`), adicionando a constante em `Filas`.
4. **O novo processador**: consome `documentos-para-ml`, produz uma
   `RespostaParcial` com `origem = ML` e publica em `respostas-parciais`.
   Se não for Java, basta respeitar o contrato JSON — mas atenção: o barramento
   interno espera a propriedade `_type` para desserialização tipada; um
   processador em outra stack precisa enviá-la, ou o Orquestrador precisa de um
   conversor mais tolerante nessa fila.

Nada além disso muda: correlação, timeout, transições de estado e publicação já
funcionam para N partes.

---

## 9. Estratégia de testes

Todos os testes rodam **sem infraestrutura externa**: broker ActiveMQ em memória
(`vm://`, não persistente) e H2 em modo MySQL. `mvn test` funciona num clone
limpo.

| Teste | O que prova |
|---|---|
| `ParametrizadorFluxoTest` | Regra dispara e publica resposta parcial; reentrega não duplica execução |
| `OrquestracaoFluxoTest` | Fluxo completo; consolidação parcial por timeout; resposta atrasada não gera segunda consolidação |
| `TransicaoDeEstadoTest` | Prova determinística das travas de transição — a segunda tentativa recebe 0 linhas |
| `IntegradorPonteTest` | Documento da origem vira mensagem interna; resposta volta como JSON puro sem `_type` |

Nos testes, os brokers interno e externo do Integrador são **instâncias
distintas** de propósito: se fossem o mesmo, a fronteira que o teste deveria
exercitar não existiria de verdade.

---

## 10. Estado atual e próximas fatias

**Fatia 1 — construída.** Integrador + Orquestrador + Parametrizador, fluxo
completo do documento chegando até a resposta consolidada voltando, com
caminhos de timeout e resposta atrasada cobertos por teste.

| # | Fatia | Conteúdo |
|---|---|---|
| 2 | Telas + containers | Cadastro de `TipoDocumento` e histórico de execuções em Swing, JDBC direto. Em paralelo: Dockerfile de cada serviço e inclusão no compose. |
| 3 | Regras dinâmicas | DRL no MySQL, editor na tela desktop, `KieBase` recarregado em runtime. |
| 4 | Branch de ML | Segundo processador, exercitando consolidação real de duas partes. |
| 5 | Múltiplos tipos | Registro dinâmico de listeners no Integrador a partir da parametrização (resolve a duplicidade da seção 11). |
| 6 | IBM MQ real | `MQConnectionFactory` no lugar do bean `connectionFactoryExterna`. |

---

## 11. Armadilhas conhecidas

### Drools 7.x não roda em JDK 21

O `mvel2 2.4.15` que o Drools 7.74.1 arrasta referencia `java.lang.Compiler`,
classe **removida no JDK 21** → `NoClassDefFoundError` ao montar o `KieBase`.

O root pom fixa **`org.mvel:mvel2:2.5.4.Final`**, que não referencia mais essa
classe e continua sendo bytecode Java 8 (major version 52) — então o alvo do
projeto permanece intacto e o código roda em JDK 8, 17 e 21.

Outros cuidados com Drools nesta versão:

- `drools-mvel` é artefato **separado** desde a 7.4x; sem ele, o `KieHelper`
  falha em runtime, não na compilação.
- **Não usar `kie-spring`** — é de uma geração anterior do Spring e não se dá
  bem com o Spring 5.3 do Boot 2.7.
- `drools-compiler` traz `xstream` em escopo compile sem versão gerenciada pelo
  Boot; o root pom fixa a versão (dependência com histórico de CVEs).

### Versões de ActiveMQ: cliente e broker divergem de propósito

Os clientes ficam no **5.16.7** (gerenciado pelo Spring Boot 2.7) porque o
**5.17+ exige Java 11**. Já a imagem oficial não publica tags 5.16.x — a mais
antiga é 5.17.6, e `latest` é 6.x, outra geração. O compose fixa **5.17.7**.

Cliente 5.16 e broker 5.17 devem conversar por OpenWire dentro da mesma linha
5.x, mas **isso não foi verificado empiricamente** (o ambiente onde o código foi
escrito não tinha runtime de container). Confirmar no primeiro `up`.

### Roteamento de filas duplicado

O mapeamento tipo de documento → filas externas existe em dois lugares: no
`application.yml` do Integrador e nas colunas `fila_entrada`/`fila_resposta` de
`tipo_documento`. É consciente: o Integrador não acessa banco nesta fatia (é
uma ponte de protocolo sem estado). Unificar é trabalho da fatia 5.

### Um listener por tipo de documento

`IntegracaoListener` tem o tipo `PEDIDO` fixo e resolve a fila por placeholder
(`${integrador.rotas.PEDIDO.fila-entrada}`). Funciona enquanto existe uma rota
só; múltiplos tipos exigem registro dinâmico de listeners — fatia 5.

---

## 12. Verificação ainda pendente

Nada disso foi executado contra infraestrutura real — o ambiente onde o código
foi escrito não tinha container runtime nem JDK 8. Falta:

- [ ] Rodar a suíte num **JDK 8 real** (aqui rodou em 17 e 21, compilando com `--release 8`).
- [ ] `podman-compose up -d` e o fluxo ponta a ponta com MySQL e ActiveMQ de
      verdade: publicar um documento em `DEV.QUEUE.PEDIDO.IN` do broker externo
      (porta 61617) e conferir a resposta em `DEV.QUEUE.PEDIDO.OUT`.
- [ ] Confirmar a compatibilidade cliente 5.16.7 ↔ broker 5.17.7.
- [ ] Confirmar que as migrations Flyway rodam contra MySQL real (foram escritas
      em SQL MySQL; os testes usam H2 com o schema gerado pelo Hibernate).
