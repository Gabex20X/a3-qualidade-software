# AGENTS.md — memoria do projeto para o agente de IA

## O projeto
API REST de gestao financeira pessoal para idosos e jovens (A3 de Garantia da Qualidade de Software, 2026/2).
O foco da A3 e **testar** o sistema: plano de teste, casos de teste, testes automatizados, testes de API, CI e relatorio.
O tema veio do pre-projeto do grupo (Way Hub); interface grafica, voz, foto de recibo e Open Finance estao **fora de escopo**.

## Stack
- Java 21, Maven, HttpServer do JDK, SQLite (sqlite-jdbc), Gson
- Testes: JUnit 5 (+ params), Mockito, JaCoCo (piso de cobertura em `cobertura.minima` no `pom.xml`)
- Docker Compose: `app` (sistema) e `testes` (suite); `newman` entra na Fase 4
- CI: `.github/workflows/ci.yml`

## Comandos
- `docker compose up --build` — sobe a API em http://localhost:8080
- `docker compose run --rm testes` — roda a suite com cobertura
- `mvn -B verify` — o mesmo, sem Docker (precisa de JDK 21 e Maven)

## Codigo (`src/main/java/br/edu/financas/`)
- `Regras` — regras puras RN-01..RN-06 (sem banco, sem relogio do sistema)
- `Servico` — casos de uso; recebe `Repositorio` e `Clock` por construtor
- `Repositorio` / `RepositorioSqlite` — persistencia
- `Api` — rotas HTTP; usuario identificado pelo cabecalho `X-Usuario-Id`
- `App` — main; variaveis `PORT`, `DB_URL`, `RELOGIO_FIXO`

## Regras de negocio
| ID | Regra |
|---|---|
| RN-01 | Idade >= 60 -> IDOSO; 15 a 29 -> JOVEM; resto -> PADRAO; idade fora de 0..130 -> 400 |
| RN-02 | Gasto do mes < 80% do orcamento -> OK; 80% a 100% -> ATENCAO; > 100% -> ESTOURADO |
| RN-03 | Conta paga -> PAGA; depois do vencimento -> ATRASADA; 0 a 3 dias -> A_VENCER; senao EM_DIA |
| RN-04 | Despesa de IDOSO > R$ 500,00 -> alertaFamiliar = true |
| RN-05 | Familiar vinculado so le (escrita -> 403); no maximo 2 familiares por idoso (3o -> 409) |
| RN-06 | Meta: percentual arredondado para baixo, teto 100; CONCLUIDA quando atual >= alvo |
| Valores | Monetarios entre 0,01 e 1.000.000,00, no maximo 2 casas decimais |

## Convencoes para o agente
- Testes no padrao AAA, com comentarios `// Arrange`, `// Act`, `// Assert`.
- Cada teste automatizado cita o caso de teste que automatiza (`ct07_...`) a partir da Fase 3.
- Valores-limite testados dos dois lados do limite.
- Teste escrito pelo agente so e aceito depois de falhar com o codigo quebrado de proposito.
- Nada de dados pessoais reais (CPF, e-mail, cartao) nem credenciais em codigo, testes ou prompts.
- O agente **nao** faz `git commit` nem `git push`; quem publica e o grupo.
- Mensagens de commit em Conventional Commits (`feat:`, `test:`, `docs:`, `ci:`, `chore:`).
