# IA avançada no aparelho (LLM local) — design

Data: 2026-11-27 · Branch: `ia-llm` (a partir da `master` 1.20) · Continuação de [[Design - IA Financeira]].

## Intenção

Hoje as Perguntas do Assistente entendem 12 tipos fixos de pergunta, uma por vez, e respondem com frases prontas. O usuário quer que a IA entenda **várias perguntas na mesma mensagem** e responda **de forma inteligente e completa**, inclusive com conselho baseado nos dados. Exemplo real dele:

> "No que é melhor eu gastar menos? e em que eu gastei mais nesse mês, quero que me fale também no que gastei mais no mês passado"

A resposta esperada junta três coisas: ranking de categorias deste mês, ranking do mês passado e onde cortar (recomendações), num texto corrido e natural. Tudo continua funcionando **offline** depois de um download único, porque a promessa do app é que nada sai do aparelho. Também é parte do trabalho de SIN 323 (UFV, entrega em 2026-12-03): a comparação "sistema antigo × LLM treinado" vira resultado.

## Objetivos

- Entender mensagens com várias perguntas, frases livres e pedidos de conselho sobre as finanças da própria pessoa.
- Seguir a conversa: "e no mês passado?" usa o contexto das últimas perguntas (só em memória).
- **Todo número da resposta vem do cálculo do app**, nunca do modelo.
- Resposta completa em português, em poucos parágrafos curtos, no estilo do app.
- Indicação de "pensando…" enquanto gera; o texto aparece aos poucos (streaming).
- IA avançada **opcional**: a pessoa ativa e baixa o modelo dentro do app; pode apagar depois.
- Quem não ativar, ou tiver celular sem suporte, continua com as Perguntas atuais sem perceber diferença de comportamento.
- Treino feito no PC do usuário (RTX 4050 Laptop, 6 GB) e avaliação reproduzível, como o resto de `ia/`.

## Não-objetivos

- Conversa geral fora de finanças pessoais (receitas de bolo, notícias, código): recusa educada.
- Conselho sobre produtos financeiros específicos (qual CDB, qual ação).
- Criar, editar ou apagar lançamentos pelo chat.
- Buscar qualquer coisa na internet; a única conexão é o download do modelo.
- Rodar em ABIs que não sejam `arm64-v8a`.

## Anti-objetivos (seria fracasso mesmo funcionando)

- Resposta com valor inventado ou errado com cara de certo.
- Pergunta, resposta ou dado financeiro saindo do aparelho, ou o texto das perguntas sendo gravado.
- App travando, fechando por falta de memória ou esquentando o celular a ponto de atrapalhar o uso.
- APK crescendo: o modelo **não** vai dentro do APK.
- A IA avançada piorar o que hoje já funciona (perguntas simples ficando mais lentas sem ganho, ou errando o que o sistema atual acerta).

## Restrições

- Aparelho de referência: S20 FE (arm64, 6 GB de RAM), Android 13. `minSdk` 26.
- Tempo aceito pelo usuário: "alguns segundos", com indicador de que está pensando. Metas por modo na seção 2; "pensando…" aparece na hora.
- Tamanho: ~1 GB aceito pelo usuário; o modo Inteligente (~2,4 GB) avisa o tamanho antes de baixar.
- Hospedagem do modelo: arquivo em release do GitHub (`BrenaoRM/MotApp`, limite de 2 GB por arquivo), numa tag própria do modelo (ex.: `modelo-ia-v1`), para que releases do app não reenviem o modelo. O app guarda URL e SHA-256 do modelo esperado.
- UI segue [[Padrões de UI]]: `FolhaApp`/`CabecalhoDeFolha`, nunca `AlertDialog`, preto/violeta, mesmo layout nos 3 temas, pouco texto. **Protótipo HTML aprovado antes de mexer nas telas.**
- Regras de dados já existentes valem: dinheiro em centavos `Long`, data do hábito `dataCompra ?: data`, excluir pagamento de fatura e categoria Fatura, orçamento via `limiteDoMes`.
- Commits `1.0.158`, `1.0.159`…; push só quando o usuário pedir. Vault atualizado a cada tarefa.

## Abordagens consideradas

