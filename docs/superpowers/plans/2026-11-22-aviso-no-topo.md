# Aviso no topo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Trocar o Snackbar global por um aviso que desce do topo (✓ violeta para sucesso, ! coral para erro) e encurtar todas as mensagens.

**Architecture:** `LocalMensagem` passa a fornecer um `Mensageiro` (`mensagem(texto, erro = false)`), ligado a um `EstadoAviso` criado no `AppNavigation`. O componente `AvisoNoTopo` desenha o aviso num `Popup` (janela própria, fica acima dos `ModalBottomSheet`). As telas só mudam texto e marcam `erro = true`.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), JUnit 4 (`testDebugUnitTest`).

**Spec:** `docs/superpowers/specs/2026-11-22-aviso-no-topo-design.md` (textos novos na tabela de lá).

## Global Constraints
- Textos exatamente como a tabela da spec: sem "com sucesso", sem "!" e sem ponto final.
- Detalhes de exceção (`e.localizedMessage`) não aparecem no aviso; vão para `Log`.
- Sucesso 3000 ms, erro 5000 ms. Novo aviso substitui o atual.
- Cores do tema: fundo `colorScheme.surface`, borda 1dp `colorScheme.outline`, cantos 20dp (igual `CartaoUnicoDeLinhas`); violeta `RoxoHero`; coral `MaterialTheme.cores.textoGasto`.
- Proibido `Toast`, `AlertDialog`, `Snackbar` depois da mudança.
- Commits `1.0.N - descrição` (próximo livre: 1.0.126); não incluir `.idea/`. O último commit do push leva `1.16`.

## Review Focus
- Aviso disparado com um sheet aberto (Categorias dentro de Nova Transação): precisa aparecer por cima do sheet → `Popup`, verificado em aparelho.
- Dois avisos em menos de 3 s: o segundo substitui e reinicia o tempo (teste `mostrarDeNovoTrocaId`).
- Mensagem de backup repetida igual (ex.: dois "Falha ao enviar o backup"): o `LaunchedEffect` só dispara quando o texto muda; manter o comportamento atual, sem regressão.
- Aviso na tela de boas-vindas (sem Scaffold): usa o mesmo `AvisoNoTopo`.
- Arrastar para cima fecha; arrasto curto (< 25dp) volta para o lugar.

---

### Task 1: Estado e componente do aviso

**Files:**
- Create: `app/src/main/java/com/example/financacelular/ui/AvisoDePagina.kt`
- Modify: `app/src/main/java/com/example/financacelular/ui/PaginaComponentes.kt:9-10`
- Modify: `app/src/main/java/com/example/financacelular/ui/AppNavigation.kt:144-171,321-322` (remover `SnackbarHostState`/`SnackbarHost` e imports)
- Test: `app/src/test/java/com/example/financacelular/ui/EstadoAvisoTest.kt`

**Interfaces:**
- Produces:
  - `class Mensageiro(private val mostrar: (String, Boolean) -> Unit) { operator fun invoke(texto: String, erro: Boolean = false) }`
  - `val LocalMensagem = staticCompositionLocalOf { Mensageiro { _, _ -> } }`
  - `data class AvisoAtual(val texto: String, val erro: Boolean, val id: Long)`
  - `class EstadoAviso { var atual: AvisoAtual? (mutableStateOf, private set); fun mostrar(texto: String, erro: Boolean); fun fechar(id: Long) }` (`fechar` só limpa se `id` for o atual)
  - `fun duracaoDoAviso(erro: Boolean): Long`
  - `@Composable fun AvisoNoTopo(estado: EstadoAviso)`

- [ ] **Step 1: Teste** `EstadoAvisoTest`: `duracaoSucessoE3000` (`duracaoDoAviso(false) == 3000L`), `duracaoErroE5000`, `mostrarDeNovoTrocaId` (dois `mostrar` → `atual.texto` é o segundo e ids diferentes), `fecharIdAntigoNaoApagaAtual` (mostrar A, mostrar B, `fechar(idDeA)` → `atual` ainda é B), `fecharIdAtualLimpa`.
- [ ] **Step 2:** `./gradlew testDebugUnitTest --tests "*EstadoAvisoTest"` → FAIL (não compila).
- [ ] **Step 3:** Implementar `AvisoDePagina.kt` e trocar `LocalMensagem` em `PaginaComponentes.kt`. `AvisoNoTopo`: lê `WindowInsets.statusBars` fora do `Popup` e aplica como padding de topo dentro; `Popup(alignment = TopCenter, properties = PopupProperties(focusable = false))` mantido enquanto `MutableTransitionState` estiver visível ou animando; `AnimatedVisibility` com `slideInVertically(spring(dampingRatio = 0.7f)) { -it } + fadeIn` / `slideOutVertically { -it } + fadeOut`; guarda o último `AvisoAtual` para a animação de saída; `LaunchedEffect(atual?.id)` → `delay(duracaoDoAviso(erro))` → `fechar(id)`; arrasto vertical com `draggable` (só para cima), fecha se < -25dp, senão volta. Layout: margem lateral 10dp, padding 14×12dp, círculo 30dp com `Icons.Rounded.Check` / `Icons.Rounded.PriorityHigh` em 16dp sobre a cor com alpha 0.15, texto `bodyMedium` Medium `maxLines = 3`, barra de 2dp embaixo animada de 1→0 em `duracaoDoAviso` (cor violeta ou coral). Erro: borda `textoGasto` com alpha 0.35 sobre `outline`.
- [ ] **Step 4:** No `AppNavigation`, `val estadoAviso = remember { EstadoAviso() }`, `val mensagem = remember { Mensageiro(estadoAviso::mostrar) }`, `AvisoNoTopo(estadoAviso)` nos dois ramos (boas-vindas e principal); remover Snackbar. Ajustar `NovaTransacaoScreen.kt:811` para `aoErro = { texto -> mensagem(texto, erro = true) }`.
- [ ] **Step 5:** `./gradlew testDebugUnitTest` e `./gradlew assembleDebug` → PASS / BUILD SUCCESSFUL.
- [ ] **Step 6:** Commit `1.0.126 - Aviso no topo no lugar do Snackbar`.

