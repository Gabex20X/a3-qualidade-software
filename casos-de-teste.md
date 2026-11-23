# Casos de Teste — Finanças para Idosos e Jovens (API)

Os requisitos (RF), regras (RN, RV) e requisitos não funcionais (RNF) citados aqui estão definidos no [plano de teste](plano-de-teste.md).

## Convenções

- **Data de referência ("hoje"):** 03/12/2026, fixada pelo relógio injetado (`Clock.fixed` nos testes JUnit e `RELOGIO_FIXO` na API).
- **Identificação:** as requisições autenticadas enviam o cabeçalho `X-Usuario-Id: <id>`. A notação "como U" significa enviar o id do usuário U.
- **Usuários de apoio** (dados fictícios):
  - **I**: idoso, `{"nome":"Dona Maria","idade":60}`
  - **U**: adulto padrão, `{"nome":"Carlos","idade":40}`
  - **J**: jovem, `{"nome":"Neto","idade":20}`
  - **F, F1, F2, F3**: possíveis familiares, `{"nome":"Familiar","idade":35}`
  - **X**: terceiro, sem vínculo nenhum
- **Nível previsto:** U = unitário, I = integração, A = API (Newman). Um caso pode ser automatizado em mais de um nível.
- **Prioridade:** vem dos riscos do plano (seção 7). Os casos de prioridade Alta são obrigatórios para a liberação.
- **Automação:** cada teste automatizado cita o caso no nome, por exemplo `ct06_valorNosLimites`.

## Resumo

| ID | Título | Requisito | Técnica | Prioridade | Nível |
|---|---|---|---|---|---|
| CT-01 | Perfil IDOSO no limite de 60 anos | RF-01, RN-01 | Valor-limite | Alta | U, I, A |
| CT-02 | Perfil JOVEM nos limites de 15 e 29 anos | RF-01, RN-01 | Valor-limite | Média | U, A |
| CT-03 | Idade fora do intervalo 0..130 | RF-01, RN-01 | Valor-limite | Média | U, A |
| CT-04 | Cadastro de usuário com dados inválidos | RF-01 | Partição de equivalência | Baixa | U, A |
| CT-05 | Criar e consultar despesa válida | RF-02 | Partição de equivalência | Alta | U, I, A |
| CT-06 | Valor da transação nos limites | RF-02, RV-01 | Valor-limite | Alta | U, A |
| CT-07 | Valor com casas decimais ou sinal inválidos | RF-02, RV-01 | Partição de equivalência | Média | U, A |
| CT-08 | Data da transação: hoje e amanhã | RF-02, RV-02 | Valor-limite | Média | U, A |
| CT-09 | Combinação tipo × vencimento | RF-02, RV-02 | Tabela de decisão | Média | U |
| CT-10 | Ciclo de vida da transação | RF-02 | Transição de estados | Alta | I, A |
| CT-11 | Status do orçamento nos limites de 80% e 100% | RF-03, RN-02 | Valor-limite | Alta | U, I, A |
| CT-12 | Só despesas do mês e da categoria contam no orçamento | RF-03, RN-02 | Partição de equivalência | Média | U, I |
| CT-13 | Situação da conta nos limites de 3 dias e do vencimento | RF-04, RN-03 | Valor-limite | Alta | U, I, A |
| CT-14 | Conta paga × data do vencimento | RF-04, RN-03 | Tabela de decisão | Média | U |
| CT-15 | Alerta de gasto de idoso no limite de R$ 500,00 | RF-05, RN-04 | Valor-limite | Alta | U, I, A |
| CT-16 | Alerta: perfil × tipo × valor | RF-05, RN-04 | Tabela de decisão | Média | U |
| CT-17 | Permissões: papel do usuário × operação | RF-06, RF-08, RN-05 | Tabela de decisão | Alta | U, I, A |
| CT-18 | Limite de 2 familiares por idoso | RF-06, RN-05 | Valor-limite | Alta | U, I, A |
| CT-19 | Vínculo familiar inválido | RF-06 | Partição de equivalência | Média | U, A |
| CT-20 | Identificação pelo cabeçalho X-Usuario-Id | RF-08 | Partição de equivalência | Alta | I, A |
| CT-21 | Progresso da meta nos limites de 100% | RF-07, RN-06 | Valor-limite | Média | U, I, A |
| CT-22 | Entradas malformadas nunca geram erro 500 | RNF-02 | Suposição de erro | Média | I, A |
| CT-23 | Tempo de resposta da listagem | RNF-01 | Teste de desempenho | Baixa | A |
| CT-24 | Precisão dos valores monetários | RNF-03, RV-01 | Valor-limite | Média | I |

