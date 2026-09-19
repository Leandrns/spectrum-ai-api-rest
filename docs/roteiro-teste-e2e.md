# Roteiro de teste end-to-end (completo)

Versão estendida do roteiro do [README](../README.md#testando-a-api), incluindo importação FIPE, exportação CSV e ingestão no BigQuery.

## 1. Registrar uma empresa + usuário admin

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

## 2. Fazer login (se precisar de um novo token)

```bash
curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "joao@empresa.com",
    "password": "Spectrum@2026!"
  }'
```

## 3. Testar autocomplete de veículos (público, sem token)

```bash
curl "http://localhost:8080/v1/vehicles/brands?q=toy"
```

> O catálogo já é populado com um seed mínimo pelas migrations. Para um catálogo completo a partir da FIPE, veja o passo 4.

## 4. (Opcional) Popular catálogo completo a partir da FIPE

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

## 5. Criar uma sessão de análise

```bash
curl -X POST http://localhost:8080/v1/sessions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "Análise Compactos 2026"}'
```

## 6. Disparar uma busca

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

## 7. Exportar em CSV

Exige o bucket configurado — veja [Exportação de dados](exportacao-e-bi.md).
A resposta traz uma URL temporária; baixe o arquivo dela:

```bash
curl "http://localhost:8080/v1/sessions/<id-da-sessao>/export?format=csv" \
  -H "Authorization: Bearer $TOKEN"
```

Chamar de novo sem nenhuma pesquisa nova reaproveita o mesmo arquivo no bucket e
devolve apenas uma URL nova — confira em `SELECT * FROM data_exports`.
