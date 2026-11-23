# Plano de Teste — Finanças para Idosos e Jovens (API)

Baseado na estrutura da IEEE 829 / ISO/IEC/IEEE 29119-3, adaptada ao tamanho do projeto.

## 1. Identificação

| Item | Valor |
|---|---|
| Sistema | Finanças para Idosos e Jovens — API REST de gestão financeira pessoal |
| Origem | Pré-projeto do grupo (Way Hub 2026/2), reduzido ao núcleo de regras de negócio |
| Versão testada | 0.1.0 (a versão final é a da tag de entrega, `v1.0.0`) |
| Repositório | https://github.com/Gabex20X/a3-qualidade-software |
| Responsáveis | _a preencher (nome e RA de cada integrante)_ |
| Documentos relacionados | [casos-de-teste.md](casos-de-teste.md), [relatorio-testes.md](relatorio-testes.md), [README.md](README.md) |

## 2. Escopo

### 2.1 Dentro do escopo

A API REST e suas regras de negócio, em três níveis (unitário, integração e API):

- cadastro de usuários e perfil por idade;
- CRUD de transações (receitas e despesas);
- orçamentos por categoria e seu status no mês;
- contas com vencimento e sua situação;
- alerta de gasto alto de idoso;
- vínculo de familiar com acesso somente leitura;
- metas de economia e aportes;
- controle de acesso pelo cabeçalho `X-Usuario-Id`;
- validação de entrada e respostas de erro (400, 401, 403, 404, 405 e 409);
- persistência em SQLite;
- execução empacotada em Docker Compose.

### 2.2 Fora do escopo

| Item | Motivo |
|---|---|
| Interface gráfica (telas da Questão 7 do pré-projeto: dashboard, gráficos, configurações de acessibilidade) | A A3 avalia os testes da lógica, e a lógica está toda na API. Uma interface aumentaria o esforço de programação sem acrescentar regras testáveis. Teste de interface com Selenium é opcional no enunciado. |
| Leitura por voz, comando de voz e foto do recibo (OCR) | Dependem de serviços externos e de hardware. Não estão implementados nesta versão. |
| Sincronização bancária (Open Finance) e investimentos | O pré-projeto já os deixa fora da primeira versão, porque exigem parcerias bancárias. |
| Categorização automática inteligente | Não está implementada: a categoria é informada pelo usuário. |
| Conteúdo educativo, quizzes e gamificação para jovens | Não estão implementados nesta versão e não têm regra numérica testável. |
| Perfil Administrador (Questão 4) | Não está implementado: não há gestão de conteúdo nesta versão. |
| Autenticação real (senha, PIN, biometria, tokens) | Foi substituída pelo cabeçalho `X-Usuario-Id`, para manter o foco nas regras. O controle de acesso (401/403) é testado, mas a autenticação em si não. |
| Teste de carga e estresse (JMeter) | Opcional no enunciado. Só o tempo de resposta básico (RNF-01) é verificado. |
| Teste de segurança (penetração, injeção) | Não é foco da disciplina. A mitigação é estrutural (consultas parametrizadas) e não tem teste dedicado. |
| Concorrência (requisições simultâneas) | O sistema é de uso individual. O acesso ao banco é serializado (`synchronized`) e não há teste dedicado. |

## 3. Itens a testar

### 3.1 Requisitos funcionais

| ID | Requisito |
|---|---|
| RF-01 | Cadastrar e consultar usuário; o perfil é derivado da idade (RN-01) |
| RF-02 | Criar, consultar, listar, alterar e excluir transações (receita ou despesa) |
| RF-03 | Definir o orçamento de uma categoria e consultar seu status no mês corrente (RN-02) |
| RF-04 | Listar as contas (despesas com vencimento) com sua situação (RN-03) |
| RF-05 | Sinalizar alerta ao familiar quando um idoso registra um gasto alto (RN-04) |
| RF-06 | Vincular familiar a um idoso, com acesso somente leitura e limite de vínculos (RN-05) |
| RF-07 | Criar metas de economia, registrar aportes e acompanhar o progresso (RN-06) |
| RF-08 | Identificar quem faz a requisição pelo cabeçalho `X-Usuario-Id` e aplicar as permissões |

### 3.2 Regras de negócio e valores-limite

A data de referência dos testes ("hoje") é **03/12/2026**, fixada pelo relógio injetado.

