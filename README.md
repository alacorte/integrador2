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

Este repositorio usa [spec-kit](https://github.com/github/spec-kit), instalado
como skills do Claude Code em `.claude/skills/speckit-*`. Fluxo recomendado
para qualquer feature nova:

1. `/speckit-constitution` — definir principios do projeto (rodar uma vez).
2. `/speckit-specify` — descrever a feature em linguagem natural; gera uma
   spec em `specs/<numero>-<nome>/spec.md`.
3. `/speckit-clarify` (opcional) — Claude faz perguntas para reduzir ambiguidade
   antes do plano.
4. `/speckit-plan` — gera o plano tecnico de implementacao a partir da spec.
5. `/speckit-tasks` — quebra o plano em tarefas acionaveis.
6. `/speckit-analyze` (opcional) — checa consistencia entre spec/plano/tarefas.
7. `/speckit-implement` — executa as tarefas.

Os templates ficam em `.specify/templates/` e os principios do projeto em
`.specify/memory/constitution.md`.
