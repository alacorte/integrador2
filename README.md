# integrador2

Monorepo Maven multi-modulo com tres projetos:

| Modulo | Java | Descricao |
|---|---|---|
| `model-commons` | 8 | Entidades de dominio e utilitarios compartilhados. Consumido pelos outros dois modulos. |
| `backend-springboot` | 8 (Spring Boot 2.7 LTS) | API REST. |
| `frontend-desktop` | 8 | Interface desktop com Swing e JavaFX. |

## Build

```bash
mvn -q -DskipTests install   # builda todos os modulos, na ordem correta de dependencia
mvn -q test                  # roda os testes de todos os modulos
```

Para rodar um modulo especifico:

```bash
mvn -pl backend-springboot -am spring-boot:run
mvn -pl frontend-desktop -am exec:java -Dexec.mainClass=br.com.integrador2.frontend.swing.SwingApp
```

### JavaFX

O modulo `frontend-desktop` inclui uma tela Swing e uma tela JavaFX de exemplo.
JavaFX nao tem artefato Maven compativel com Java 8 (os artefatos
`org.openjfx:javafx-*` exigem Java 11+), entao rodar `FxApp` requer uma
distribuicao de JDK 8 que embuta o JavaFX no runtime, por exemplo:

- Azul Zulu 8 FX
- BellSoft Liberica Full JDK 8
- Oracle JDK 8u (licenca proprietaria)

O Swing (`SwingApp`) roda em qualquer JDK 8 sem nenhuma dependencia extra.

## Spec-Driven Development

Fluxo leve, usando o Plan Mode nativo do Claude Code + specs em Markdown:

1. Escrever a spec da feature em `specs/<nome-da-feature>.md`, a partir do
   template em `specs/TEMPLATE.md` (objetivo, escopo, entidades/contratos,
   regras de negocio, criterios de aceite).
2. No Claude Code, pedir para entrar em **Plan Mode** e ler a spec (ex: "leia
   specs/cadastro-produto.md e entre em plan mode"). O Claude explora o
   codigo existente e propoe um plano de implementacao passo a passo.
3. Revisar/ajustar o plano antes de aprovar.
4. Aprovar o plano; a implementacao acontece so depois disso.

Sem CLI externo, sem artefatos obrigatorios alem da propria spec. E o ponto
de partida recomendado para quem esta comecando com Claude Code; um fluxo
mais formal (tipo spec-kit) pode fazer sentido depois, se o processo exigir
mais rigor entre spec/plano/tarefas.
