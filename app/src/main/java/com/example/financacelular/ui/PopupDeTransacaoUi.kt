package com.example.financacelular.ui

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Verde
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.financacelular.R
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.Categoria
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.deteccao.TransacaoDetectada
import com.example.financacelular.ui.theme.RoxoHero
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.cores
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.Normalizer

private const val CATEGORIAS_NA_GRADE = 7
private const val ETAPA_VALOR = 0
private const val ETAPA_CATEGORIA = 1
private const val ETAPA_DESCRICAO = 2
private const val ETAPA_CARTAO = 3 // só no crédito com mais de um cartão
private const val ETAPA_CONFIRMAR = 4
private const val ETAPA_SALVO = 5
/** Sem nenhum toque nesse tempo, o popup some e a transação vira notificação. */
private const val TEMPO_SEM_RESPOSTA_MS = 30_000L

/**
 * Conteúdo do popup de transação detectada (janela flutuante, ver `PopupDeTransacao`).
 * Etapas no mesmo cartão: valor, categoria, descrição, cartão (só no crédito com mais de um cartão) e confirmação;
 * depois um "Salvo" rápido.
 * Reaproveita a [NovaTransacaoViewModel] da tela Nova Transação, então salvar, a sugestão de categoria
 * e a memória "esta loja = esta categoria" funcionam igual.
 */
