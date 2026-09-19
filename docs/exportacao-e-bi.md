# Exportação de dados e BI

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
  A coluna `fonte` usa as quatro tags descritas em [Pesquisa de specs](pesquisa-de-specs.md).
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
