# Arquitetura do integrador2

Documento vivo: registra **o que foi decidido e por quê**. As mensagens de
commit contam o que mudou; este arquivo conta o raciocínio por trás — que é o
que se perde quando ninguém escreve.

## O domínio

Documentos JSON chegam de sistemas de origem via **IBM MQ**. O **Integrador**
recebe e repassa ao **Orquestrador**, que distribui o documento para
processadores especializados rodando em **processos separados** — hoje só o
**Parametrizador** (motor de regras Drools), futuramente também uma **análise
por Machine Learning**. O Orquestrador espera as respostas parciais, consolida
(com timeout: se um processador não responder a tempo, segue com o que tiver) e
devolve ao Integrador, que envia de volta à fila de resposta do tipo de
documento.

É o padrão **Scatter-Gather / Aggregator** dos Enterprise Integration Patterns.

## Decisões e o porquê

### Processos separados, não módulos no mesmo JVM

Integrador, Orquestrador e Parametrizador são apps Spring Boot independentes
ligados por **ActiveMQ Classic**. Isso permite escalar cada parte
separadamente, e faz o processo de ML (que pode nem ser Java) entrar como mais
um consumidor do barramento, sem nenhuma mudança na mecânica de consolidação.

O IBM MQ é só a **borda externa**, tocada exclusivamente pelo Integrador.
Internamente, ninguém mais sabe que IBM MQ existe.

### O Orquestrador usa um mini outbox — handler não publica

**O problema:** se o handler que recebe uma `RespostaParcial` gravasse no banco
*e* publicasse a `RespostaConsolidada` na mesma operação, uma falha entre as
duas (broker fora do ar, processo derrubado) perderia a mensagem **em
silêncio** — o job de timeout só varre `PENDENTE`, então nunca a resgataria.

**O desenho:** `PENDENTE → PRONTA → CONSOLIDADA`. O handler de mensagem só
grava e, quando a última parte chega, promove a `PRONTA`. Quem publica é
**exclusivamente** o job agendado, lendo as linhas `PRONTA`. Se a publicação
falhar, a linha continua `PRONTA` e a próxima passada tenta de novo.

O job publica **antes** de marcar `CONSOLIDADA`, nunca o contrário: isso troca
"perder a resposta" por "talvez entregar duas vezes", que é o lado certo do
trade-off — o `correlationId` permite ao consumidor descartar a duplicata.

### Toda transição de estado é um UPDATE condicional

`UPDATE ... SET status = X WHERE correlation_id = ? AND status = <esperado>`, e
só age quem afetou 1 linha. Sem isso, quando a última resposta parcial chega no
exato instante em que o job de timeout decide consolidar, os dois publicam — e
o sistema de origem recebe **duas respostas para um documento**, uma `COMPLETO`
e outra `PARCIAL_POR_TIMEOUT`. Com timeout ajustado perto da latência real do
processador, isso não é raro: acontece.

É também o que sustenta a promessa de escalar o Orquestrador horizontalmente:
duas instâncias, dois jobs, mesma trava.

Coberto por teste determinístico em `TransicaoDeEstadoTest`.

### Idempotência vem de constraint, não de lógica

- `execucao_documento.correlation_id` é único → reentrega do JMS não roda a
  regra de novo; o resultado gravado é republicado.
- `consolidacao_parte (correlation_id, origem)` é único → cada processador
  contribui no máximo uma linha.

A tabela `consolidacao_parte` substituiu um campo JSON de "partes recebidas"
justamente por isso: um blob mutável exigiria read-modify-write, que perde
atualização assim que o branch de ML trouxer concorrência real.

### Um schema MySQL por serviço

Parametrizador e Orquestrador têm schemas separados, cada um com seu
`flyway_schema_history`. Num schema compartilhado, o segundo serviço a subir
encontraria migrations aplicadas que não consegue resolver localmente e
**falharia no start** com *"Detected applied migration not resolved locally"*.
Não é sutil — é erro garantido no dia um.

### JSON puro na borda, envelope tipado dentro