### Task 2: Textos curtos e marcação de erro nas telas

**Files:**
- Modify: `ui/AppNavigation.kt` (~406, ~410), `ui/CartaoScreen.kt`, `ui/CategoriasScreen.kt`, `ui/InvestimentoScreen.kt`, `ui/NovaTransacaoScreen.kt`, `ui/NovaTransacaoViewModel.kt:157-188`, `ui/CalendarioScreen.kt:199-207,515-520,556,568,711-716`
- Test: `app/src/test/java/com/example/financacelular/ui/MensagensAgendaTest.kt`

**Interfaces:**
- Consumes: `Mensageiro.invoke(texto, erro)` (Task 1).
- Produces (em `CalendarioScreen.kt`, `internal`): `fun avisoAfazerSalvo(resultado: ResultadoCalendario?): Pair<String, Boolean>` e `fun mensagemEdicao(resultado: ResultadoCalendario, sucesso: String): Pair<String, Boolean>` (`second` = erro).

- [ ] **Step 1: Teste** `MensagensAgendaTest`: `avisoAfazerSalvo(null) == "Afazer salvo" to false`; `SUCESSO → "Salvo no Google Agenda" to false`; `SEM_PERMISSAO → "Salvo só no app: sem acesso à Agenda" to true`; `SEM_CALENDARIO → "Salvo só no app: conecte sua conta Google" to true`; `ERRO → "Salvo só no app: falha na Agenda" to true`; `mensagemEdicao(SUCESSO, "Evento excluído") == "Evento excluído" to false`; `SEM_PERMISSAO → "Sem acesso à Agenda" to true`; `SEM_CALENDARIO → "Nenhum calendário conectado" to true`; `ERRO → "Falha ao alterar na Agenda" to true`.
- [ ] **Step 2:** `./gradlew testDebugUnitTest --tests "*MensagensAgendaTest"` → FAIL.
- [ ] **Step 3:** Extrair/ajustar as duas funções e trocar todos os textos das telas listadas pela tabela da spec, passando `erro = true` nos marcados *(erro)*, incluindo "Valor maior que o saldo", "Dias devem ser de 1 a 31", "Edite pelo app Google Agenda", "Calendário só de leitura".
- [ ] **Step 4:** `./gradlew testDebugUnitTest` → PASS; `grep -rn "com sucesso\|mensagem(\"[^\"]*[.!]\")" app/src/main/java` → nenhum resultado.
- [ ] **Step 5:** Commit `1.0.127 - Mensagens curtas e erros em coral`.

### Task 3: Backup com textos curtos e erro marcado

**Files:**
- Modify: `ui/ConfiguracoesViewModel.kt` (todas as atribuições de `statusBackupMessage`, constante `MENSAGEM_EXPORT_FALHOU`), `ui/ConfiguracoesScreen.kt:128-132`

**Interfaces:**
- Consumes: `Mensageiro` (Task 1).
- Produces: `var statusBackupErro: Boolean` (mutableStateOf, private set) e `private fun status(texto: String, erro: Boolean = false)` que define os dois campos; toda atribuição direta vira `status(...)`.

- [ ] **Step 1:** Trocar textos pela tabela da spec; mensagens de progresso que terminam em "..." ficam como estão; exceções saem do texto e vão para `Log.w(TAG, ..., e)` onde ainda não houver log.
- [ ] **Step 2:** `ConfiguracoesScreen`: `mensagem(texto, erro = viewModel.statusBackupErro)`.
- [ ] **Step 3:** `./gradlew testDebugUnitTest assembleDebug lintDebug` → PASS, 0 erros de lint; `grep -n "localizedMessage" ui/ConfiguracoesViewModel.kt` só aparece dentro de `Log`.
- [ ] **Step 4:** Atualizar o vault (Histórico de Versões, Padrões de UI: linha "Feedback" e tabela, Decisões, Backlog).
- [ ] **Step 5:** Commit `1.16 - Aviso no topo e mensagens curtas` e push (pedido pelo usuário).