---

## CT-01 — Perfil IDOSO no limite de 60 anos

| Campo | Conteúdo |
|---|---|
| Requisito | RF-01, RN-01 |
| Técnica | Análise de valor-limite (59 \| 60) |
| Prioridade | Alta: o perfil IDOSO habilita o alerta (RN-04) e o vínculo familiar (RN-05) |
| Pré-condição | API no ar |
| Dados | idade = 59; idade = 60 |
| Passos | 1. `POST /usuarios` com `{"nome":"Teste","idade":59}` · 2. Repetir com `idade` 60 · 3. `GET /usuarios/{id}` do segundo usuário |
| Resultado esperado | 1. 201, `perfil = "PADRAO"` · 2. 201, `perfil = "IDOSO"` · 3. 200, mesmo `id`, `nome`, `idade` 60 e `perfil "IDOSO"` |

## CT-02 — Perfil JOVEM nos limites de 15 e 29 anos

| Campo | Conteúdo |
|---|---|
| Requisito | RF-01, RN-01 |
| Técnica | Análise de valor-limite (14 \| 15 e 29 \| 30) |
| Prioridade | Média |
| Pré-condição | API no ar |
| Dados | idade = 14, 15, 29, 30 |
| Passos | `POST /usuarios` uma vez para cada idade |
| Resultado esperado | 201 em todos; perfis `PADRAO`, `JOVEM`, `JOVEM` e `PADRAO`, nesta ordem |

## CT-03 — Idade fora do intervalo 0..130

| Campo | Conteúdo |
|---|---|
| Requisito | RF-01, RN-01 |
| Técnica | Análise de valor-limite (−1 \| 0 e 130 \| 131) |
| Prioridade | Média |
| Pré-condição | API no ar |
| Dados | idade = −1, 0, 130, 131 |
| Passos | `POST /usuarios` uma vez para cada idade |
| Resultado esperado | −1 → 400 com `{"erro":"idade deve estar entre 0 e 130"}` · 0 → 201 `PADRAO` · 130 → 201 `IDOSO` · 131 → 400 com a mesma mensagem. Nas recusas, nenhum usuário é criado |

## CT-04 — Cadastro de usuário com dados inválidos

| Campo | Conteúdo |
|---|---|
| Requisito | RF-01 |
| Técnica | Partição de equivalência (classes inválidas de nome e idade) |
| Prioridade | Baixa |
| Pré-condição | API no ar |
| Dados | a) sem `nome` · b) `nome` = `"   "` · c) sem `idade` · d) `idade` = `"sessenta"` (texto) · e) `idade` = 60.5 |
| Passos | `POST /usuarios` com cada corpo; depois `GET /usuarios/999999` |
| Resultado esperado | a) a e): 400 com campo `erro` · `GET` de usuário inexistente: 404 |

## CT-05 — Criar e consultar despesa válida

| Campo | Conteúdo |
|---|---|
| Requisito | RF-02 |
| Técnica | Partição de equivalência (classe válida, caminho feliz) |
| Prioridade | Alta |
| Pré-condição | Usuário U cadastrado |
| Dados | `{"tipo":"DESPESA","valor":120.50,"categoria":"Mercado","descricao":"compras"}` |
| Passos | 1. Como U, `POST /usuarios/U/transacoes` · 2. Como U, `GET /transacoes/{id}` · 3. Como U, `GET /usuarios/U/transacoes` |
| Resultado esperado | 1. 201; `id` > 0; `tipo "DESPESA"`; `valor` 120.50; `categoria "mercado"` (normalizada); `data "2026-12-03"` (hoje, por omissão); `paga false`; `alertaFamiliar false` · 2. 200 com os mesmos campos · 3. 200, lista com 1 item |

## CT-06 — Valor da transação nos limites