O barramento interno usa `MappingJackson2MessageConverter` com uma propriedade
de tipo (`_type`), o que dá desserialização direta para a classe certa. Para
fora, o Integrador serializa à mão e envia texto puro: o sistema de origem não
deve depender dos nomes das nossas classes Java.

Usar JSON como `TextMessage` (em vez do `ObjectMessage` padrão do Spring) também
evita de vez a whitelist `trustedPackages` do ActiveMQ.

### Entidades JPA em model-commons

Decisão do time: entidades anotadas direto nos POJOs compartilhados, sem
duplicar entidade JPA + DTO. A coordenada Maven é
`jakarta.persistence:jakarta.persistence-api:2.2.3` — **não**
`javax.persistence:javax.persistence-api`. O pacote Java continua
`javax.persistence.*`; o que muda é o artefato. Usar o artefato errado coloca
duas jars com `javax.persistence.Entity` no classpath, porque o
`spring-boot-starter-data-jpa` 2.7 exclui explicitamente o antigo.

Cada serviço faz `@EntityScan` só do seu subpacote — o Parametrizador não mapeia
as tabelas do Orquestrador, que nem existem no schema dele.

## Armadilhas conhecidas

### Drools 7.x não roda em JDK 21

O `mvel2 2.4.15` que o Drools 7.74.1 arrasta referencia `java.lang.Compiler`,
classe **removida no JDK 21** → `NoClassDefFoundError` ao montar o `KieBase`.

O root pom fixa **`org.mvel:mvel2:2.5.4.Final`**, que não referencia mais essa
classe e continua sendo bytecode Java 8 (major version 52) — então o alvo do
projeto permanece intacto e o código roda em JDK 8, 17 e 21.

Outros cuidados com Drools nesta versão:
- `drools-mvel` é artefato **separado**; sem ele, o `KieHelper` falha em
  runtime, não na compilação.
- **Não usar `kie-spring`** — é de uma geração anterior do Spring. O `KieBase`
  é montado à mão num `@Bean` (`DroolsConfig`), o que também é a via que a
  fatia de regras dinâmicas vai usar quando o DRL vier do banco.
- `drools-compiler` traz `xstream` em escopo compile sem versão gerenciada pelo
  Boot; o root pom fixa a versão.

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
uma ponte de protocolo sem estado). Unificar é trabalho da fatia que liga a
tela de administração ao roteamento.

## Estado atual

**Fatia 1 — construída.** Integrador + Orquestrador + Parametrizador, fluxo
completo do documento chegando até a resposta consolidada voltando, com
caminhos de timeout e resposta atrasada cobertos por teste.

### Próximas fatias

2. Telas Swing (cadastro de `TipoDocumento` + histórico de execuções), JDBC
   direto no MySQL. Em paralelo: Dockerfile de cada serviço e inclusão deles no
   compose, agora que o fluxo está estável.
3. Regras dinâmicas: DRL armazenado no MySQL, editor na tela desktop, `KieBase`
   recarregado em runtime.
4. Segundo branch do Orquestrador: `ml-analise` (stack a definir), exercitando
   consolidação real de duas partes.
5. Múltiplos tipos de documento: registro dinâmico de listeners no Integrador a
   partir da parametrização.
6. Troca do broker stand-in pelo IBM MQ real (`MQConnectionFactory` no lugar do
   bean `connectionFactoryExterna`).

## Verificação ainda pendente

Nada disso foi executado contra infraestrutura real — o ambiente de
desenvolvimento não tinha container runtime nem JDK 8. Falta:

- Rodar a suíte num **JDK 8 real** (aqui rodou em 17 e 21, compilando com `--release 8`).
- `podman-compose up -d` e o fluxo ponta a ponta com MySQL e ActiveMQ de verdade,
  publicando um documento na fila `DEV.QUEUE.PEDIDO.IN` do broker externo
  (porta 61617) e conferindo a resposta em `DEV.QUEUE.PEDIDO.OUT`.
- Confirmar a compatibilidade cliente 5.16.7 ↔ broker 5.17.7.
