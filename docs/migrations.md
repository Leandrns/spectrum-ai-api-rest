# Histórico de migrations

Migrations ficam em `src/main/resources/db/migration/` e seguem o padrão Flyway `V{número}__{descrição}.sql`. São executadas automaticamente no startup da aplicação.

| Versão | Descrição |
|---|---|
| `V1__init_schema.sql` | Schema inicial (multi-tenant, users, sessions, searches, prompts) |
| `V2__vehicles_catalog.sql` | Catálogo de veículos para autocomplete + seed mínimo |
| `V3__prompt_templates_seed.sql` | Seed dos prompts versionados para o Gemini (`vehicle_spec_search` v1, hoje inativo) |
| `V4__searches_ai_latency_ms.sql` | Métrica de latência da IA |
| `V5__audit_log.sql` | Trilha de auditoria + soft delete |
| `V6__encrypt_pii_columns.sql` | Legado: alargou colunas de PII para ciphertext. A criptografia em repouso foi removida; o arquivo é mantido porque a migration já foi aplicada |
| `V7__data_exports.sql` | Registro dos arquivos exportados (CSV) e sua retenção |
| `V8__prompt_vehicle_spec_search.sql` | `vehicle_spec_search` v2: hierarquia de fontes, restrição de mercado e ano-modelo, fontes proibidas, rastreabilidade das tags e `NOT_FOUND` |
| `V9__bigquery_syncs.sql` | Controle da ingestão no BigQuery: hash por pesquisa, para não empilhar linhas iguais |

Para criar uma nova migration, adicione um arquivo com o próximo número de versão.