| Campo | Conteúdo |
|---|---|
| Requisito | RF-02, RV-01 |
| Técnica | Análise de valor-limite (0,00 \| 0,01 e 1.000.000,00 \| 1.000.000,01) |
| Prioridade | Alta |
| Pré-condição | Usuário U cadastrado |
| Dados | `valor` = 0.00, 0.01, 1000000.00, 1000000.01 (DESPESA, categoria "teste") |
| Passos | Como U, `POST /usuarios/U/transacoes` uma vez para cada valor |
| Resultado esperado | 0.00 → 400 · 0.01 → 201 · 1000000.00 → 201 · 1000000.01 → 400. Nas recusas, `erro = "valor deve estar entre 0.01 e 1000000.00"` |

## CT-07 — Valor com casas decimais ou sinal inválidos

| Campo | Conteúdo |
|---|---|
| Requisito | RF-02, RV-01 |
| Técnica | Partição de equivalência |
| Prioridade | Média |
| Pré-condição | Usuário U cadastrado |
| Dados | a) 10.001 · b) 10.10 · c) −5.00 · d) sem `valor` |
| Passos | Como U, `POST /usuarios/U/transacoes` com cada valor |
| Resultado esperado | a) 400, `erro = "valor deve ter no maximo 2 casas decimais"` · b) 201 (zero à direita é aceito) · c) 400 · d) 400, `erro = "valor e obrigatorio"` |

## CT-08 — Data da transação: hoje e amanhã

| Campo | Conteúdo |
|---|---|
| Requisito | RF-02, RV-02 |
| Técnica | Análise de valor-limite (hoje \| hoje + 1) |
| Prioridade | Média |
| Pré-condição | Usuário U cadastrado; hoje = 03/12/2026 |
| Dados | a) `data` = "2026-12-03" · b) "2026-12-04" · c) "03/12/2026" (formato errado) |
| Passos | Como U, `POST /usuarios/U/transacoes` (DESPESA, 10.00) com cada data |
| Resultado esperado | a) 201 · b) 400, `erro = "data nao pode ser futura"` · c) 400, `erro = "data deve estar no formato AAAA-MM-DD"` |

## CT-09 — Combinação tipo × vencimento

| Campo | Conteúdo |
|---|---|
| Requisito | RF-02, RV-02 |
| Técnica | Tabela de decisão |
| Prioridade | Média |
| Pré-condição | Usuário U cadastrado |
| Passos | Como U, `POST /usuarios/U/transacoes` (valor 10.00) para cada regra da tabela |

| Regra | `tipo` | `vencimento` informado? | Resultado esperado |
|---|---|---|---|
| 1 | DESPESA | não | 201 |
| 2 | DESPESA | sim ("2026-12-10") | 201, com `vencimento` gravado |
| 3 | RECEITA | não | 201 |
| 4 | RECEITA | sim | 400, `erro = "vencimento so se aplica a despesas"` |
| 5 | "TRANSFERENCIA" | não | 400, `erro = "tipo deve ser RECEITA ou DESPESA"` |
| 6 | ausente | não | 400 |

## CT-10 — Ciclo de vida da transação

| Campo | Conteúdo |
|---|---|
| Requisito | RF-02 |
| Técnica | Transição de estados: inexistente → criada → alterada → excluída |
| Prioridade | Alta |
| Pré-condição | Usuário U com a transação T criada (DESPESA 50.00, "mercado") |
| Passos | 1. Como U, `PUT /transacoes/T` com `{"tipo":"DESPESA","valor":80.00,"categoria":"lazer"}` · 2. `GET /transacoes/T` · 3. `DELETE /transacoes/T` · 4. `GET /transacoes/T` · 5. `DELETE /transacoes/T` · 6. `PUT /transacoes/T` |
| Resultado esperado | 1. 200, `valor` 80.00, `categoria "lazer"` · 2. 200, reflete a alteração · 3. 204, sem corpo · 4. 404 · 5. 404 · 6. 404 (uma transação excluída não volta a nenhum estado) |

## CT-11 — Status do orçamento nos limites de 80% e 100%

| Campo | Conteúdo |
|---|---|
| Requisito | RF-03, RN-02 |
| Técnica | Análise de valor-limite (79,99 \| 80,00 e 100,00 \| 100,01) |
| Prioridade | Alta |
| Pré-condição | Para cada cenário, um usuário U novo com orçamento `PUT /usuarios/U/orcamentos {"categoria":"saude","limite":100.00}` |
| Dados | Despesas de "saude" em 12/2026 somando: 79.99 · 80.00 · 100.00 · 100.01 |
| Passos | Lançar as despesas e, como U, `GET /usuarios/U/orcamentos` |
| Resultado esperado | `status` = `OK`, `ATENCAO`, `ATENCAO`, `ESTOURADO`, respectivamente; `gasto` igual à soma lançada; `limite` 100.00 |