@Composable
fun PopupDeTransacaoConteudo(
    detectada: TransacaoDetectada,
    nomeBanco: String,
    aoPrecisarTeclado: (Boolean) -> Unit,
    aoResolver: () -> Unit,
    aoRemover: () -> Unit,
    aoExpirar: () -> Unit,
    aoMudarVoltar: ((() -> Unit)?) -> Unit
) {
    val context = LocalContext.current
    val viewModel = remember {
        NovaTransacaoViewModel(context.applicationContext as Application).also { it.aplicarDetectada(detectada) }
    }
    val categorias by viewModel.categorias.collectAsStateWithLifecycle()
    val cartoes by viewModel.listaCartoes.collectAsStateWithLifecycle()
    val repositorio = remember {
        FinancaRepository.getInstance(AppDatabase.getInstance(context.applicationContext))
    }
    val contagens by remember { repositorio.contarTransacoesPorCategoria() }.collectAsStateWithLifecycle(initialValue = emptyList())

    var etapa by remember { mutableIntStateOf(0) }
    var corrigindoValor by remember { mutableStateOf(false) }
    var verTodas by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }
    var visivel by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()

    val ehCredito = viewModel.tipo == TipoTransacao.DESPESA && viewModel.formaPagamento == FormaPagamento.CARTAO_CREDITO
    // Com um só cartão (ou nenhum) não há o que escolher: a etapa do cartão some
    val temEtapaCartao = ehCredito && cartoes.size > 1
    val totalDeEtapas = if (temEtapaCartao) 5 else 4
    val posicao = (if (!temEtapaCartao && etapa >= ETAPA_CONFIRMAR) etapa - 1 else etapa).coerceAtMost(totalDeEtapas - 1)

    // Quem chega a uma etapa pelo resumo (Editar) volta direto ao resumo ao confirmar
    var voltarAoResumo by remember { mutableStateOf(false) }
    fun avancar() {
        if (voltarAoResumo) {
            voltarAoResumo = false
            etapa = ETAPA_CONFIRMAR
        } else {
            etapa = if (etapa == ETAPA_DESCRICAO && !temEtapaCartao) ETAPA_CONFIRMAR else etapa + 1
        }
    }

    fun fechar() {
        aoResolver()
        escopo.launch {
            aoPrecisarTeclado(false)
            visivel = false
            delay(300)
            aoRemover()
        }
    }

    // Voltar uma etapa (seta do cabeçalho e botão voltar do sistema)
    val voltarEtapa: (() -> Unit)? = if (etapa in ETAPA_CATEGORIA..ETAPA_CONFIRMAR) {
        {
            erro = null
            verTodas = false
            voltarAoResumo = false
            etapa = if (etapa == ETAPA_CONFIRMAR && !temEtapaCartao) ETAPA_DESCRICAO else etapa - 1
        }
    } else null

    // O voltar do sistema só chega à janela nas etapas com teclado (só nelas a janela tem foco)
    val acaoDoVoltar: (() -> Unit)? = voltarEtapa ?: if (corrigindoValor) {
        { corrigindoValor = false }
    } else null
    SideEffect { aoMudarVoltar(acaoDoVoltar) }

    LaunchedEffect(Unit) { visivel = true }
    // Ninguém tocou no popup: some sozinho e a transação fica na notificação, para não se perder
    var tocou by remember { mutableStateOf(false) }
    LaunchedEffect(tocou) {
        if (!tocou) {
            delay(TEMPO_SEM_RESPOSTA_MS)
            aoExpirar()
            fechar()
        }
    }
    // Só pede o foco da janela (e o teclado) enquanto há um campo de texto na tela
    LaunchedEffect(etapa, corrigindoValor) { aoPrecisarTeclado(corrigindoValor || etapa == ETAPA_DESCRICAO) }
    LaunchedEffect(etapa) {
        if (etapa == ETAPA_SALVO) {
            delay(1100)
            fechar()
        }
    }

    // Crédito sem nenhum cartão cadastrado não teria como salvar (e a transação se perderia): lança no débito
    LaunchedEffect(Unit) {
        if (detectada.formaPagamento == FormaPagamento.CARTAO_CREDITO && repositorio.listarTodosCartoesSync().isEmpty()) {
            viewModel.onFormaPagamentoChange(FormaPagamento.DEBITO)
        }
    }
    LaunchedEffect(ehCredito, cartoes) {
        if (ehCredito && viewModel.cartaoIdSelecionado == null) {
            (cartaoDoBanco(cartoes.map { it.id to it.nome }, nomeBanco) ?: cartoes.firstOrNull()?.id)
                ?.let { viewModel.onCartaoSelecionado(it) }
        }
    }
    val cartaoDoLancamento = cartoes.firstOrNull { it.id == viewModel.cartaoIdSelecionado }

    val usoPorCategoria = remember(contagens) { contagens.associate { it.categoriaId to it.total } }
    val doTipo = remember(categorias, viewModel.tipo) {
        categorias
            .filter { it.tipo == viewModel.tipo && !it.nome.equals("Fatura", ignoreCase = true) }
            .sortedWith(compareByDescending<Categoria> { usoPorCategoria[it.id] ?: 0 }.thenBy { it.nome.lowercase() })
    }
    val naGrade = remember(doTipo, viewModel.categoriaSelecionada) {
        val ranking = doTipo.take(CATEGORIAS_NA_GRADE)
        val escolhida = viewModel.categoriaSelecionada
        if (escolhida == null || ranking.any { it.id == escolhida.id } || doTipo.none { it.id == escolhida.id }) {
            ranking
        } else {
            ranking.take(CATEGORIAS_NA_GRADE - 1) + escolhida
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 16.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (!tocou) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        tocou = true
                    }
                }
            }
    ) {
        AnimatedVisibility(
            visible = visivel,
            enter = slideInVertically(tween(220)) { -it } + fadeIn(tween(150)),
            exit = slideOutVertically(tween(250)) { -it } + fadeOut(tween(200))
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp)) {
                    CabecalhoDoPopup(
                        nomeBanco = nomeBanco,
                        posicao = posicao,
                        total = totalDeEtapas,
                        aoVoltar = voltarEtapa,
                        aoFechar = ::fechar
                    )
                    Spacer(Modifier.height(4.dp))
                    AnimatedContent(
                        targetState = etapa,
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) using SizeTransform() },
                        label = "etapaDoPopup"
                    ) { atual ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            when (atual) {
                                ETAPA_VALOR -> EtapaValor(
                                    detectada = detectada,
                                    valor = viewModel.valor,
                                    corrigindo = corrigindoValor,
                                    aoCorrigir = { corrigindoValor = true },
                                    aoMudarValor = viewModel::onValorChange,
                                    aoConfirmar = {
                                        if (viewModel.valor.isBlank()) {
                                            erro = "Informe o valor."
                                        } else {
                                            erro = null
                                            corrigindoValor = false
                                            avancar()
                                        }
                                    },
                                    erro = erro
                                )
                                ETAPA_CATEGORIA -> EtapaCategoria(
                                    categorias = if (verTodas) doTipo.sortedBy { it.nome.lowercase() } else naGrade,
                                    selecionada = viewModel.categoriaSelecionada,
                                    mostrandoTodas = verTodas,
                                    aoVerTodas = { verTodas = true },
                                    aoEscolher = {
                                        viewModel.onCategoriaChange(it)
                                        erro = null
                                    },
                                    aoConfirmar = ::avancar
                                )
                                ETAPA_DESCRICAO -> EtapaDescricao(
                                    descricao = viewModel.descricao,
                                    aoMudar = viewModel::onDescricaoChange,
                                    aoContinuar = ::avancar
                                )
                                ETAPA_CARTAO -> EtapaCartao(
                                    cartoes = cartoes.map { it.id to it.nome },
                                    selecionado = viewModel.cartaoIdSelecionado,
                                    aoEscolher = viewModel::onCartaoSelecionado,
                                    aoConfirmar = ::avancar
                                )
                                ETAPA_CONFIRMAR -> EtapaConfirmar(
                                    viewModel = viewModel,
                                    cartao = cartaoDoLancamento?.nome,
                                    escolheuCartao = temEtapaCartao,
                                    erro = erro,
                                    salvando = viewModel.salvando,
                                    aoEditar = {
                                        voltarAoResumo = true
                                        etapa = it
                                    },
                                    aoCancelar = ::fechar,
                                    aoSalvar = {
                                        viewModel.salvar(
                                            nomeCategoriaDigitada = "",
                                            aoErro = { erro = it },
                                            aoSalvarComSucesso = {
                                                aoResolver()
                                                etapa = ETAPA_SALVO
                                            }
                                        )
                                    }
                                )
                                else -> EtapaSalvo(viewModel.tipo, viewModel.valor)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CabecalhoDoPopup(nomeBanco: String, posicao: Int, total: Int, aoVoltar: (() -> Unit)?, aoFechar: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 36.dp)) {
        // Espaço fixo à esquerda: a logo dá lugar à seta nas etapas 2 a 4, sem empurrar o resto do cabeçalho
        if (aoVoltar != null) {
            Box(
                modifier = Modifier
                    .size(Tamanho.toque)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = aoVoltar),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Voltar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Tamanho.icone)
                )
            }
        } else {
            Box(modifier = Modifier.size(Tamanho.toque), contentAlignment = Alignment.Center) {
                val forma = RoundedCornerShape(7.dp)
                Box(modifier = Modifier.size(22.dp).clip(forma).semantics { contentDescription = "MotApp" }) {
                    Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, modifier = Modifier.size(22.dp))
                    Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }
        }
        Text(
            nomeBanco,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.semantics { contentDescription = "Etapa ${posicao + 1} de $total" }
        ) {
            repeat(total) { i ->
                Box(
                    Modifier
                        .size(width = 14.dp, height = 3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i <= posicao) RoxoHero else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f))
                )
            }
        }
        Box(
            modifier = Modifier
                .size(Tamanho.toque)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = aoFechar),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Fechar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Tamanho.icone)
            )
        }
    }
}

