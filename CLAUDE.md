# integrador2

Parametrizador de integração: documentos JSON chegam por IBM MQ, passam por um
motor de regras (Drools) e a resposta consolidada volta para a fila de resposta
do tipo de documento. Padrão **Scatter-Gather/Aggregator**, com os processadores
em processos separados ligados por ActiveMQ.

**`docs/arquitetura.md` tem o raciocínio completo** — modelo de dados, contratos
de mensagem, máquina de estados, e o porquê de cada decisão. Ler antes de propor
mudança estrutural.

---

## Restrições que não se negociam

- **Java 8 em todos os módulos.** É o que amarra Spring Boot em 2.7.x e Drools
  na linha 7.x — as últimas versões que suportam Java 8. Não sugerir upgrade de
  nenhum dos dois sem antes tratar essa restrição com o usuário.
- **Drools não roda em JDK 21**: o MVEL referencia `java.lang.Compiler`,
  removida nessa versão. O root pom fixa `mvel2 2.5.4.Final` para destravar. Se
  aparecer `NoClassDefFoundError: java/lang/Compiler`, é esse pin que se perdeu.
- **Clientes ActiveMQ ficam no 5.16.7**; o 5.17+ exige Java 11. A imagem do
  broker no compose é 5.17.7 porque não existe imagem oficial 5.16.x.
- **`drools-mvel` é dependência obrigatória** e sua falta só aparece em runtime.
  **Não usar `kie-spring`** (geração anterior do Spring).

---

## Módulos

| Módulo | Processo | Papel |
|---|---|---|
| `model-commons` | — | Entidades JPA + contratos de mensagem. Compartilhado por todos. |
| `integrador` | sim | Ponte IBM MQ ↔ barramento interno. Único com **dois** `ConnectionFactory`. |
| `orquestrador` | sim | Espalha, consolida com timeout, devolve. Tem a máquina de estados. |
| `parametrizador` | sim | Drools + histórico de execuções. |
| `frontend-desktop` | — | Telas Swing (fatia 2, ainda só o esqueleto), JDBC direto no MySQL. |

### Onde fica o quê

```
model-commons/
  commons/mensageria/     DocumentoRecebido, RespostaParcial, RespostaConsolidada, Filas
  commons/model/          EntidadeBase, OrigemProcessador
  commons/model/parametrizacao/   TipoDocumento, ExecucaoDocumento, StatusExecucao
  commons/model/orquestracao/     ConsolidacaoPendente, ConsolidacaoParte,
                                  StatusConsolidacao, ResultadoConsolidacao

integrador/
  config/       JmsConfig (os dois brokers), BrokerExternoConfig (stand-in do IBM MQ),
                RoteamentoProperties (tipo -> filas externas)
  mensageria/   IntegracaoListener (as duas pontas da ponte)
  origem/       OrigemGateway + OrigemJmsGateway (fronteira com a origem)

orquestrador/
  consolidacao/ ConsolidacaoService (máquina de estados),
                PublicacaoConsolidadaJob (único ponto que publica)
  mensageria/   OrquestracaoListener, JmsConfig
  persistence/  ConsolidacaoPendenteRepository (UPDATEs condicionais), ConsolidacaoParteRepository
  resources/db/migration/

parametrizador/
  regras/       MotorRegras (interface), DroolsMotorRegras, DroolsConfig (KieBase à mão),
                FatoDocumento (campos num Map — regra nova não exige classe nova)
  documento/    ProcessadorDocumentoService (idempotência + orquestração local)
  mensageria/   DocumentoListener, JmsConfig
  resources/regras/documento.drl
  resources/db/migration/
```

---

## Invariantes — quebrar isso introduz bug silencioso

1. **No Orquestrador, handler de mensagem nunca publica.** Só grava no banco.
   Publicar é exclusividade do `PublicacaoConsolidadaJob`. Misturar as duas
   coisas reintroduz o dual-write que perde documento em silêncio: o job de
   timeout só varre `PENDENTE` e nunca resgataria o caso.
2. **Transição de estado é sempre `UPDATE ... WHERE status = <esperado>`**, e só
   age quem afetou 1 linha. Nunca `save()` incondicional num
   `ConsolidacaoPendente`. É o que impede publicar a mesma resposta duas vezes
   quando a última parte chega junto com o estouro do prazo — e o que permite
   escalar o Orquestrador horizontalmente.
3. **O job publica antes de marcar `CONSOLIDADA`**, nunca o contrário. Isso troca
   "perder a resposta" por "talvez duplicar", que é o lado certo do trade-off.
4. **Idempotência vem de constraint única no banco**, não de `if` na aplicação
   (`execucao_documento.correlation_id`, `consolidacao_parte(correlation_id, origem)`).
5. **Cada serviço só mapeia as entidades do seu schema** (`@EntityScan` no
   subpacote). Parametrizador e Orquestrador têm schemas MySQL separados, com
   histórico de Flyway próprio — juntar quebra o start do segundo serviço.
6. **Para fora trafega JSON puro**, sem a propriedade `_type` que o barramento
   interno usa. O sistema de origem não conhece nossas classes.
7. **No Integrador, todo `@JmsListener` e `JmsTemplate` declara com qual broker
   fala** (`containerFactory` / `@Qualifier`). O configurer do Boot aplica o
   conversor Jackson a qualquer fábrica que passe por ele, então a fábrica
   externa repõe `SimpleMessageConverter` explicitamente.
8. **`correlationId` no MDC em todo listener**, com `try/finally`. É toda a
   observabilidade que existe hoje.

---

## Comandos

```bash
mvn test                       # tudo; broker em memória e H2, sem infra externa
mvn -pl orquestrador -am test  # um módulo

podman-compose up -d           # MySQL (dois schemas) + ActiveMQ; ou docker compose

mvn -pl parametrizador -am spring-boot:run
mvn -pl orquestrador   -am spring-boot:run
mvn -pl integrador     -am spring-boot:run
```

Portas: **61616** barramento interno, **61617** broker stand-in do IBM MQ
(perfil `dev`, dentro do Integrador), **8161** console web do ActiveMQ
(`admin`/`admin`), **3306** MySQL.

Documento de teste entra em `DEV.QUEUE.PEDIDO.IN`, resposta sai em
`DEV.QUEUE.PEDIDO.OUT`.

---

## Convenções

- Código, comentários e documentação em **português**; identificadores sem acento.
- Comentário só quando explica um **porquê** não óbvio — uma armadilha, uma
  restrição escondida, um trade-off. Não descrever o que o código já diz.
- Testes não podem depender de infraestrutura externa: broker `vm://` e H2.
- Fluxo de trabalho: spec em `specs/<feature>.md` a partir do `TEMPLATE.md`,
  depois Plan Mode para revisar o plano antes de escrever código.

---

## O que ainda não foi verificado

Nada foi executado contra infraestrutura real (o ambiente onde o código foi
escrito não tinha container runtime nem JDK 8). Pendências em
`docs/arquitetura.md`, seção 12 — resumindo: rodar num JDK 8 real, subir o
compose e fazer o fluxo ponta a ponta, e confirmar cliente 5.16.7 ↔ broker
5.17.7. **Não afirmar que o sistema funciona ponta a ponta até isso acontecer.**
