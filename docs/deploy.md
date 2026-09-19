# Deploy em produção

Stack de produção: **Railway para a API** + **Supabase (free tier) para o Postgres**. Dois provedores, mas ambos com painel próprio, HTTPS automático e deploy via `git push` — nenhum passo manual de servidor (VM, firewall, certificado) é necessário.

## 1. Banco (Supabase)

1. Crie um projeto gratuito em [supabase.com](https://supabase.com).
2. Em **Settings → Database**, copie a connection string no modo **Session** (porta 5432) — **não** use o Transaction pooler (porta 6543), que não suporta prepared statements da forma que o Hibernate usa.
3. A URL final tem o formato:
   ```
   jdbc:postgresql://db.xxxxxxxxxxxx.supabase.co:5432/postgres?sslmode=require
   ```
   `sslmode=require` é obrigatório — o Supabase não aceita conexão sem TLS.

## 2. API (Railway)

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

## Verificando

```bash
curl https://<seu-servico>.up.railway.app/actuator/health
```

Depois, atualize `EXPO_PUBLIC_API_URL` no app mobile para essa URL — e acrescente essa
origem em `CORS_ALLOWED_ORIGINS`. Se o app baixar o CSV por `fetch`, a origem de produção
também precisa entrar no CORS **do bucket**, que é configuração separada
(veja [Exportação de dados](exportacao-e-bi.md)).