| | Como funciona | Troca |
|---|---|---|
| **A. Planejar → calcular → escrever (escolhida)** | O LLM transforma a mensagem numa lista de chamadas às ferramentas do app (as funções de `RespondedorDePerguntas`); o app calcula; o LLM escreve a resposta usando só os fatos calculados. | Números sempre corretos, perguntas compostas e conselho funcionam, reaproveita código testado. Exige treinar o modelo a gerar as chamadas. |
| B. Resumo no prompt | O app monta um resumo do mês e o LLM responde livremente. | Mais simples, mas o modelo pode inventar números que não estão no resumo, e o prompt grande deixa cada pergunta lenta no celular. |
| C. Sem LLM | Dividir a frase em pedaços e juntar as respostas prontas. | Rápido e leve, mas continua robótico e sem conselho de verdade; não atende ao pedido. |

O usuário mandou seguir com a recomendação: **A**.

## Desenho

### 1. Fluxo de uma pergunta

```
mensagem (+ últimas 2 trocas, só em memória)
  → Fase 1 "planejar": LLM gera JSON restrito por gramática
       [{"ferramenta":"ranking","mes":"2026-11"},
        {"ferramenta":"ranking","mes":"2026-10"},
        {"ferramenta":"como_economizar"}]
  → app valida e executa cada chamada (código Kotlin, determinístico)
       → lista de FATOS estruturados (texto curto com os valores já formatados)
  → Fase 2 "responder": LLM escreve a resposta usando só os FATOS (streaming na tela)
  → verificador: todo valor em R$ e % do texto precisa existir nos FATOS
       falhou → mostra a resposta montada pelas frases prontas de cada fato
```

- Lista vazia ou ferramenta `fora_do_escopo` → recusa curta ("Só consigo falar sobre os seus gastos e receitas.").
- As ferramentas são as 12 intenções atuais com parâmetros (`mes`, `categoria`, `mes_comparado`), mais as que o builder achar necessárias para os exemplos de teste (ex.: gasto num estabelecimento). Cada ferramenta devolve um fato estruturado, não um texto final.
- A Fase 1 usa gramática (GBNF do llama.cpp) para que o JSON seja sempre válido e só cite ferramentas e categorias que existem.

### 2. Modelo e treino (no PC)

- **Três modos, um só processo:** o mesmo script de dados, treino, exportação e avaliação roda para três tamanhos da mesma família. Proposta: Qwen3 (licença Apache 2.0, bom português) em 0.6B, 1.7B e 4B.
  - **Rápida (0.6B):** download de ~0,4 GB. Meta de até ~6 s por resposta no S20 FE.
  - **Média (1.7B):** ~1,1 GB. Meta de até ~15 s.
  - **Inteligente (4B):** ~2,3–2,5 GB. Meta de até ~30 s. É experimental: o arquivo passa do limite de 2 GB do GitHub e precisa ser dividido em partes, e pode não caber na memória do S20 FE.
  - Treina-se primeiro a Rápida, para validar o processo, depois a Média e por fim a Inteligente.
- **Só vai para o app o modo que passar** na avaliação de qualidade e no teste de memória e tempo no S20 FE.
  - Se nem os três passarem, fica um só, o mais inteligente que funcionar no celular.
  - Se o 4B não couber, "Inteligente" passa a ser o 1.7B e somem os nomes que sobrarem.
- A pessoa escolhe o modo na sheet de ativação e pode trocar depois. Trocar de modo baixa outro arquivo e apaga o anterior.
- Ajuste com LoRA (Unsloth, 4 bits) na RTX 4050 (6 GB; o 4B fica no limite da placa e usa lote pequeno). O PC fica ligado ~20 h/dia para treinar. Um único modelo por tamanho aprende as duas tarefas (planejar e escrever), com prompts diferentes.
- Dados sintéticos gerados por script em `ia/llm/`: combinações de perguntas simples e compostas, paráfrases, conversas com continuação ("e no mês passado?"), perguntas fora do escopo; respostas-alvo escritas a partir de fatos sintéticos. Nenhum dado real do usuário. Pode usar o Ollama local para gerar paráfrases.
- Exportar para GGUF quantizado (Q4_K_M ou similar).
- Avaliação (vira tabela do trabalho, uma coluna por modo + sistema atual), com conjunto de teste separado de ~50+ perguntas escritas à mão por mim (o usuário autorizou), nunca usadas no treino:
  - acerto exato do plano (lista de chamadas);
  - fidelidade: % de respostas que passam no verificador de números;
  - recusa correta fora do escopo;
  - comparação com o sistema atual nas mesmas perguntas;
  - tempo e memória no S20 FE.

### 3. Execução no celular

