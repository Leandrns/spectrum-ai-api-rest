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
- [Exportação de dados (CSV)](#exportação-de-dados-csv)
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
| `GCS_EXPORT_BUCKET` | Só para exportação | Nome do bucket no GCP — veja [Exportação de dados](#exportação-de-dados-csv) |

> Sem `GCS_EXPORT_BUCKET` a API sobe normalmente; apenas os endpoints `/export`
> respondem `502 STORAGE_ERROR`. Todo o resto funciona.

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

### 7. Exportar em CSV

Exige o bucket configurado — veja [Exportação de dados](#exportação-de-dados-csv).
A resposta traz uma URL temporária; baixe o arquivo dela:

```bash
curl "http://localhost:8080/v1/sessions/<id-da-sessao>/export?format=csv" \
  -H "Authorization: Bearer $TOKEN"
```

Chamar de novo sem nenhuma pesquisa nova reaproveita o mesmo arquivo no bucket e
devolve apenas uma URL nova — confira em `SELECT * FROM data_exports`.

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
| `GET` | `/v1/searches/{id}/export` | JWT (`ADMIN`/`ANALYST`) | Exporta a ficha de um veículo (`?format=csv`) |
| `GET` | `/v1/sessions/{id}/export` | JWT (`ADMIN`/`ANALYST`) | Exporta o comparativo da sessão inteira (`?format=csv`) |
| `POST` | `/v1/admin/vehicles/import` | JWT (`ADMIN`) | Popula catálogo via FIPE |
| `GET` | `/v1/admin/vehicles/import/status` | JWT (`ADMIN`) | Status da importação |

**Roles disponíveis**: `ADMIN`, `ANALYST`, `VIEWER`.

> O Swagger UI lista todos os endpoints com schemas, exemplos e botão "Try it out": http://localhost:8080/swagger-ui.html

---

## Exportação de dados (CSV)

Dois escopos, ambos com o mesmo formato de arquivo:

| Endpoint | O que gera |
|---|---|
| `GET /v1/searches/{id}/export?format=csv` | A ficha técnica de um veículo |
| `GET /v1/sessions/{id}/export?format=csv` | Todas as pesquisas concluídas da sessão, num arquivo só |

A resposta **não é o arquivo**, e sim uma URL temporária de download direto do bucket:

```json
{ "downloadUrl": "https://storage.googleapis.com/...", "expiresAt": "2026-08-22T22:00:00Z" }
```

`format=pdf` está previsto no contrato e responde `501` até a segunda etapa da feature.

### Formato do arquivo

CSV em **RFC 4180** (vírgula, CRLF, aspas duplas) e **UTF-8 com BOM** — o BOM é o que
faz o Excel respeitar os acentos, e as ferramentas de BI o ignoram.

O layout é **long/tidy**: uma linha por campo, com oito colunas fixas.

```csv
marca,modelo,versao,ano_modelo,categoria,campo,valor,fonte
Toyota,Corolla Cross,XRE,2026,Motor e Transmissão,Potência,177 cv,OFFICIAL
Toyota,Corolla Cross,XRE,2026,Motor e Transmissão,Torque,"21,0 kgfm",REVIEW
Toyota,Corolla Cross,XRE,2026,Rodas,Aro (polegadas),18,OFFICIAL
Toyota,Corolla Cross,XRE,2026,Rodas,Pneus Run-Flat,Dado não encontrado,ESTIMATED
```

Por que não uma coluna por campo: a ficha canônica tem 14 categorias e mais de 250
campos, e cada revisão do prompt acrescenta outros. Em formato wide, as colunas
mudariam sozinhas e quebrariam dashboards já montados — aqui o schema é fixo e o
pivot de `categoria`/`campo` fica a cargo da ferramenta de BI.

Detalhes que valem saber:

- Campos sem resposta aparecem como `Dado não encontrado` / `ESTIMATED`, de propósito:
  é assim que se mede a cobertura de uma pesquisa. Filtrar depois é trivial.
- Na exportação de sessão, se o mesmo veículo foi pesquisado mais de uma vez, vale a
  pesquisa concluída mais recentemente.
- O arquivo traz só dados do veículo — nada de `search_id`, status ou timestamps.

### Configurando o bucket

1. Crie o bucket no GCP (region única basta; não precisa ser público).
2. Crie uma service account com `roles/storage.objectAdmin` **no bucket** e baixe a
   chave JSON.

   > A assinatura de URL V4 precisa de uma chave privada. Uma service account **com
   > chave JSON** assina localmente. Se a aplicação rodar apenas com a credencial do
   > metadata server (Cloud Run sem chave), a assinatura passa a exigir
   > `roles/iam.serviceAccountTokenCreator` e o fluxo IAM SignBlob.

3. Preencha `GCS_EXPORT_BUCKET` e `GCP_PROJECT_ID` no `.env`.
4. Salve a chave como `gcp-credentials.json` na raiz do projeto — já está no
   `.gitignore` e no `.dockerignore`.
5. Habilite a montagem da credencial no Compose:

```bash
cp docker-compose.override.yml.example docker-compose.override.yml
```

O Compose aplica o override automaticamente, sem `-f` extra.

### CORS do bucket (só se o download for por `fetch`)

A `downloadUrl` aponta para o `storage.googleapis.com`, não para a API — então o CORS
que vale ali é o **do bucket**, e a configuração da API não tem efeito nenhum sobre ele.

Se o app abrir a URL em nova aba, via `window.location` ou `<a href>`, não há
preflight e nada precisa ser feito. Já se o download for por `fetch`/XHR (para
mostrar um progresso, por exemplo), configure o bucket:

```bash
gcloud storage buckets update gs://SEU-BUCKET --cors-file=cors-bucket.json
```

Com `cors-bucket.json` assim (ajuste as origens para as do seu frontend):

```json
[{ "origin": ["http://localhost:8081"], "method": ["GET"], "responseHeader": ["Content-Type", "Content-Disposition"], "maxAgeSeconds": 3600 }]
```

### Retenção dos arquivos

O `DataRetentionScheduler` apaga `searches` antigas, mas **não** apaga objetos do
bucket — configure *Object Lifecycle Management* no GCS (delete após 730 dias, o
mesmo valor de `RETENTION_SEARCHES_DAYS`). É configuração de infra, sem código.
A tabela `data_exports` é apenas o registro de controle: guarda o hash do conteúdo
para reaproveitar um arquivo já enviado em vez de subir outro igual.

---

## Perfis de execução

| Perfil | Uso | Comportamento |
|---|---|---|
| `dev` | Desenvolvimento local | SQL logado, stack trace nos erros, log nível DEBUG, rate limit relaxado, fallback de chave JWT |
| `prod` | Produção | SQL desativado, erros sem detalhes, HTTPS obrigatório, JWT via env var obrigatório |

Controlado pela variável `SPRING_PROFILES_ACTIVE` no `.env`.

---

## Deploy em produção

Stack de produção: **Railway para a API** + **Supabase (free tier) para o Postgres**. Dois provedores, mas ambos com painel próprio, HTTPS automático e deploy via `git push` — nenhum passo manual de servidor (VM, firewall, certificado) é necessário.

### 1. Banco (Supabase)

1. Crie um projeto gratuito em [supabase.com](https://supabase.com).
2. Em **Settings → Database**, copie a connection string no modo **Session** (porta 5432) — **não** use o Transaction pooler (porta 6543), que não suporta prepared statements da forma que o Hibernate usa.
3. A URL final tem o formato:
   ```
   jdbc:postgresql://db.xxxxxxxxxxxx.supabase.co:5432/postgres?sslmode=require
   ```
   `sslmode=require` é obrigatório — o Supabase não aceita conexão sem TLS.

### 2. API (Railway)

1. No [dashboard do Railway](https://railway.app), **New Project → Deploy from GitHub repo**, escolha o repositório `spectrum-ai-api-rest`. O Railway detecta o `Dockerfile` da raiz sozinho — não há build command a configurar.
2. Na aba **Variables**, configure (mesmas do `.env`, valores de produção):

   | Variável | Valor |
   |---|---|
   | `SPRING_PROFILES_ACTIVE` | `prod` |
   | `DATABASE_URL` | connection string do Supabase (passo 1), **com o prefixo `jdbc:`** |
   | `DATABASE_USER` | `postgres` |
   | `DATABASE_PASSWORD` | senha do projeto Supabase |
   | `JWT_SECRET` | gerar com `openssl rand -base64 64` |
   | `GEMINI_API_KEY` | sua chave da Gemini API |
   | `FIPE_API_TOKEN` | seu token da FIPE API |
   | `CORS_ALLOWED_ORIGINS` | origem do app mobile em produção (exata, sem curinga) |
   | `GCS_EXPORT_BUCKET` | nome do bucket de exportações |
   | `GCP_PROJECT_ID` | id do projeto GCP |
   | `GCP_CREDENTIALS_JSON` | service account em base64 (passo 3) |

   > **Não adicione o plugin PostgreSQL do Railway.** O banco aqui é o Supabase, e o
   > plugin injeta uma variável `DATABASE_URL` própria no formato
   > `postgresql://user:senha@host/railway` — que o Spring não aceita e que
   > sobrescreveria a do Supabase, derrubando a aplicação no boot.

   `PORT` é injetada pelo Railway e a aplicação já a respeita — não defina manualmente.

3. **Credencial do GCP.** O Railway não monta arquivos de secret, então
   `GOOGLE_APPLICATION_CREDENTIALS` (que aponta para um caminho em disco) não serve lá.
   Passe o JSON inteiro em `GCP_CREDENTIALS_JSON` — a aplicação aceita texto puro ou
   base64. Use **base64**: o painel trata o valor como uma linha só, e a chave privada
   tem quebras de linha.

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("gcp-credentials.json")) | Set-Clipboard
   ```

   ```bash
   base64 -w0 gcp-credentials.json
   ```

   Na primeira exportação o log confirma qual identidade foi usada:
   `GCS autenticado pela credencial inline (service account spectrum-exports@...)`.

4. **Expor a API**: *Settings → Networking → Generate Domain*. O Railway dá HTTPS e envia
   `X-Forwarded-Proto`, então `REQUIRE_HTTPS=true` (default do profile `prod`) já funciona
   sem ajuste de proxy. Em *Settings → Deploy*, aponte o healthcheck para
   `/actuator/health` — assim um deploy que sobe quebrado não substitui a versão no ar.
5. Deploy automático: qualquer `git push` na branch conectada builda e publica a nova versão sozinho — não há passo manual de deploy.

### Verificando

```bash
curl https://<seu-servico>.up.railway.app/actuator/health
```

Depois, atualize `EXPO_PUBLIC_API_URL` no app mobile para essa URL — e acrescente essa
origem em `CORS_ALLOWED_ORIGINS`. Se o app baixar o CSV por `fetch`, a origem de produção
também precisa entrar no CORS **do bucket**, que é configuração separada
(veja [Exportação de dados](#exportação-de-dados-csv)).

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
├── search/        # Buscas de veículos e resultados (SSE)
├── export/        # Exportação CSV/PDF para BI + armazenamento no GCS
├── vehicles/      # Catálogo de veículos + importação FIPE
├── insights/      # Geração de insights via IA
├── ai/            # Integração com provedores de IA (Gemini)
├── tenant/        # Isolamento de dados por tenant
├── audit/         # Trilha de auditoria (LGPD/SOX)
├── common/        # DTOs, exceções, retenção de dados
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
| `V6__encrypt_pii_columns.sql` | Legado: alargou colunas de PII para ciphertext. A criptografia em repouso foi removida; o arquivo é mantido porque a migration já foi aplicada |

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

**Erro de CORS chamando a API a partir do app**

Confirme primeiro qual é a lista ativa — ela é logada no boot:

```bash
docker compose logs api | grep "CORS configurado"
```

- `origins=[]` → nenhuma origem permitida, tudo responde `403 Invalid CORS request`.
  A partir da versão atual isso derruba o boot com mensagem explícita, mas se aparecer:
  a causa é `CORS_ALLOWED_ORIGINS` definida **vazia**. O default do Spring
  (`${VAR:origens}`) só vale quando a variável **não existe** — passar vazio não cai
  no default, o vazio vence. Comente a linha no `.env` em vez de deixá-la sem valor.
- Origem ausente da lista → em dev os padrões já cobrem `localhost` em qualquer porta,
  o emulador Android (`10.0.2.2`) e as faixas de rede local (`192.168.*`, `10.*`,
  `172.16.*`), que é como o celular físico enxerga a máquina. Se sua origem for outra,
  acrescente em `CORS_ALLOWED_ORIGINS`.
- Preflight recusado por header → o navegador diz qual (*"Request header field X is not
  allowed"*). Acrescente-o em `CORS_ALLOWED_HEADERS`.

Para reproduzir sem o app, simule o preflight trocando a origem:

```bash
curl -i -X OPTIONS http://localhost:8080/v1/sessions -H "Origin: http://192.168.0.7:8081" -H "Access-Control-Request-Method: GET"
```

`200` com `Access-Control-Allow-Origin` de volta = liberado; `403` = origem recusada.

**Erro de CORS ao baixar o CSV da `downloadUrl`**
- Esse CORS não é da API: quem responde é o `storage.googleapis.com`. Se o download for
  feito por `fetch`/XHR no app web, é preciso configurar CORS **no bucket** (veja
  [Exportação de dados](#exportação-de-dados-csv)). Abrir a URL em nova aba ou via
  `window.location` não dispara CORS e funciona sem nenhuma configuração.

**Quero zerar o banco e começar do zero**
```bash
docker compose down -v
docker compose up --build
```
