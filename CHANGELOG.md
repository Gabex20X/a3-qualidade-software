# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e versionamento [SemVer](https://semver.org/lang/pt-BR/).

## [Nao publicado]

### Adicionado
- Esqueleto do sistema: API REST de financas pessoais (usuarios, transacoes, orcamentos, contas, familiares, metas).
- Regras de negocio RN-01 a RN-06 em `Regras`.
- Persistencia em SQLite; relogio injetavel (`RELOGIO_FIXO`).
- Primeiros testes unitarios das regras (`RegrasTest`, valores-limite).
- Dockerfile e docker-compose.yml (servicos `app` e `testes`).
- Workflow do GitHub Actions (build, testes, cobertura e suite no Docker).
