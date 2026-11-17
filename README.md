# Finanças para Idosos e Jovens — A3 de Garantia da Qualidade de Software

API REST de gestão financeira pessoal com regras adaptadas ao perfil do usuário (idoso, jovem ou padrão)
e acompanhamento por familiar. O tema vem do pré-projeto do grupo; aqui o foco é **testar** o sistema.

> Integrantes: _a preencher (nome e RA)_
> Execução verde do CI: _link a preencher_

## Como rodar (só precisa do Docker)

```bash
docker compose up --build          # sobe a API em http://localhost:8080
docker compose run --rm testes     # roda a suíte de testes com cobertura
```

Sem Docker (JDK 21 + Maven): `mvn -B verify` e `java -jar target/financas-api.jar`.

Variáveis: `PORT` (8080), `DB_URL` (`jdbc:sqlite:dados/financas.db`) e `RELOGIO_FIXO` (ex.: `2026-12-03`, congela a data).

## Regras de negócio

| ID | Regra | Limites |
|---|---|---|
| RN-01 | Perfil por idade | 15–29 JOVEM · 60+ IDOSO · demais PADRÃO · fora de 0–130 recusado |
| RN-02 | Alerta de orçamento do mês | < 80% OK · 80%–100% ATENÇÃO · > 100% ESTOURADO |
| RN-03 | Situação da conta | vencida ATRASADA · 0–3 dias A_VENCER · paga PAGA |
| RN-04 | Gasto alto de idoso | despesa > R$ 500,00 gera `alertaFamiliar` |
| RN-05 | Familiar | só leitura (escrita → 403) · máximo 2 por idoso (3º → 409) |
| RN-06 | Metas de economia | percentual com teto 100 · CONCLUÍDA ao atingir o alvo |

## Endpoints

O usuário é identificado pelo cabeçalho `X-Usuario-Id` (sem ele → 401).

| Método | Rota | Descrição |
|---|---|---|
| POST | `/usuarios` | Cadastra usuário (`nome`, `idade`) |
| GET | `/usuarios/{id}` | Consulta usuário |
| POST | `/usuarios/{id}/transacoes` | Cria receita/despesa |
| GET | `/usuarios/{id}/transacoes` | Lista transações (dono ou familiar) |
| GET / PUT / DELETE | `/transacoes/{id}` | Consulta, altera, exclui |
| PUT | `/usuarios/{id}/orcamentos` | Define orçamento (`categoria`, `limite`) |
| GET | `/usuarios/{id}/orcamentos` | Status dos orçamentos no mês |
| GET | `/usuarios/{id}/contas` | Despesas com vencimento e situação |
| POST | `/usuarios/{id}/familiares` | Vincula familiar (`familiarId`) |
| POST / GET | `/usuarios/{id}/metas` | Cria / lista metas |
| POST | `/metas/{id}/aportes` | Aporta valor na meta |

## Estrutura

```
src/main/java/br/edu/financas/   código do sistema
src/test/java/br/edu/financas/   testes JUnit 5
.github/workflows/ci.yml          pipeline
plano-de-teste.md · casos-de-teste.md · relatorio-testes.md   (próximas fases)
postman/ · scripts/                                           (próximas fases)
```

## Uso de IA

- **Ferramenta e modelo:** Claude Code (Anthropic), modelo Claude Opus 5.5.
- **Memória do projeto:** [AGENTS.md](AGENTS.md).

### Registros

| # | Artefato | Pedido (resumo) | O que o agente errou ou omitiu | O que o grupo fez |
|---|---|---|---|---|
| 1 | Código do sistema | Gerar o esqueleto da API Java a partir do pré-projeto | Gerou ~1.270 linhas no sistema, acima da faixa de 300–700 sugerida no enunciado | _a preencher após a revisão do grupo_ |

_Os demais registros são adicionados conforme o projeto avança. Mínimo: 5 linhas, 4 tipos de artefato e 2 erros reais._

### Testes do agente quebrados de propósito

_A preencher na Fase 3 (mutação manual)._

### Reflexão

_A preencher no fim do projeto._
