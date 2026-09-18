# integrador2

Parametrizador de integração: documentos JSON chegam por IBM MQ, passam por um
motor de regras (Drools) e a resposta consolidada volta para a fila de resposta
do tipo de documento. Padrão Scatter-Gather/Aggregator, com os processadores em
processos separados.

O raciocínio por trás das decisões está em **`docs/arquitetura.md`** — ler antes
de propor mudanças estruturais.

## Restrições que não se negociam

- **Java 8 em todos os módulos.** É o que amarra Spring Boot em 2.7.x e Drools
  na linha 7.x — as últimas versões que suportam Java 8. Não sugerir upgrade de
  nenhum dos dois sem antes tratar essa restrição com o usuário.
- **Drools não roda em JDK 21** (o MVEL referencia `java.lang.Compiler`,
  removida). O root pom fixa `mvel2 2.5.4.Final` para destravar. Se aparecer
  `NoClassDefFoundError: java/lang/Compiler`, é esse pin que se perdeu.
- **Clientes ActiveMQ ficam no 5.16.7**; o 5.17+ exige Java 11. A imagem do
  broker no compose é 5.17.7 porque não existe imagem oficial 5.16.x.

## Módulos

| Módulo | Processo | Papel |
|---|---|---|
| `model-commons` | — | Entidades JPA + contratos de mensagem. Compartilhado por todos. |
| `integrador` | sim | Ponte IBM MQ ↔ barramento interno. Único com **dois** `ConnectionFactory`. |
| `orquestrador` | sim | Espalha, consolida com timeout, devolve. Tem a máquina de estados. |
| `parametrizador` | sim | Drools + histórico de execuções. |
| `frontend-desktop` | — | Telas Swing (fatia 2), JDBC direto no MySQL. |

## Invariantes do código — quebrar isso introduz bug silencioso

1. **No Orquestrador, handler de mensagem nunca publica.** Só grava no banco.
   Publicar é exclusividade do `PublicacaoConsolidadaJob`. Misturar as duas
   coisas reintroduz o dual-write que perde documento em silêncio.
2. **Transição de estado é sempre `UPDATE ... WHERE status = <esperado>`**, e só
   age quem afetou 1 linha. Nunca `save()` incondicional num
   `ConsolidacaoPendente`. É o que impede publicar a mesma resposta duas vezes.
3. **Idempotência vem de constraint única no banco**, não de `if` na aplicação.
4. **Cada serviço só mapeia as entidades do seu schema** (`@EntityScan` no
   subpacote). Parametrizador e Orquestrador têm schemas MySQL separados, com
   histórico de Flyway próprio — juntar quebra o start do segundo serviço.
5. **Para fora trafega JSON puro**, sem a propriedade `_type` que o barramento
   interno usa. O sistema de origem não conhece nossas classes.

## Comandos

```bash
mvn test                      # tudo; usa broker em memória e H2, sem infra externa
mvn -pl orquestrador -am test # um módulo

podman-compose up -d          # MySQL (dois schemas) + ActiveMQ; ou docker compose

mvn -pl parametrizador -am spring-boot:run
mvn -pl orquestrador   -am spring-boot:run
mvn -pl integrador     -am spring-boot:run
```

O Integrador sobe, em perfil `dev`, um broker embutido na **61617** que faz o
papel do IBM MQ (o barramento interno é a 61616). Documento de teste entra em
`DEV.QUEUE.PEDIDO.IN`, resposta sai em `DEV.QUEUE.PEDIDO.OUT`.

## Convenções

- Código e comentários em **português**, sem acentos em identificadores.
- Comentário só quando explica um **porquê** não óbvio (uma armadilha, uma
  restrição escondida). Não descrever o que o código já diz.
- Fluxo de trabalho: spec em `specs/<feature>.md` a partir do `TEMPLATE.md`,
  depois Plan Mode para revisar o plano antes de escrever código.
