# Spectrum AI — Backend

API REST para a plataforma de análise competitiva automotiva Spectrum AI.

Construída com **Spring Boot 4**, **Java 21**, **PostgreSQL** e integração com **Gemini AI**.

---

## Tecnologias

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 4 |
| Segurança | Spring Security + JWT (JJWT 0.12) |
| Persistência | Spring Data JPA + PostgreSQL 16 |
| Migrations | Flyway |
| IA | Google Gemini 1.5 Pro |
| Documentação | SpringDoc OpenAPI (Swagger UI) |
| Build | Maven 3.9 |

---

## Pré-requisitos

| Ferramenta | Versão mínima | Necessário para |
|---|---|---|
| Docker Desktop | 24+ | Subir banco e API em contêiner |
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

Abra o `.env` e preencha os valores obrigatórios:

| Variável | Obrigatória | Descrição |
|---|---|---|
| `JWT_SECRET` | Sim | Segredo para assinar os tokens JWT. Deve ter no mínimo 256 bits em base64. |
| `GEMINI_API_KEY` | Sim | Chave de API do Google Gemini. Obter com o responsável pelo projeto. |
| `DATABASE_PASSWORD` | Recomendado | Senha do PostgreSQL. O default `spectrum` serve para dev local. |

**Gerando um `JWT_SECRET` seguro:**
```bash
# Linux / macOS
openssl rand -base64 64

# Windows (PowerShell)
[Convert]::ToBase64String((1..64 | ForEach-Object { [byte](Get-Random -Max 256) }))
```

> O arquivo `.env` nunca deve ser commitado — ele já está no `.gitignore`.

---

## Rodando a aplicação

### Opção A — Tudo no Docker (recomendado)

Sobe o banco de dados PostgreSQL e a API juntos:

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

---

### Opção B — Banco no Docker, API na IDE (para debug)

Útil quando você quer usar o debugger da IDE ou ter hot-reload mais rápido.

**1. Subir apenas o banco:**
```bash
docker compose up postgres
```

**2. Configurar as variáveis na run configuration da IDE:**

Adicione as seguintes variáveis de ambiente na configuração de execução:

```
SPRING_PROFILES_ACTIVE=dev
DATABASE_URL=jdbc:postgresql://localhost:5432/spectrum
DATABASE_USER=spectrum
DATABASE_PASSWORD=spectrum
JWT_SECRET=<seu-valor>
GEMINI_API_KEY=<sua-chave>
```

**3. Rodar a aplicação pela IDE** apontando para a classe `SpectrumAiApplication`.

---

## Verificando que a aplicação subiu

| Endpoint | Descrição |
|---|---|
| `http://localhost:8080/actuator/health` | Status da aplicação |
| `http://localhost:8080/swagger-ui.html` | Documentação interativa da API |
| `http://localhost:8080/v3/api-docs` | Spec OpenAPI em JSON |

---

## Estrutura de módulos

```
src/main/java/com/spectrumai/backend/
├── auth/          # Autenticação, JWT, registro e login
├── company/       # Gestão de empresas (multi-tenant)
├── user/          # Gestão de usuários e roles
├── session/       # Sessões de análise competitiva
├── search/        # Buscas de veículos e resultados
├── vehicles/      # Catálogo de veículos pré-cadastrados
├── insights/      # Geração de insights via IA
├── ai/            # Integração com provedores de IA (Gemini)
├── tenant/        # Isolamento de dados por tenant
└── config/        # Configurações gerais (Security, CORS, OpenAPI)
```

---

## Perfis de execução

| Perfil | Uso | Comportamento |
|---|---|---|
| `dev` | Desenvolvimento local | SQL logado, stack trace nos erros, log nível DEBUG |
| `prod` | Produção | SQL desativado, erros sem detalhes, log nível INFO |

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

## Migrations

As migrations ficam em `src/main/resources/db/migration/` e seguem o padrão Flyway `V{número}__{descrição}.sql`.

O Flyway executa automaticamente as migrations pendentes ao iniciar a aplicação. Para criar uma nova migration, adicione um arquivo com o próximo número de versão.
