# Pesquisa de specs

A ficha técnica é montada pelo Gemini com grounding (Google Search) a partir do
prompt versionado `vehicle_spec_search`, que vive na tabela `prompt_templates` —
não no código. `PromptServiceImpl` carrega a linha ativa daquele nome e substitui
os placeholders `{{brand}}`, `{{model}}`, `{{trim}}`, `{{year}}` e `{{categories}}`.

## Hierarquia de fontes

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

## Ano-modelo e rastreabilidade da tag

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

## Restrição de mercado e fontes proibidas

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

## As quatro tags de `source`

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

## Escolhendo o modelo

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

## Customizando o prompt

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
