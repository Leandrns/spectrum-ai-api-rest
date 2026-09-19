# Troubleshooting (avançado)

Os problemas mais comuns já estão no [README](../README.md#troubleshooting). Este arquivo cobre os casos mais específicos.

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
  [Exportação de dados](exportacao-e-bi.md)). Abrir a URL em nova aba ou via
  `window.location` não dispara CORS e funciona sem nenhuma configuração.