| ID | Regra | Valores-limite a testar |
|---|---|---|
| RN-01 | Idade ≥ 60 → IDOSO; 15 a 29 → JOVEM; demais → PADRÃO; fora de 0..130 → recusado (400) | −1 \| 0 · 14 \| 15 · 29 \| 30 · 59 \| 60 · 130 \| 131 |
| RN-02 | Gasto do mês < 80% do limite → OK; de 80% a 100% → ATENÇÃO; > 100% → ESTOURADO | limite 100,00: 79,99 \| 80,00 · 100,00 \| 100,01 |
| RN-03 | Conta paga → PAGA; vencida (após a data) → ATRASADA; vence em 0 a 3 dias → A_VENCER; senão → EM_DIA | vencimento em +4 \| +3 · 0 \| −1 dias |
| RN-04 | Despesa de IDOSO **acima** de R$ 500,00 → `alertaFamiliar = true` | 500,00 \| 500,01 |
| RN-05 | Familiar vinculado só lê (escrita → 403); no máximo 2 familiares por idoso (3º → 409) | 1 \| 2 \| 3 vínculos |
| RN-06 | Progresso = atual/alvo, arredondado para baixo, teto de 100%; CONCLUÍDA quando atual ≥ alvo | alvo 100,00: 99,99 \| 100,00 · 150,00 |
| RV-01 | Valor monetário entre 0,01 e 1.000.000,00, com no máximo 2 casas decimais | 0,00 \| 0,01 · 1.000.000,00 \| 1.000.000,01 · 10,001 |
| RV-02 | Data da transação não pode ser futura; vencimento só vale para despesas | hoje \| hoje + 1 dia |

### 3.3 Requisitos não funcionais (mensuráveis)

| ID | Requisito | Como medir |
|---|---|---|
| RNF-01 | Toda resposta da API em até **300 ms**, com o banco local e até 100 transações por usuário | Asserção `pm.response.responseTime < 300` na coleção Newman |
| RNF-02 | Entrada inválida nunca gera erro 500: sempre 4xx com corpo JSON `{"erro": "..."}` | Casos negativos de API e de integração (CT-22) |
| RNF-03 | Valores monetários sem perda de precisão: centavos preservados na gravação e na leitura | Teste de integração com o SQLite (ida e volta de 0,01 e 1.000.000,00) |
| RNF-04 | A suíte roda numa máquina que tenha só o Docker, em até **5 minutos** (com cache) | `docker compose run --rm testes` em cópia limpa e no job `docker` do CI |

## 4. Estratégia

### 4.1 Níveis de teste

| Nível | Alvo | Isolamento | Ferramenta |
|---|---|---|---|
| Unitário | `Regras` (funções puras) e `Servico` (casos de uso) | `Servico` com dublê do `Repositorio` (Mockito) e `Clock` fixo | JUnit 5 + `@ParameterizedTest`, Mockito |
| Integração | `Servico` + `RepositorioSqlite` com banco real; `Api` + `Servico` + SQLite via HTTP | SQLite em arquivo temporário, um banco novo por teste | JUnit 5, `java.net.http.HttpClient` |
| API (sistema) | Contêiner da aplicação, de fora para dentro | Banco limpo a cada execução; `RELOGIO_FIXO=2026-12-03` | Postman (coleção v2.1) + Newman |

### 4.2 Técnicas de projeto de casos

- **Partição de equivalência:** classes válidas e inválidas de cada entrada (tipo, valor, data, perfil, papel do usuário).
- **Análise de valor-limite:** os dois lados de cada limite das regras RN-01 a RN-06 e RV-01/RV-02 (tabela 3.2).
- **Tabela de decisão:** combinações de condições do RN-04 (perfil × tipo × valor) e do RN-05 (papel do ator × operação), e status da conta (paga × data).
- **Transição de estados:** ciclo de vida da transação (criada → alterada → excluída → 404).
- **Suposição de erro (error guessing):** JSON malformado, tipos errados, cabeçalho inválido e métodos HTTP não suportados.
- **Baseada em risco:** a prioridade de cada caso (Alta, Média, Baixa) vem dos riscos da seção 7. Casos de Alta são executados primeiro e são obrigatórios para a liberação.
- **Caixa-branca:** cobertura de instrução e de ramo (JaCoCo), com análise dos ramos não cobertos no relatório.
- **Teste de mutação manual:** operadores alterados de propósito nas regras (por exemplo, `>=` vira `>`) para provar que a suíte detecta o defeito.

### 4.3 Controle do relógio e dos dados

- **Relógio:** o `Servico` recebe um `java.time.Clock` no construtor. Os testes usam `Clock.fixed(2026-12-03)`, e a API em contêiner usa a variável `RELOGIO_FIXO`. Nenhum teste depende da data real.
- **Dados:** cada teste cria os próprios dados (usuários, transações) e não depende da ordem de execução. Os testes de integração usam um banco SQLite novo por teste. Os dados são fictícios ("Dona Maria", "Neto"), sem CPF, e-mail ou cartão reais.
- **Regressão:** a suíte completa roda a cada `push` no GitHub Actions.

## 5. Critérios

| Tipo | Critério |
|---|---|
| **Entrada** | O código compila; o build do CI está verde; `docker compose up --build` sobe a API; requisitos e regras desta seção revisados pelo grupo; dados de teste e data de referência definidos |
| **Saída (pronto)** | 100% dos casos de prioridade Alta executados e aprovados · ≥ 95% de todos os casos aprovados · cobertura de instrução ≥ 70% (meta 80%), com análise do que ficou fora · 0 defeitos de severidade Crítica ou Alta em aberto · todos os mutantes manuais (mínimo 3) detectados · CI verde no commit da tag de entrega · rastreabilidade completa (todo RF/RN com ao menos um caso) |
| **Suspensão** | Build quebrado ou contêiner que não sobe; defeito bloqueante na identificação do usuário (RF-08) ou na persistência que impeça mais de 30% dos casos; ambiente do CI indisponível |
| **Retomada** | Correção commitada e build verde no CI; reexecução da suíte completa (regressão), e não só do caso que falhou |