## CT-12 — Só despesas do mês e da categoria contam no orçamento

| Campo | Conteúdo |
|---|---|
| Requisito | RF-03, RN-02 |
| Técnica | Partição de equivalência (transações que entram e que não entram na soma) |
| Prioridade | Média |
| Pré-condição | Usuário U com orçamento "saude" de 100.00 |
| Dados | a) DESPESA "saude" 50.00 em 2026-12-01 (entra) · b) DESPESA "saude" 60.00 em 2026-11-30 (mês anterior) · c) RECEITA "saude" 500.00 (é receita) · d) DESPESA "lazer" 90.00 (outra categoria) |
| Passos | Lançar a) a d) e, como U, `GET /usuarios/U/orcamentos`; depois `PUT /usuarios/U/orcamentos` com `limite` 0 |
| Resultado esperado | `gasto` = 50.00 e `status` = `OK` (só a) conta) · limite 0 → 400 |

## CT-13 — Situação da conta nos limites de 3 dias e do vencimento

| Campo | Conteúdo |
|---|---|
| Requisito | RF-04, RN-03 |
| Técnica | Análise de valor-limite (+4 \| +3 dias e 0 \| −1 dia) |
| Prioridade | Alta |
| Pré-condição | Usuário U; hoje = 03/12/2026 |
| Dados | Despesas não pagas com `vencimento` 2026-12-07 (+4), 2026-12-06 (+3), 2026-12-03 (0), 2026-12-02 (−1) |
| Passos | Lançar as despesas e, como U, `GET /usuarios/U/contas` |
| Resultado esperado | 200, lista ordenada por vencimento: 12-02 `ATRASADA`, 12-03 `A_VENCER`, 12-06 `A_VENCER`, 12-07 `EM_DIA` |

## CT-14 — Conta paga × data do vencimento

| Campo | Conteúdo |
|---|---|
| Requisito | RF-04, RN-03 |
| Técnica | Tabela de decisão |
| Prioridade | Média |
| Pré-condição | Usuário U; hoje = 03/12/2026 |
| Passos | Lançar uma despesa para cada regra e, como U, `GET /usuarios/U/contas` |

| Regra | `paga` | Vencimento | Resultado esperado |
|---|---|---|---|
| 1 | true | 10 dias atrás | `PAGA` |
| 2 | true | daqui a 10 dias | `PAGA` |
| 3 | false | 10 dias atrás | `ATRASADA` |
| 4 | false | daqui a 10 dias | `EM_DIA` |
| 5 | — | sem vencimento | não aparece em `/contas` |

## CT-15 — Alerta de gasto de idoso no limite de R$ 500,00

| Campo | Conteúdo |
|---|---|
| Requisito | RF-05, RN-04 |
| Técnica | Análise de valor-limite (500,00 \| 500,01) |
| Prioridade | Alta: é o principal diferencial do produto para o público idoso |
| Pré-condição | Usuário I (idoso, 60 anos) cadastrado |
| Dados | DESPESA "saude" com `valor` 500.00 e 500.01 |
| Passos | Como I, `POST /usuarios/I/transacoes` com cada valor |
| Resultado esperado | 500.00 → 201, `alertaFamiliar false` · 500.01 → 201, `alertaFamiliar true` |

## CT-16 — Alerta: perfil × tipo × valor

| Campo | Conteúdo |
|---|---|
| Requisito | RF-05, RN-04 |
| Técnica | Tabela de decisão |
| Prioridade | Média |
| Pré-condição | Usuários I (idoso), J (jovem) e U (padrão) |
| Passos | Cada usuário lança a própria transação conforme a tabela |

| Regra | Perfil | Tipo | Valor > 500,00? | `alertaFamiliar` |
|---|---|---|---|---|
| 1 | IDOSO | DESPESA | sim (9000.00) | true |
| 2 | IDOSO | DESPESA | não (100.00) | false |
| 3 | IDOSO | RECEITA | sim (9000.00) | false |
| 4 | JOVEM | DESPESA | sim (9000.00) | false |
| 5 | PADRAO | DESPESA | sim (9000.00) | false |

## CT-17 — Permissões: papel do usuário × operação