@Composable
private fun EtapaValor(
    detectada: TransacaoDetectada,
    valor: String,
    corrigindo: Boolean,
    aoCorrigir: () -> Unit,
    aoMudarValor: (String) -> Unit,
    aoConfirmar: () -> Unit,
    erro: String?
) {
    val ehGasto = detectada.tipo == TipoTransacao.DESPESA
    val forma = when (detectada.formaPagamento) {
        FormaPagamento.CARTAO_CREDITO -> "crédito"
        FormaPagamento.DEBITO -> "débito"
        FormaPagamento.DINHEIRO -> "dinheiro"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // Pílula do tipo: mesma cor do seletor Gasto/Ganho da Nova Transação
        val corTipo = if (ehGasto) MaterialTheme.cores.textoGasto else MaterialTheme.cores.textoGanho
        Row(
            modifier = Modifier
                .background((if (ehGasto) Coral else Verde).copy(alpha = 0.16f), CircleShape)
                .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (ehGasto) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                contentDescription = null,
                tint = corTipo,
                modifier = Modifier.size(Tamanho.iconePequeno)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (ehGasto) "Gasto" else "Ganho",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = corTipo
            )
        }
        Text(
            listOfNotNull(detectada.descricao, forma).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
    if (corrigindo) {
        val foco = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(150) // espera a janela ganhar foco para o teclado abrir
            runCatching { foco.requestFocus() }
        }
        CampoValor(valor = valor, aoMudar = aoMudarValor, foco = foco)
    } else {
        // Mesmo molde do CampoValor (R$ pequeno e esmaecido + número grande), para o valor não mudar de forma ao corrigir
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "R$ ",
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 20.sp),
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                formatarValorDoPopup(valor),
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 34.sp),
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
    if (erro != null) TextoDeErro(erro)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!corrigindo) BotaoSecundario("Corrigir", aoCorrigir, modifier = Modifier.weight(0.8f))
        BotaoPrincipal("Confirmar valor", aoConfirmar, modifier = Modifier.weight(1f), cor = RoxoHero)
    }
}