## 6. Ambiente e ferramentas

| Ferramenta | Versão | Uso |
|---|---|---|
| Java (Eclipse Temurin) | 21 | Linguagem e runtime |
| Maven | 3.9 | Build (imagem `maven:3.9-eclipse-temurin-21`) |
| JUnit Jupiter | 5.11.3 | Testes unitários e de integração |
| Mockito | 5.14.2 | Dublês do repositório |
| JaCoCo | 0.8.12 | Cobertura, com piso configurado no `pom.xml` |
| SQLite JDBC | 3.47.1.0 | Banco de dados |
| Gson | 2.11.0 | JSON da API |
| Docker + Docker Compose | Engine 24+ / Compose v2 | Execução reprodutível (`app`, `testes`, `newman`) |
| Postman / Newman | Coleção v2.1 / Newman 6 | Testes de API |
| GitHub Actions | `ubuntu-latest` | Integração contínua |
| GitHub Issues | — | Registro de defeitos |
| Claude Code | Claude Opus 5.5 | Agente de IA (ver "Uso de IA" no README) |

## 7. Riscos de teste

Probabilidade (P) e Impacto (I): A = alto, M = médio, B = baixo.

| ID | Risco | P | I | Mitigação |
|---|---|---|---|---|
| R1 | Erro de limite nas regras (`>=` trocado por `>`) passar despercebido | M | A | Valor-limite dos dois lados em todas as regras; mutação manual nos operadores |
| R2 | Testes instáveis por dependerem da data do sistema | A | A | `Clock` injetado e `RELOGIO_FIXO`; proibido `LocalDate.now()` sem relógio |
| R3 | Vazamento de permissão (familiar consegue alterar dados do idoso) | M | A | Tabela de decisão papel × operação; casos negativos 403 em unitário, integração e API |
| R4 | Perda de precisão em valores monetários | M | A | `BigDecimal` (nunca `double`), gravado como texto; teste de ida e volta no banco |
| R5 | A suíte não rodar na máquina do professor | M | A | Tudo em Docker Compose; job `docker` no CI; teste numa cópia limpa antes da entrega |
| R6 | CI quebrar na véspera da entrega | B | A | Pipeline rodando desde a Fase 1; todo push passa pelo CI |
| R7 | Testes gerados pela IA com asserções fracas | M | M | Revisão de cada teste; mutação manual; teste só é aceito depois de ser visto falhando |
| R8 | Integrantes sem Docker local e rede que intercepta TLS | A | M | CI como ambiente de referência; instruções no README |
| R9 | Prazo curto (entrega em 03/12/2026) | A | A | Priorização por risco; opcionais (Selenium, JMeter, SonarQube) só se sobrar tempo |
| R10 | Regra mal interpretada (por exemplo, "acima de R$ 500" incluir ou não os 500,00) | M | M | Limites escritos com número nesta tabela 3.2 e revisados pelo grupo antes da automação |

## 8. Cronograma e responsabilidades

| Período | Atividade | Responsável |
|---|---|---|
| 16/11 | Fase 1: sistema, Docker e CI | _a preencher_ |
| 17/11 – 19/11 | Fase 2: plano de teste e casos de teste; revisão por pares | _a preencher_ |
| 20/11 – 25/11 | Fase 3: testes unitários, de integração e de mutação; cobertura ≥ 70% | _a preencher_ |
| 26/11 – 28/11 | Fase 4: coleção Postman + Newman; CI completo | _a preencher_ |
| 29/11 – 02/12 | Fase 5: relatório, Uso de IA, cópia limpa, tag `v1.0.0` e ensaio | _a preencher_ |
| 03/12 | Entrega no Anima Hub | _a preencher_ |

## 9. Métricas coletadas

| Métrica | Fonte | Uso |
|---|---|---|
| Casos planejados, executados, aprovados, reprovados e bloqueados (por nível) | Surefire e Newman | Resumo da execução e critério de saída |
| Taxa de aprovação = aprovados / executados | Surefire e Newman | Critério de saída (≥ 95%) |
| Cobertura de instrução e de ramo | JaCoCo | Critério de saída (≥ 70%) e análise do que falta |
| Mutantes detectados / mutantes criados | Script de mutação | Eficácia da suíte |
| Densidade de defeitos = defeitos / KLOC | GitHub Issues e contagem de linhas | Relatório |
| Rastreabilidade = requisitos com caso / requisitos | Script de rastreabilidade | Critério de saída (100%) |
| Tempo de execução da suíte | Log do CI | RNF-04 |