| Campo | Conteúdo |
|---|---|
| Requisito | RF-06, RF-08, RN-05 |
| Técnica | Tabela de decisão |
| Prioridade | Alta: vazamento de permissão é o risco R3 |
| Pré-condição | Idoso I com o familiar F vinculado (`POST /usuarios/I/familiares {"familiarId":F}` feito por I); terceiro X sem vínculo; transação T e meta M de I |
| Passos | Executar cada operação sobre os dados de I, como I, como F e como X |

| Operação | Como I (dono) | Como F (familiar) | Como X (terceiro) |
|---|---|---|---|
| `GET /usuarios/I/transacoes` | 200 | 200 | 403 |
| `GET /transacoes/T` | 200 | 200 | 403 |
| `GET /usuarios/I/contas`, `/orcamentos`, `/metas` | 200 | 200 | 403 |
| `POST /usuarios/I/transacoes` | 201 | 403 | 403 |
| `PUT /transacoes/T` | 200 | 403 | 403 |
| `DELETE /transacoes/T` (por último) | 204 | 403 | 403 |
| `PUT /usuarios/I/orcamentos` | 200 | 403 | 403 |
| `POST /usuarios/I/metas` e `POST /metas/M/aportes` | 201 / 200 | 403 | 403 |

Mensagem do 403 para F: `"familiar tem acesso somente leitura"`. As tentativas recusadas não alteram nada (confere-se com um `GET` como I).

## CT-18 — Limite de 2 familiares por idoso

| Campo | Conteúdo |
|---|---|
| Requisito | RF-06, RN-05 |
| Técnica | Análise de valor-limite (2 \| 3 vínculos) |
| Prioridade | Alta |
| Pré-condição | Idoso I e usuários F1, F2, F3 cadastrados |
| Passos | Como I, `POST /usuarios/I/familiares`: 1. F1 · 2. F2 · 3. F3 · 4. F1 de novo |
| Resultado esperado | 1. 201 · 2. 201 · 3. 409, `erro = "limite de 2 familiares atingido"` · 4. 409, `erro = "familiar ja vinculado"` · Depois disso, F1 e F2 leem os dados de I e F3 recebe 403 |

## CT-19 — Vínculo familiar inválido

| Campo | Conteúdo |
|---|---|
| Requisito | RF-06 |
| Técnica | Partição de equivalência (classes inválidas) |
| Prioridade | Média |
| Pré-condição | Usuários I (idoso), J (jovem) e F cadastrados |
| Dados e passos | a) Como J, `POST /usuarios/J/familiares {"familiarId":F}` · b) Como I, `{"familiarId":I}` · c) Como I, `{"familiarId":999999}` · d) Como I, corpo `{}` · e) Como F, `POST /usuarios/I/familiares {"familiarId":F}` |
| Resultado esperado | a) 400, `"apenas usuarios idosos podem vincular familiares"` · b) 400, `"usuario nao pode ser familiar de si mesmo"` · c) 404 · d) 400, `"familiarId e obrigatorio"` · e) 403 (só o próprio idoso cria vínculos) |

## CT-20 — Identificação pelo cabeçalho X-Usuario-Id

| Campo | Conteúdo |
|---|---|
| Requisito | RF-08 |
| Técnica | Partição de equivalência (cabeçalho ausente, inválido, de usuário inexistente, válido) |
| Prioridade | Alta |
| Pré-condição | Usuário U cadastrado |
| Dados e passos | `GET /usuarios/U/transacoes`: a) sem cabeçalho · b) `X-Usuario-Id: abc` · c) `X-Usuario-Id: 999999` · d) como U · e) Como U, `GET /usuarios/999999/transacoes` · f) Sem cabeçalho, `POST /usuarios` e `GET /usuarios/U` |
| Resultado esperado | a) 401, `"informe o cabecalho X-Usuario-Id"` · b) 401, `"cabecalho X-Usuario-Id invalido"` · c) 401 · d) 200 · e) 404 (dono inexistente) · f) 201 e 200 (rotas públicas) |

## CT-21 — Progresso da meta nos limites de 100%

