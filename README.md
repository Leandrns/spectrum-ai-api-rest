# Spectrum AI — Backend

API REST para a plataforma de análise competitiva automotiva Spectrum AI.

Construída com **Spring Boot 4**, **Java 21**, **PostgreSQL 16** e integração com **Google Gemini**.

---

## Integrantes

Caio Alexandre dos Santos - RM: 558460

Leandro do Nascimento Souza - RM: 558893

Rafael de Mônaco Maniezo - RM: 556079

Vinicius Rozas Pannuci de Paula Cont - RM: 555338

---

## Sumário

- [Quick start (TL;DR)](#quick-start-tldr)
- [Tecnologias](#tecnologias)
- [Pré-requisitos](#pré-requisitos)
- [Configuração do ambiente](#configuração-do-ambiente)
- [Rodando a aplicação](#rodando-a-aplicação)
- [Roteiro de teste end-to-end](#roteiro-de-teste-end-to-end)
- [Endpoints principais](#endpoints-principais)
- [Perfis de execução](#perfis-de-execução)
- [Comandos úteis](#comandos-úteis)
- [Estrutura de módulos](#estrutura-de-módulos)
- [Migrations](#migrations)
- [Troubleshooting](#troubleshooting)

---

## Quick start (TL;DR)

Com Docker Desktop instalado:

```bash
git clone <url-do-repositorio>
cd spectrum-ai-api-rest
cp .env.example .env
# edite .env e preencha JWT_SECRET, GEMINI_API_KEY e FIPE_API_TOKEN
docker compose up --build
```

Pronto. Abra http://localhost:8080/swagger-ui.html para explorar a API.

---

## Tecnologias

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 4 |
| Segurança | Spring Security + JWT (JJWT 0.12) |
| Persistência | Spring Data JPA + PostgreSQL 16 |
| Migrations | Flyway |
| IA | Google Gemini (modelo `gemini-2.5-flash`) |
| Documentação | SpringDoc OpenAPI (Swagger UI) |
| Build | Maven 3.9 |

---

## Pré-requisitos

| Ferramenta | Versão mínima | Necessário para |
|---|---|---|
| Docker Desktop | 24+ | Subir banco e API em contêiner (fluxo recomendado) |
| Java 21 | 21 | Rodar/debugar fora do Docker |
| Maven | 3.9 | Build fora do Docker |

> Para o fluxo recomendado (Docker) só é necessário o Docker Desktop.

---

## Configuração do ambiente

### 1. Clonar o repositório

```bash
git clone <url-do-repositorio>
cd spectrum-ai-api-rest
```

### 2. Criar o arquivo de variáveis de ambiente

```bash
cp .env.example .env
```

Abra o `.env` e preencha:

| Variável | Obrigatória | Como obter |
|---|---|---|
| `JWT_SECRET` | Sim | Gere com os comandos abaixo (base64, mínimo 256 bits) |
| `GEMINI_API_KEY` | Sim | https://aistudio.google.com/apikey (gratuito) |
| `FIPE_API_TOKEN` | Sim (para popular catálogo) | https://fipe.online/dashboard (gratuito) |
| `DATABASE_PASSWORD` | Recomendado | Default `spectrum` funciona em dev local |
| `DATA_ENCRYPTION_KEY` | Apenas em prod | Dev usa fallback embutido — em prod, gere com `openssl rand -base64 32` |

**Gerando um `JWT_SECRET` seguro:**
```bash
# Linux / macOS
openssl rand -base64 64
```
```powershell
# Windows (PowerShell)
[Convert]::ToBase64String((1..64 | ForEach-Object { [byte](Get-Random -Max 256) }))
```

> O arquivo `.env` nunca deve ser commitado — já está no `.gitignore`.

---

## Rodando a aplicação

### Opção A — Tudo no Docker (recomendado)

Sobe o banco e a API juntos:

```bash
docker compose up --build
```

Na primeira execução o Docker irá:
1. Baixar as imagens base
2. Compilar o projeto via Maven dentro do contêiner
3. Subir o PostgreSQL e aguardar o healthcheck
4. Subir a API e rodar as migrations do Flyway automaticamente

Nas execuções seguintes (sem mudanças no código):
```bash
docker compose up
```

Para rodar em background:
```bash
docker compose up --build -d
docker compose logs -f api   # acompanhar os logs
```

### Opção B — Banco no Docker, API na IDE (para debug)

Útil para usar o debugger da IDE ou ter hot-reload mais rápido.

**1. Subir apenas o banco:**
```bash
docker compose up postgres
```

**2. Configurar as variáveis na run configuration da IDE:**

```
SPRING_PROFILES_ACTIVE=dev
DATABASE_URL=jdbc:postgresql://localhost:5432/spectrum
DATABASE_USER=spectrum
DATABASE_PASSWORD=spectrum
JWT_SECRET=<seu-valor>
GEMINI_API_KEY=<sua-chave>
FIPE_API_TOKEN=<seu-token>
```

**3. Rodar a aplicação pela IDE** apontando para a classe `SpectrumAiApplication`.

### Verificando que subiu

| Endpoint | O que verificar |
|---|---|
| http://localhost:8080/actuator/health | Deve retornar `{"status":"UP"}` |
| http://localhost:8080/swagger-ui.html | Documentação interativa da API |
| http://localhost:8080/v3/api-docs | Spec OpenAPI em JSON |

---

## Roteiro de teste end-to-end

Roteiro mínimo para validar o funcionamento da API após o `docker compose up`. Você pode executar tudo pelo **Swagger UI** (http://localhost:8080/swagger-ui.html) ou via `curl`.

### 1. Registrar uma empresa + usuário admin

`POST /v1/auth/register` cria uma nova empresa (tenant) e já cadastra o usuário como **`ADMIN`** dessa empresa — esse é o ponto de partida para testar todos os fluxos protegidos.

> Para adicionar analistas a uma empresa **já existente**, use `POST /v1/auth/registerAnalyst` (mesmo payload). Esse endpoint cria um usuário com role `ANALYST` vinculado à empresa informada — útil para simular um cenário multiusuário.

> **Política de senha**: 10–128 caracteres, com maiúscula, minúscula, dígito e caractere especial. Não pode conter sequências triviais (`1234`, `abcd`, `qwerty`) nem caracteres repetidos 3x. Exemplo válido: `Spectrum@2026!`

```bash
curl -X POST http://localhost:8080/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "companyName": "Minha Empresa Ltda",
    "fullName": "João da Silva",
    "email": "joao@empresa.com",
    "password": "Spectrum@2026!"
  }'
```

A resposta inclui `accessToken` e `refreshToken`. Copie o `accessToken` — ele já tem permissão de `ADMIN` e funciona para todos os passos seguintes.

### 2. Fazer login (se precisar de um novo token)

```bash
curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "joao@empresa.com",
    "password": "Spectrum@2026!"
  }'
```

### 3. Testar autocomplete de veículos (público, sem token)

```bash
curl "http://localhost:8080/v1/vehicles/brands?q=toy"
```

> O catálogo já é populado com um seed mínimo pelas migrations. Para um catálogo completo a partir da FIPE, veja o passo 4.

### 4. (Opcional) Popular catálogo completo a partir da FIPE

O usuário criado no passo 1 já é `ADMIN`, então o `accessToken` retornado pelo `/register` pode ser usado diretamente:

```bash
TOKEN="cole-aqui-o-access-token"

curl -X POST http://localhost:8080/v1/admin/vehicles/import \
  -H "Authorization: Bearer $TOKEN"
```

Acompanhe o progresso:

```bash
curl http://localhost:8080/v1/admin/vehicles/import/status \
  -H "Authorization: Bearer $TOKEN"
```

### 5. Criar uma sessão de análise

```bash
curl -X POST http://localhost:8080/v1/sessions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "Análise Compactos 2026"}'
```

### 6. Disparar uma busca

```bash
curl -X POST http://localhost:8080/v1/searches \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "<id-da-sessao-criada-acima>",
    "brand": "Toyota",
    "model": "Corolla",
    "year": 2024
  }'
```

A resposta retorna `202 Accepted` com o `id` da busca enfileirada. Para acompanhar o progresso em tempo real (SSE):

```bash
curl -N http://localhost:8080/v1/searches/<id>/stream \
  -H "Authorization: Bearer $TOKEN"
```

Ou obtenha o resultado final:

```bash
curl http://localhost:8080/v1/searches/<id>/result \
  -H "Authorization: Bearer $TOKEN"
```

---

## Endpoints principais

| Método | Path | Auth | Descrição |
|---|---|---|---|
| `POST` | `/v1/auth/register` | público | Cria empresa + usuário (role `ADMIN`) — empresa precisa não existir |
| `POST` | `/v1/auth/registerAnalyst` | público | Adiciona usuário (role `ANALYST`) a uma empresa **já existente** |
| `POST` | `/v1/auth/login` | público | Login com email + senha |
| `POST` | `/v1/auth/refresh` | público | Renova access token |
| `GET` | `/v1/vehicles/brands` | público | Autocomplete de marcas |
| `GET` | `/v1/vehicles/models` | público | Autocomplete de modelos |
| `GET` | `/v1/vehicles/trims` | público | Autocomplete de versões |
| `POST` | `/v1/sessions` | JWT (`ADMIN`/`ANALYST`) | Cria sessão de análise |
| `GET` | `/v1/sessions` | JWT | Lista sessões (paginado) |
| `POST` | `/v1/searches` | JWT (`ADMIN`/`ANALYST`) | Enfileira busca de veículo |
| `GET` | `/v1/searches/{id}/stream` | JWT | Progresso da busca (SSE) |
| `GET` | `/v1/searches/{id}/result` | JWT | Resultado completo |
| `GET` | `/v1/searches/{id}/export` | JWT (`ADMIN`/`ANALYST`) | Exporta para PDF/CSV |
| `POST` | `/v1/admin/vehicles/import` | JWT (`ADMIN`) | Popula catálogo via FIPE |
| `GET` | `/v1/admin/vehicles/import/status` | JWT (`ADMIN`) | Status da importação |

**Roles disponíveis**: `ADMIN`, `ANALYST`, `VIEWER`.

> O Swagger UI lista todos os endpoints com schemas, exemplos e botão "Try it out": http://localhost:8080/swagger-ui.html

---

## Perfis de execução

| Perfil | Uso | Comportamento |
|---|---|---|
| `dev` | Desenvolvimento local | SQL logado, stack trace nos erros, log nível DEBUG, rate limit relaxado, fallback de chaves JWT/AES |
| `prod` | Produção | SQL desativado, erros sem detalhes, HTTPS obrigatório, JWT/AES via env var obrigatório |

Controlado pela variável `SPRING_PROFILES_ACTIVE` no `.env`.

---

## Comandos úteis

```bash
# Parar todos os serviços
docker compose down

# Parar e apagar o banco (reset completo)
docker compose down -v

# Ver logs em tempo real
docker compose logs -f

# Rebuild somente da API (após mudanças no código)
docker compose up --build api

# Acessar o banco via psql
docker compose exec postgres psql -U spectrum -d spectrum
```

---

## Estrutura de módulos

```
src/main/java/com/spectrumai/backend/
├── auth/          # Autenticação, JWT, registro, login, política de senhas
├── company/       # Gestão de empresas (multi-tenant)
├── user/          # Gestão de usuários e roles
├── session/       # Sessões de análise competitiva
├── search/        # Buscas de veículos e resultados (SSE, export)
├── vehicles/      # Catálogo de veículos + importação FIPE
├── insights/      # Geração de insights via IA
├── ai/            # Integração com provedores de IA (Gemini)
├── tenant/        # Isolamento de dados por tenant
├── audit/         # Trilha de auditoria (LGPD/SOX)
├── common/        # Crypto AES-GCM, DTOs, exceções, retenção de dados
└── config/        # Configurações gerais (Security, CORS, OpenAPI)
```

---

## Migrations

Migrations ficam em `src/main/resources/db/migration/` e seguem o padrão Flyway `V{número}__{descrição}.sql`. São executadas automaticamente no startup da aplicação.

| Versão | Descrição |
|---|---|
| `V1__init_schema.sql` | Schema inicial (multi-tenant, users, sessions, searches, prompts) |
| `V2__vehicles_catalog.sql` | Catálogo de veículos para autocomplete + seed mínimo |
| `V3__prompt_templates_seed.sql` | Seed dos prompts versionados para o Gemini |
| `V4__searches_ai_latency_ms.sql` | Métrica de latência da IA |
| `V5__audit_log.sql` | Trilha de auditoria + soft delete |
| `V6__encrypt_pii_columns.sql` | Expande colunas de PII para suportar ciphertext AES-GCM |

Para criar uma nova migration, adicione um arquivo com o próximo número de versão.

---

## Troubleshooting

**`docker compose up` falha com erro de porta 5432 já em uso**
- Outro PostgreSQL está rodando localmente. Pare-o ou altere a porta no `docker-compose.yml`.

**API sobe mas retorna 500 em qualquer endpoint**
- Verifique se `JWT_SECRET` está preenchido no `.env` (mínimo 256 bits em base64).
- Verifique os logs com `docker compose logs api`.

**`/v1/auth/register` retorna 400 "senha nao atende a politica de seguranca"**
- A senha precisa ter ≥10 caracteres, com maiúscula, minúscula, dígito e caractere especial. Não pode ter sequências triviais nem conter o seu email/nome.

**Importação da FIPE retorna 401 ou 403**
- O endpoint exige role `ADMIN`. Use o `accessToken` retornado por `/v1/auth/register` (que já vem como admin).
- Verifique se `FIPE_API_TOKEN` foi preenchido no `.env`.

**Erro de checksum do Flyway após editar uma migration já aplicada**
- Set `FLYWAY_REPAIR_ON_START=true` no `.env` e suba novamente. Volte para `false` em seguida.

**Quero zerar o banco e começar do zero**
```bash
docker compose down -v
docker compose up --build
```