- llama.cpp compilado para `arm64-v8a` via NDK/CMake, chamado por JNI; geração em thread própria (fora da Main), com cancelamento.
- Modelo carregado sob demanda ao abrir as Perguntas e liberado ao sair do Assistente (ou após alguns minutos sem uso).
- Requisitos para oferecer a ativação: arm64 e RAM total ≥ 4 GB; senão o botão não aparece.
- Tempo limite da geração (ex.: 30 s) → cai no sistema atual.

### 4. Ativar e baixar

- Entrada no próprio Assistente (Perguntas) e em Ajustes: "IA avançada".
- Sheet de ativação: tamanho do download, aviso para usar Wi-Fi, barra de progresso, cancelar; download continua se a pessoa sair da tela (reaproveitar o mecanismo do `ApkDownloader`/`AtualizacaoService`).
- Arquivo salvo na pasta privada do app, conferido pelo SHA-256; arquivo incompleto ou errado é apagado.
- Opção de desativar e apagar o modelo (libera o espaço).
- Atualização do modelo: nova versão = nova URL/hash no app; o app oferece baixar de novo.

### 5. Tela das Perguntas

- Enquanto gera: indicador de "pensando…" no balão da resposta; o texto entra aos poucos.
- Sugestões de pergunta passam a incluir uma composta (ex.: "Onde gastei mais este mês e no anterior?").
- Detalhes visuais decididos no protótipo.

### 6. Fallback (sempre volta ao que existe hoje)

Modelo não baixado, celular sem suporte, erro ao carregar, falta de memória, tempo esgotado ou verificador reprovando → resposta do sistema atual (ou frases prontas dos fatos). Nada disso aparece como erro para a pessoa.

## Ordem (prazo: rodando de verdade no celular até 2026-12-04)

Trabalho no celular e treino no PC andam em paralelo:

| PC (GPU, ~20 h/dia) | App |
|---|---|
| 1. Ferramentas com fatos estruturados + pipeline no PC com Ollama (sem treino) | 1. llama.cpp via JNI rodando um GGUF base no S20 FE (mede tempo e memória cedo) |
| 2. Dados sintéticos + perguntas de teste | 2. Protótipo HTML (sheet de ativação com os modos, "pensando…") → aprovação |
| 3. Treino Rápida → avaliação → Média → Inteligente | 3. Download/ativação, pipeline completo, verificador, fallback |
| 4. Publicar os GGUF aprovados na release `modelo-ia-v1` | 4. Teste no S20 FE com os modelos treinados |

Corte se apertar: entregar só o modo mais inteligente que funcionar no celular (decisão do usuário).

## Decidido pelo usuário

- Usar a placa de vídeo dele para treinar.
- Download de ~0,5–1 GB é aceitável; ativação e download dentro do app.
- Alguns segundos de espera são aceitáveis, com indicação de que está pensando.
- Seguir com a abordagem recomendada (A).
- Três modos (Rápida, Média, Inteligente) feitos pelo mesmo processo; se não der, só a Inteligente.
- Tem que rodar de verdade no celular até 2026-12-04. O PC fica treinando ~20 h/dia.
- As perguntas de teste posso escrever eu.

## Decisões minhas (após revisão)

- **Verificador estrito:** cada fato é estruturado (`rotulo`, `periodo`, `valor`). Para passar, todo número da resposta (R$, %, contagens, dias, ordinais) precisa aparecer **na mesma frase** que o rótulo e o período do seu fato. Assim um valor certo atribuído à categoria errada é reprovado. A métrica de fidelidade do trabalho usa essa regra.
- **Streaming por frase:** o modelo gera escondido, e cada frase só aparece depois de passar no verificador. Se uma frase reprovar, a geração para e o resto da resposta é completado com as frases prontas dos fatos que ainda faltam. Para a pessoa, nada aparece como erro e nenhum texto já mostrado é trocado.
- **Categorias:** o nome é texto livre do usuário. Por isso a gramática da Fase 1 é montada a cada pergunta com as categorias daquela pessoa. O treino sorteia conjuntos de categorias (as padrão do app e outras inventadas), e a avaliação inclui nomes que não aparecem no treino.

## Deixado para o builder

- Modelo base e quantização finais (pela avaliação).
- Formato exato dos prompts, do JSON e dos fatos; conjunto final de ferramentas.
- Tamanho do conjunto sintético e hiperparâmetros do LoRA.
- Como integrar o llama.cpp (módulo Gradle separado ou não; versão fixada).
- Número de threads, tamanho de contexto, política de liberar memória.
- Texto e posição exata do indicador "pensando…" (via protótipo).