@Composable
private fun EtapaCategoria(
    categorias: List<Categoria>,
    selecionada: Categoria?,
    mostrandoTodas: Boolean,
    aoVerTodas: () -> Unit,
    aoEscolher: (Categoria) -> Unit,
    aoConfirmar: () -> Unit
) {
    Text("Qual a categoria?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    // Mesma grade de 4 colunas da Nova Transação; "Todas" abre a lista completa com rolagem
    val itens: List<Categoria?> = if (mostrandoTodas) categorias else categorias + null
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .verticalScroll(rememberScrollState())
    ) {
        itens.chunked(4).forEach { linha ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                linha.forEach { categoria ->
                    val escolhida = categoria != null && categoria.id == selecionada?.id
                    BlocoDeEscolha(
                        selecionado = escolhida,
                        onClick = { if (categoria == null) aoVerTodas() else aoEscolher(categoria) },
                        modifier = Modifier.weight(1f).height(72.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (categoria == null) {
                                    Icons.Outlined.GridView
                                } else {
                                    iconeParaCategoria(categoria.nome, categoria.icone)
                                },
                                contentDescription = null,
                                tint = if (escolhida) RoxoHero else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(Tamanho.icone)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                categoria?.nome ?: "Todas",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (escolhida) FontWeight.Bold else FontWeight.Medium,
                                color = if (escolhida) RoxoHero else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                repeat(4 - linha.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    // Tocar só marca a categoria (a sugerida já vem marcada); o botão confirma, para corrigir um toque errado
    BotaoPrincipal(
        texto = if (selecionada != null) "Confirmar ${selecionada.nome}" else "Escolha uma categoria",
        onClick = aoConfirmar,
        modifier = Modifier.fillMaxWidth(),
        cor = RoxoHero,
        enabled = selecionada != null
    )
}

@Composable
private fun EtapaDescricao(descricao: String, aoMudar: (String) -> Unit, aoContinuar: () -> Unit) {
    CampoApp(valor = descricao, aoMudar = aoMudar, rotulo = "Descrição", modifier = Modifier.fillMaxWidth())
    BotaoPrincipal("Continuar", aoContinuar, modifier = Modifier.fillMaxWidth(), cor = RoxoHero)
}

/** Escolha do cartão do crédito: blocos de 2 por linha, como a forma de pagamento da Nova Transação. */
@Composable
private fun EtapaCartao(
    cartoes: List<Pair<Long, String>>,
    selecionado: Long?,
    aoEscolher: (Long) -> Unit,
    aoConfirmar: () -> Unit
) {
    Text("Qual cartão?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .verticalScroll(rememberScrollState())
    ) {
        cartoes.chunked(2).forEach { linha ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                linha.forEach { (id, nome) ->
                    val escolhido = id == selecionado
                    BlocoDeEscolha(
                        selecionado = escolhido,
                        onClick = { aoEscolher(id) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.CreditCard,
                                contentDescription = null,
                                tint = if (escolhido) RoxoHero else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(Tamanho.icone)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                nome,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (escolhido) FontWeight.Bold else FontWeight.Medium,
                                color = if (escolhido) RoxoHero else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                repeat(2 - linha.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    val nomeEscolhido = cartoes.firstOrNull { it.first == selecionado }?.second
    BotaoPrincipal(
        texto = if (nomeEscolhido != null) "Confirmar $nomeEscolhido" else "Escolha um cartão",
        onClick = aoConfirmar,
        modifier = Modifier.fillMaxWidth(),
        cor = RoxoHero,
        enabled = nomeEscolhido != null
    )
}

@Composable
private fun EtapaConfirmar(
    viewModel: NovaTransacaoViewModel,
    cartao: String?,
    escolheuCartao: Boolean,
    erro: String?,
    salvando: Boolean,
    aoEditar: (Int) -> Unit,
    aoCancelar: () -> Unit,
    aoSalvar: () -> Unit
) {
    val ehGasto = viewModel.tipo == TipoTransacao.DESPESA
    val pagamento = when (viewModel.formaPagamento) {
        // Quando há uma linha própria para o cartão, o subtítulo só diz "Crédito"
        FormaPagamento.CARTAO_CREDITO -> "Crédito" + (if (escolheuCartao) "" else cartao?.let { " · $it" } ?: "")
        FormaPagamento.DEBITO -> "Débito"
        FormaPagamento.DINHEIRO -> "Dinheiro"
    }
    Text("Confirmar lançamento", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
    ) {
        LinhaDoResumo(
            "Valor",
            (if (ehGasto) "− " else "+ ") + "R$ ${formatarValorDoPopup(viewModel.valor)}",
            if (ehGasto) MaterialTheme.cores.textoGasto else MaterialTheme.cores.textoGanho,
            subtitulo = pagamento
        ) { aoEditar(0) }
        DivisorDoResumo()
        LinhaDoResumo("Categoria", viewModel.categoriaSelecionada?.nome ?: "Escolher") { aoEditar(1) }
        DivisorDoResumo()
        LinhaDoResumo("Descrição", viewModel.descricao.ifBlank { "Sem descrição" }) { aoEditar(2) }
        if (escolheuCartao) {
            DivisorDoResumo()
            LinhaDoResumo("Cartão", cartao ?: "Escolher") { aoEditar(ETAPA_CARTAO) }
        }
    }
    if (erro != null) TextoDeErro(erro)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BotaoSecundario("Cancelar", aoCancelar, modifier = Modifier.weight(0.8f))
        BotaoPrincipal(
            "Salvar R$ ${formatarValorDoPopup(viewModel.valor)}",
            aoSalvar,
            modifier = Modifier.weight(1f),
            cor = RoxoHero,
            enabled = !salvando
        )
    }
}

@Composable
private fun DivisorDoResumo() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
}

@Composable
private fun LinhaDoResumo(
    rotulo: String,
    valor: String,
    corValor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    subtitulo: String? = null,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .then(
                if (onClick != null) Modifier.clickable(onClickLabel = "Editar $rotulo", role = Role.Button, onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            rotulo,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                valor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = corValor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitulo != null) {
                Text(
                    subtitulo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (onClick != null) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = "Editar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Tamanho.iconePequeno)
            )
        }
    }
}

@Composable
private fun EtapaSalvo(tipo: TipoTransacao, valor: String) {
    val texto = (if (tipo == TipoTransacao.DESPESA) "Gasto salvo" else "Ganho salvo") + " · R$ " + formatarValorDoPopup(valor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Verde.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.cores.textoGanho,
                modifier = Modifier.size(Tamanho.icone)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(texto, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TextoDeErro(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.cores.textoGasto,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}

/** Id do cartão cujo nome bate com o do banco que avisou ("Inter Black" ↔ "Inter"), sem ligar para maiúsculas e acentos. */
internal fun cartaoDoBanco(cartoes: List<Pair<Long, String>>, nomeBanco: String): Long? {
    val banco = semAcentoMinusculo(nomeBanco.trim())
    if (banco.isEmpty()) return null
    return cartoes.firstOrNull { (_, nome) ->
        val cartao = semAcentoMinusculo(nome.trim())
        cartao.isNotEmpty() && (cartao.contains(banco) || banco.contains(cartao))
    }?.first
}

private fun semAcentoMinusculo(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

/** "1234,56" (formato do campo) -> "1.234,56". */
private fun formatarValorDoPopup(valor: String): String {
    val centavos = valor.replace(",", ".").toDoubleOrNull()?.let { Math.round(it * 100) } ?: return valor
    val reais = (centavos / 100).toString().reversed().chunked(3).joinToString(".").reversed()
    return "$reais,${(centavos % 100).toString().padStart(2, '0')}"
}
