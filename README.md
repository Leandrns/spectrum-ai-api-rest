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
- [Pesquisa de specs](#pesquisa-de-specs)
- [Exportação de dados (CSV)](#exportação-de-dados-csv)
- [Ingestão no BigQuery](#ingestão-no-bigquery)
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
| `GEMINI_MODEL` | Não | Modelo da pesquisa de specs. Padrão `gemini-2.5-flash` — veja [Pesquisa de specs](#pesquisa-de-specs) |
| `FIPE_API_TOKEN` | Sim (para popular catálogo) | https://fipe.online/dashboard (gratuito) |
| `DATABASE_PASSWORD` | Recomendado | Default `spectrum` funciona em dev local |
| `GCS_EXPORT_BUCKET` | Só para exportação | Nome do bucket no GCP — veja [Exportação de dados](#exportação-de-dados-csv) |
| `BQ_ENABLED` | Só para o BI | `true` liga a ingestão no BigQuery — veja [Ingestão no BigQuery](#ingestão-no-bigquery) |

> Sem `GCS_EXPORT_BUCKET` a API sobe normalmente; apenas os endpoints `/export`
> respondem `502 STORAGE_ERROR`. Todo o resto funciona. O mesmo vale para
> `BQ_ENABLED`: desligado (o padrão), só `/export/bigquery` responde `503`.

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
| `POST` | `/v1/searches/{id}/export/bigquery` | JWT (`ADMIN`/`ANALYST`) | Envia a ficha para o BigQuery — veja [Ingestão no BigQuery](#ingestão-no-bigquery) |
| `POST` | `/v1/sessions/{id}/export/bigquery` | JWT (`ADMIN`/`ANALYST`) | Envia a sessão inteira para o BigQuery |
| `POST` | `/v1/admin/vehicles/import` | JWT (`ADMIN`) | Popula catálogo via FIPE |
| `GET` | `/v1/admin/vehicles/import/status` | JWT (`ADMIN`) | Status da importação |

**Roles disponíveis**: `ADMIN`, `ANALYST`, `VIEWER`.

> O Swagger UI lista todos os endpoints com schemas, exemplos e botão "Try it out": http://localhost:8080/swagger-ui.html

---

## Pesquisa de specs

A ficha técnica é montada pelo Gemini com grounding (Google Search) a partir do
prompt versionado `vehicle_spec_search`, que vive na tabela `prompt_templates` —
não no código. `PromptServiceImpl` carrega a linha ativa daquele nome e substitui
os placeholders `{{brand}}`, `{{model}}`, `{{trim}}`, `{{year}}` e `{{categories}}`.

### Hierarquia de fontes

O prompt não deixa o modelo escolher onde procurar: ele percorre os níveis nesta
ordem e só desce com os campos que o nível acima não cobriu.

| Nível | Fontes | Tag resultante |
|---|---|---|
| 1 | Site oficial da montadora **no Brasil** (ficha técnica, configurador, tabela de equipamentos), PBEV/INMETRO (`gov.br/inmetro`), PROCONVE/IBAMA (`gov.br/ibama`), hubs de imprensa (`media.stellantis.com`, `vwcomunicacao.com.br`, `media.gm.com`), manuais do proprietário em PDF | `OFFICIAL` |
| 2 | `carrosnaweb.com.br`, `quatrorodas.abril.com.br` | `REVIEW` |
| 3 | Reviews em vídeo no YouTube, de canais de imprensa automotiva brasileira | `REVIEW` |
| 4 | Demais publicações automotivas brasileiras | `REVIEW` |

O PBEV é a fonte preferencial para consumo, cilindrada e autonomia; o PROCONVE,
para código de motor e regime de potência/torque; a Quatro Rodas, para números de
desempenho medido em pista.

O nível 3 existe porque o vídeo cobre bem justamente o que a ficha técnica omite —
presença de equipamento, multimídia, acabamento — já que o apresentador mostra o
item no carro. A contrapartida está no prompt: vídeo confirma **presença**, não
ausência. O silêncio de um review não vira `"Não"`; negar um item exige a tabela
oficial de equipamentos.

### Ano-modelo e rastreabilidade da tag

Duas regras que o prompt trata em seção própria porque, como linha solta, eram
ignoradas:

- **Ano-modelo.** O prompt manda localizar o ano *dentro* da fonte antes de usá-la
  — cabeçalho da planilha PBEV, título da ficha, nome do arquivo do manual — e
  descartar quando não for o ano pedido, mesmo sendo o site oficial e a mesma
  geração. Fonte oficial de outro ano-modelo **não é `OFFICIAL`**: vira
  `ESTIMATED` com o ano de origem no valor (`"10 airbags (estimado a partir do
  ano-modelo 2024, mesma geração)"`).
- **Rastreabilidade.** A tag descreve a origem *daquele campo*, não a melhor fonte
  aberta na pesquisa. Vídeo é sempre `REVIEW`, ainda que o canal esteja lendo o
  release da montadora. O PASSO 1 exige um mapa numerado de fontes com o nível de
  cada uma, e há uma verificação antiuniformidade: se o mapa cita um vídeo e o
  JSON saiu 100% `OFFICIAL`, as tags foram inventadas.

### Restrição de mercado e fontes proibidas

Duas seções do prompt existem para conter falhas observadas em produção:

- **Mercado brasileiro, sem exceção.** Fonte com unidade imperial (`lb-ft`, `mpg`,
  `hp`), nomenclatura de versão que não existe aqui (`XLE`, `Lariat`, `Limited`)
  ou domínio institucional estrangeiro é descartada — não convertida. Dado que só
  existe em fonte estrangeira é `NOT_FOUND`, nunca `ESTIMATED`.
- **Fontes proibidas.** Redes sociais, fóruns, Reddit/Quora, páginas de anúncio de
  classificados, wikis e agregadores automáticos. Dado que só aparece nelas
  também é `NOT_FOUND`.

`GeminiAiProvider` **não filtra** citações dessas origens: remover a fonte
esconderia a procedência sem corrigir o dado que veio dela. Em vez disso ele
registra um `WARN` quando o modelo cita um host proibido, que é o que torna a
regra verificável sem abrir cada resultado à mão:

```
WARN  Gemini citou 1 fonte(s) proibida(s) pelo prompt — revise o prompt ativo: [https://www.facebook.com/...]
```

### As quatro tags de `source`

| Tag | Significado |
|---|---|
| `OFFICIAL` | Dado de nível 1, para a versão **e** o ano-modelo exatos — as condições são cumulativas. Inclui o `"Não"` quando a tabela oficial de equipamentos foi consultada e o item não consta nela |
| `REVIEW` | Dado de nível 2, 3 ou 4, para a versão e o ano-modelo pesquisados |
| `ESTIMATED` | Inferido de base verificável **dentro do mercado brasileiro** (versão irmã nacional, ano adjacente, mesma motorização). O `value` declara a base: `"177 cv (estimado a partir da versão XEi 2025, mesmo motor 2.0)"` |
| `NOT_FOUND` | Dado indisponível após percorrer os níveis 1 a 3, ou existente apenas em fonte estrangeira/proibida. O `value` é exatamente `"Dado não encontrado"` |

Até a v1 do prompt não existia `NOT_FOUND`: lacuna real e inferência dividiam
`ESTIMATED`, então não dava para medir cobertura sem inspecionar o texto do valor.

`overallConfidence` sai da média dos pesos por campo — `OFFICIAL` 1.0,
`REVIEW` 0.8, `ESTIMATED` 0.4, `NOT_FOUND` 0.0.

### Escolhendo o modelo

`GEMINI_MODEL` seleciona o modelo usado na pesquisa. Padrão: `gemini-2.5-flash`.

```bash
GEMINI_MODEL=gemini-2.5-pro
```

Use o id completo do modelo como publicado pela API — `GeminiAiProvider` lê o
prefixo da família (`gemini-2`/`gemini-3` contra `gemini-1.x`) para decidir como
configurar o grounding, e um alias inventado cai no ramo errado. Nas famílias 2.x
e 3.x a API não expõe `DynamicRetrievalConfig`: quem decide acionar a busca é o
modelo, e a instrução no prompt é o único mecanismo para forçá-la. Modelo sem
suporte a Google Search grounding inviabiliza a pesquisa inteira.

O modelo em uso aparece em cada chamada no log: `Gemini (gemini-2.5-flash) respondeu em 8420ms`.

### Customizando o prompt

Nova regra de negócio, fonte oficial adicional ou ajuste de formato de valor não
exigem deploy de código — exigem uma migration que publique a próxima versão:

```sql
UPDATE prompt_templates SET active = FALSE WHERE name = 'vehicle_spec_search';
INSERT INTO prompt_templates (id, name, version, body, active, description)
VALUES (uuid_generate_v4(), 'vehicle_spec_search', 3, $prompt$...$prompt$, TRUE, '...');
```

O `UPDATE` não é opcional: `findByNameAndActiveTrue` devolve `Optional`, e um
índice único parcial em `prompt_templates(name) WHERE active` (criado na `V8`)
rejeita duas versões ativas do mesmo prompt. Versões antigas ficam na tabela
para comparação de qualidade entre revisões.

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

`format=pdf` devolve a mesma ficha em layout de relatório, no lugar do CSV.

Para análise contínua em ferramenta de BI, em vez de um arquivo por pesquisa, veja
[Ingestão no BigQuery](#ingestão-no-bigquery).

### Formato do arquivo

CSV em **RFC 4180** (vírgula, CRLF, aspas duplas) e **UTF-8 com BOM** — o BOM é o que
faz o Excel respeitar os acentos, e as ferramentas de BI o ignoram.

O layout é **long/tidy**: uma linha por campo, com oito colunas fixas.

```csv
marca,modelo,versao,ano_modelo,categoria,campo,valor,fonte
Toyota,Corolla Cross,XRE,2026,Motor e Transmissão,Potência,177 cv,OFFICIAL
Toyota,Corolla Cross,XRE,2026,Motor e Transmissão,Torque,"21,0 kgfm",REVIEW
Toyota,Corolla Cross,XRE,2026,Rodas,Aro (polegadas),18,OFFICIAL
Toyota,Corolla Cross,XRE,2026,Rodas,Pneus Run-Flat,Dado não encontrado,NOT_FOUND
```

Por que não uma coluna por campo: a ficha canônica tem 14 categorias e mais de 250
campos, e cada revisão do prompt acrescenta outros. Em formato wide, as colunas
mudariam sozinhas e quebrariam dashboards já montados — aqui o schema é fixo e o
pivot de `categoria`/`campo` fica a cargo da ferramenta de BI.

Detalhes que valem saber:

- Campos sem resposta aparecem como `Dado não encontrado` / `NOT_FOUND`, de propósito:
  é assim que se mede a cobertura de uma pesquisa. Filtrar depois é trivial.
  A coluna `fonte` usa as quatro tags descritas em [Pesquisa de specs](#pesquisa-de-specs).
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

## Ingestão no BigQuery

O CSV serve para uma pessoa baixar uma ficha. Para análise contínua — cobertura por
categoria, comparativo entre marcas, evolução de uma versão entre revisões — o dado
precisa estar num lugar consultável, e não num arquivo por pesquisa. É o que esta
integração faz: **cada pesquisa concluída vira linhas numa tabela de fatos do
BigQuery**, uma linha por campo da ficha.

Ligue com `BQ_ENABLED=true` no `.env`. Desligada (o padrão), a API sobe normalmente e
só `/export/bigquery` responde `503 WAREHOUSE_ERROR`.

| Quando | O que acontece |
|---|---|
| Pesquisa concluída | Sobe sozinha, se `BQ_AUTO_SYNC=true` (padrão) |
| `POST /v1/searches/{id}/export/bigquery` | Sobe uma pesquisa específica |
| `POST /v1/sessions/{id}/export/bigquery` | Sobe todas as pesquisas concluídas da sessão |

Os endpoints existem para carregar o histórico que já estava no banco antes da
integração, e para refazer uma carga que falhou. A resposta não é arquivo:

```json
{ "searchId": "...", "rows": 254, "skipped": false, "ingestedAt": "2026-09-05T14:22:07Z" }
```

`skipped: true` significa que o conteúdo não mudou desde a última carga e nada foi
reenviado — a consulta no BI já reflete esses dados.

> A carga automática **nunca derruba a pesquisa**: se o BigQuery estiver fora, a
> pesquisa continua concluída e válida, o erro fica no log e a pesquisa fica ausente
> da tabela até alguém chamar o endpoint. Não há job de reconciliação ainda.

### O que consultar

A aplicação cria **uma tabela e uma view**. Consulte sempre a view:

| Objeto | Papel |
|---|---|
| `vehicle_specs` | Tabela de fatos, **append-only**. Guarda todas as cargas, inclusive revisões da mesma pesquisa |
| `vw_vehicle_specs_latest` | Uma linha por (pesquisa, categoria, campo): a carga mais recente vence |

A tabela é append-only porque linhas recém-inseridas pela streaming API ficam num
buffer que **não aceita DML por até 90 minutos** — um `DELETE` antes do insert
falharia de forma intermitente. Empilhar e resolver na leitura troca esse problema
por uma view, e de graça mantém o histórico de revisões.

Reenvio redundante é barrado antes disso, pelo hash de conteúdo em `bigquery_syncs`:
sincronizar duas vezes a mesma pesquisa não grava nada na segunda.

### Colunas

| Coluna | Tipo | Observação |
|---|---|---|
| `tenant_id` | STRING | Empresa dona da pesquisa. **Filtre sempre por aqui** |
| `search_id` | STRING | Pesquisa de origem; permite voltar ao histórico da API |
| `session_id` | STRING | Sessão, quando a pesquisa nasceu num comparativo |
| `marca`, `modelo`, `versao` | STRING | |
| `ano_modelo` | INT64 | |
| `categoria`, `campo` | STRING | As 14 categorias canônicas e seus campos |
| `valor` | STRING | O valor como a IA retornou. **É o dado de referência** |
| `fonte` | STRING | `OFFICIAL`, `REVIEW`, `ESTIMATED` ou `NOT_FOUND` |
| `encontrado` | BOOL | `false` quando `fonte = NOT_FOUND`. Mede cobertura sem comparar strings |
| `valor_num` | FLOAT64 | Primeiro número de `valor`, quando existe. Best-effort — veja abaixo |
| `confianca` | NUMERIC | Confiança geral da pesquisa (0 a 1), igual em todas as linhas dela |
| `pesquisado_em` | TIMESTAMP | Conclusão da pesquisa. **Coluna de particionamento** |
| `ingerido_em` | TIMESTAMP | Carga que trouxe a linha; desempata revisões |

Particionada por dia em `pesquisado_em` e clusterizada por
`tenant_id, marca, modelo, categoria`. Filtrar por data e tenant é o que mantém o
custo de consulta perto de zero.

Sobre `valor_num`: a ficha mistura convenções, então a regra é a do português —
ponto seguido de exatamente três dígitos é separador de milhar (`1.999 cm³` → 1999) e
qualquer outro ponto é decimal (`1.6 Turbo` → 1.6). Valores com mais de um número
(`17/18`) devolvem o primeiro. Para indicador de negócio, confira em `valor`.

### Consultas de exemplo

Cobertura da ficha por categoria — quanto a IA conseguiu preencher:

```sql
SELECT categoria,
       COUNTIF(encontrado) AS preenchidos,
       COUNT(*) AS campos,
       ROUND(COUNTIF(encontrado) / COUNT(*) * 100, 1) AS cobertura_pct
FROM `spectrum-ai-504812.spectrum_analytics.vw_vehicle_specs_latest`
WHERE tenant_id = 'SEU-TENANT'
  AND pesquisado_em >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 90 DAY)
GROUP BY categoria
ORDER BY cobertura_pct;
```

Comparativo lado a lado, uma coluna por veículo:

```sql
SELECT categoria, campo,
       ANY_VALUE(IF(modelo = 'Corolla Cross', valor, NULL)) AS corolla_cross,
       ANY_VALUE(IF(modelo = 'Compass', valor, NULL)) AS compass
FROM `spectrum-ai-504812.spectrum_analytics.vw_vehicle_specs_latest`
WHERE tenant_id = 'SEU-TENANT'
  AND modelo IN ('Corolla Cross', 'Compass')
  AND ano_modelo = 2026
GROUP BY categoria, campo
ORDER BY categoria, campo;
```

### Configurando o dataset

A credencial e o projeto são **os mesmos do bucket** — nada novo para configurar
além do dataset.

1. Habilite a **BigQuery API** no projeto (normalmente já vem habilitada).
2. Crie o dataset `spectrum_analytics` **na mesma localização do bucket**
   (`us-east1`). A localização de um dataset é imutável: é por isso que o dataset é
   pré-requisito de infra e não é criado pela aplicação. Co-localizar mantém aberta a
   opção de trocar a ingestão por um load job a partir do bucket, se o volume crescer.
3. Conceda `roles/bigquery.dataEditor` à service account de exportação, **no
   dataset** (não no projeto) — o mesmo princípio do `objectAdmin` no bucket.

```bash
bq --location=us-east1 mk --dataset --description "Spectrum AI - fatos de ficha tecnica para BI" spectrum-ai-504812:spectrum_analytics
```

`dataEditor` basta: a tabela e a view são criadas pela API `tables.insert`, não por
um query job, então `roles/bigquery.jobUser` não é necessário para a aplicação. Quem
**consulta** precisa de `jobUser` no projeto (consultar é criar um job) mais
`dataViewer` no dataset.

Não crie a tabela à mão. Uma tabela sem particionamento funciona igual e só se
revela na fatura, porque toda consulta passa a varrer o histórico inteiro.

### Retenção e custo

As partições expiram em `BQ_RETENTION_DAYS` dias (padrão 730, o mesmo
`RETENTION_SEARCHES_DAYS`). O `DataRetentionScheduler` apaga do Postgres e **não
alcança o BigQuery** — lá quem descarta é a expiração de partição, configurada na
criação da tabela.

Custo no volume deste projeto: streaming insert é US$ 0,01 por 200 MB, e uma pesquisa
de ~250 linhas dá ~50 KB — cerca de 4.000 pesquisas por centavo. Armazenamento tem
10 GB grátis por mês e consulta tem 1 TB. Na prática, dentro do free tier.

As linhas não carregam dado pessoal: são especificações de veículo, e o `tenant_id` é
um UUID interno.

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
   | `BQ_ENABLED` | `true` para ligar a ingestão no BigQuery (padrão `false`) |
   | `BQ_DATASET` | dataset criado no GCP — padrão `spectrum_analytics` |

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
├── export/        # Exportação CSV/PDF + armazenamento no GCS
│   └── bigquery/  # Ingestão das fichas na tabela de fatos do BigQuery (BI)
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
| `V3__prompt_templates_seed.sql` | Seed dos prompts versionados para o Gemini (`vehicle_spec_search` v1, hoje inativo) |
| `V4__searches_ai_latency_ms.sql` | Métrica de latência da IA |
| `V5__audit_log.sql` | Trilha de auditoria + soft delete |
| `V6__encrypt_pii_columns.sql` | Legado: alargou colunas de PII para ciphertext. A criptografia em repouso foi removida; o arquivo é mantido porque a migration já foi aplicada |
| `V7__data_exports.sql` | Registro dos arquivos exportados (CSV) e sua retenção |
| `V8__prompt_vehicle_spec_search.sql` | `vehicle_spec_search` v2: hierarquia de fontes, restrição de mercado e ano-modelo, fontes proibidas, rastreabilidade das tags e `NOT_FOUND` |
| `V9__bigquery_syncs.sql` | Controle da ingestão no BigQuery: hash por pesquisa, para não empilhar linhas iguais |

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