| Campo | Conteúdo |
|---|---|
| Requisito | RF-07, RN-06 |
| Técnica | Análise de valor-limite (99,99 \| 100,00 do alvo e acima do alvo) |
| Prioridade | Média |
| Pré-condição | Usuário U com a meta M criada: `POST /usuarios/U/metas {"descricao":"Reserva","valorAlvo":100.00}` |
| Passos | Como U, `POST /metas/M/aportes`: 1. 99.99 · 2. 0.01 · 3. 50.00; depois 4. `POST /usuarios/U/metas` com `valorAlvo` 0 · 5. aporte na meta 999999 |
| Resultado esperado | Na criação: `percentual` 0, `EM_ANDAMENTO` · 1. `percentual` 99, `EM_ANDAMENTO` · 2. `percentual` 100, `CONCLUIDA` · 3. `valorAtual` 150.00, `percentual` 100 (teto), `CONCLUIDA` · 4. 400 · 5. 404 |

## CT-22 — Entradas malformadas nunca geram erro 500

| Campo | Conteúdo |
|---|---|
| Requisito | RNF-02 |
| Técnica | Suposição de erro (error guessing) |
| Prioridade | Média |
| Pré-condição | Usuário U cadastrado |
| Dados e passos | a) `POST /usuarios` com corpo `{nao json` · b) corpo `[1,2]` · c) Como U, transação com `"valor":"abc"` · d) Como U, transação com `"paga":"sim"` · e) `PATCH /usuarios/U` · f) `GET /rota-inexistente` · g) `GET /transacoes/99999999999999999999` (20 dígitos) |
| Resultado esperado | a) 400 `"JSON invalido"` · b) 400 `"corpo deve ser um objeto JSON"` · c) 400 `"valor deve ser numerico"` · d) 400 `"paga deve ser true ou false"` · e) 405 · f) 404 · g) 404. Todas as respostas são JSON com o campo `erro`; nenhuma é 500 |

## CT-23 — Tempo de resposta da listagem

| Campo | Conteúdo |
|---|---|
| Requisito | RNF-01 |
| Técnica | Teste de desempenho (tempo de resposta sob carga nominal) |
| Prioridade | Baixa |
| Pré-condição | Usuário U com 100 transações cadastradas; API em contêiner local |
| Passos | Como U, `GET /usuarios/U/transacoes` |
| Resultado esperado | 200, lista com 100 itens, `responseTime` < 300 ms |

## CT-24 — Precisão dos valores monetários

| Campo | Conteúdo |
|---|---|
| Requisito | RNF-03, RV-01 |
| Técnica | Análise de valor-limite (menor e maior valor aceitos) + suposição de erro (soma de ponto flutuante) |
| Prioridade | Média: risco R4 |
| Pré-condição | Usuário U com orçamento "cafe" de 1.00; banco SQLite real |
| Dados | Transações 0.01 e 1000000.00; despesas "cafe" de 0.10 e 0.20 |
| Passos | 1. Gravar as transações e lê-las de novo do banco · 2. Consultar `GET /usuarios/U/orcamentos` |
| Resultado esperado | 1. Valores lidos exatamente iguais aos gravados (0.01 e 1000000.00) · 2. `gasto` = 0.30 exatamente (e não 0.30000000000000004) |

---

## Matriz de rastreabilidade

| Requisito | Casos |
|---|---|
| RF-01 | CT-01, CT-02, CT-03, CT-04 |
| RF-02 | CT-05, CT-06, CT-07, CT-08, CT-09, CT-10 |
| RF-03 | CT-11, CT-12 |
| RF-04 | CT-13, CT-14 |
| RF-05 | CT-15, CT-16 |
| RF-06 | CT-17, CT-18, CT-19 |
| RF-07 | CT-21 |
| RF-08 | CT-17, CT-20 |
| RN-01 | CT-01, CT-02, CT-03 |
| RN-02 | CT-11, CT-12 |
| RN-03 | CT-13, CT-14 |
| RN-04 | CT-15, CT-16 |
| RN-05 | CT-17, CT-18 |
| RN-06 | CT-21 |
| RV-01 | CT-06, CT-07, CT-24 |
| RV-02 | CT-08, CT-09 |
| RNF-01 | CT-23 |
| RNF-02 | CT-22 |
| RNF-03 | CT-24 |
| RNF-04 | Verificado pelo job `docker` do CI e pela execução em cópia limpa (não é um caso de teste) |

Todos os requisitos e regras do plano têm pelo menos um caso. A partir da Fase 3, um script vai conferir se cada caso tem ao menos um teste automatizado com o nome `ctXX_...`.
