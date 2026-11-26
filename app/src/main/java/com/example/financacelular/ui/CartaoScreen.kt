package com.example.financacelular.ui

import com.example.financacelular.data.somaEmReais
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.CartaoEntity
import com.example.financacelular.data.calcularVencimentoFatura
import com.example.financacelular.ui.theme.AzulCartao
import com.example.financacelular.ui.theme.Espaco
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.Verde
import com.example.financacelular.ui.theme.cores
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit


private fun formatarDataFatura(texto: String): String {
    val partes = texto.take(10).split("-")
    return if (partes.size == 3) "${partes[2]}/${partes[1]}/${partes[0]}" else texto
}

/** "sáb, 04 de out" — cai para dd/MM/yyyy se não conseguir interpretar a data. */
private fun formatarDiaAmigavel(texto: String): String =
    runCatching {
        LocalDate.parse(texto.take(10))
            .format(DateTimeFormatter.ofPattern("EEE, dd 'de' MMM", LocalePtBr))
            .replace(".", "")
            .replaceFirstChar { it.uppercase() }
    }.getOrElse { formatarDataFatura(texto) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartaoScreen(
    viewModel: CartaoViewModel = viewModel(),
    estadoLista: LazyListState
) {
    val mensagem = LocalMensagem.current
    val focusManager = LocalFocusManager.current
    val listaCartoesCarregada by viewModel.listaCartoes.collectAsStateWithLifecycle()
    val listaCartoes = listaCartoesCarregada.orEmpty()
    val categorias by viewModel.categorias.collectAsStateWithLifecycle()
    val categoriaPorId = remember(categorias) { categorias.associateBy { it.id } }
    val cartaoSelecionadoId by viewModel.cartaoSelecionadoId.collectAsStateWithLifecycle()
    val mesSelecionado by viewModel.mesSelecionado.collectAsStateWithLifecycle()
    val transacoesFaturaCarregada by viewModel.transacoesFatura.collectAsStateWithLifecycle()
    val transacoesFatura = transacoesFaturaCarregada.orEmpty()
    val faturaPaga by viewModel.faturaPaga.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(pageCount = { listaCartoes.size + 1 })

    // Sincroniza a página do Pager com o ViewModel
    LaunchedEffect(pagerState.currentPage, listaCartoes) {
        if (listaCartoes.isNotEmpty() && pagerState.currentPage < listaCartoes.size) {
            viewModel.selecionarCartao(listaCartoes[pagerState.currentPage].id)
        }
    }

    val formatoMoeda = remember { NumberFormat.getCurrencyInstance(LocalePtBr) }

    var mostrarConfig by rememberSaveable { mutableStateOf(false) }
    var cartaoParaEditarId by rememberSaveable { mutableStateOf<Long?>(null) }
    val cartaoParaEditar = listaCartoes.firstOrNull { it.id == cartaoParaEditarId }
    var confirmandoExclusao by rememberSaveable { mutableStateOf(false) }

    val valorTotalFatura = transacoesFatura.somaEmReais { it.valorCentavos }
    val naPaginaAdicionar = listaCartoes.isEmpty() || pagerState.currentPage >= listaCartoes.size

    // Avisa o ViewModel quando o carrossel está na página "Adicionar cartão" (a barra inferior troca o botão)
    LaunchedEffect(naPaginaAdicionar, listaCartoesCarregada != null) {
        if (listaCartoesCarregada != null) viewModel.definirNaPaginaAdicionar(naPaginaAdicionar)
    }

    // Ao criar um cartão, o carrossel vai para ele (a página "Adicionar" tem key própria e não acompanha o índice)
    var qtdCartoesAnterior by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(listaCartoes.size, listaCartoesCarregada != null) {
        if (listaCartoesCarregada != null) {
            val anterior = qtdCartoesAnterior
            if (anterior != null && listaCartoes.size > anterior) pagerState.animateScrollToPage(anterior)
            qtdCartoesAnterior = listaCartoes.size
        }
    }

    // Botão "Adicionar cartão" da barra inferior pede para abrir o formulário de novo cartão
    LaunchedEffect(Unit) {
        viewModel.pedidoAdicionarCartao.collect {
            cartaoParaEditarId = null
            confirmandoExclusao = false
            mostrarConfig = true
        }
    }

    // Agrupa os lançamentos por dia (mantém a ordem original)
    val lancamentosPorDia = remember(transacoesFatura) {
        transacoesFatura.groupBy { it.data.toString().take(10) }
    }

    ListaDePagina(estadoLista) {
        item {
            // CARROSSEL DE CARTÕES (cartão centralizado, na largura das demais seções)
            if (listaCartoesCarregada != null) HorizontalPager(
                state = pagerState,
                pageSpacing = 10.dp,
                key = { page -> listaCartoes.getOrNull(page)?.id ?: -1L }
            ) { page ->
                if (page < listaCartoes.size) {
                    val cartao = listaCartoes[page]
                    val isAtivo = cartao.id == cartaoSelecionadoId
                    val vencimento = remember(mesSelecionado, cartao) {
                        calcularVencimentoFatura(
                            anoMes = mesSelecionado,
                            diaVencimento = cartao.diaVencimento,
                            diaFechamento = cartao.diaFechamento
                        )
                    }

                    CartaoFatura(
                        cartao = cartao,
                        valorTotal = if (isAtivo) valorTotalFatura else 0.0,
                        faturaPaga = if (isAtivo) faturaPaga else false,
                        vencimento = vencimento,
                        formatoMoeda = formatoMoeda,
                        onAbrirConfig = {
                            cartaoParaEditarId = cartao.id
                            confirmandoExclusao = false
                            mostrarConfig = true
                        }
                    )
                } else {
                    CardAdicionarCartao(
                        onClick = {
                            cartaoParaEditarId = null
                            confirmandoExclusao = false
                            mostrarConfig = true
                        }
                    )
                }
            }

            if (listaCartoes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    IndicadorDePaginas(pagerState = pagerState)
                }
            }

            if (!naPaginaAdicionar) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = Tamanho.toque),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Lançamentos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (transacoesFatura.size == 1) "1 item" else "${transacoesFatura.size} itens",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (!naPaginaAdicionar) {
            if (transacoesFaturaCarregada?.isEmpty() == true) {
                item(key = "vazio") {
                    EstadoVazio(
                        icone = Icons.AutoMirrored.Outlined.ReceiptLong,
                        mensagem = "Não há compras na fatura deste mês para este cartão.",
                        modifier = Modifier.animateItem()
                    )
                }
            }

            if (transacoesFatura.isNotEmpty()) {
                // Um único cartão com todos os lançamentos, agrupados por dia (como as Categorias da Início)
                item(key = "lancamentos") {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            lancamentosPorDia.entries.forEachIndexed { indiceDia, (dia, lancamentos) ->
                                DiaDaFatura(
                                    titulo = formatarDiaAmigavel(dia),
                                    total = formatoMoeda.format(lancamentos.somaEmReais { it.valorCentavos }),
                                    primeiro = indiceDia == 0
                                )
                                lancamentos.forEachIndexed { indice, transacao ->
                                    if (indice > 0) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 70.dp, end = 14.dp),
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    val categoria = categoriaPorId[transacao.categoriaId]
                                    LinhaLancamento(
                                        icone = iconeParaCategoria(categoria?.nome.orEmpty(), categoria?.icone),
                                        titulo = transacao.descricao ?: "Compra no cartão",
                                        categoria = categoria?.nome,
                                        valor = formatoMoeda.format(transacao.valor)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // BOTTOM SHEET DE CONFIGURAÇÃO / CRIAÇÃO DE CARTÃO (a confirmação de exclusão troca o conteúdo dele)
    if (mostrarConfig) {
        var nomeCartao by rememberSaveable(cartaoParaEditarId) { mutableStateOf(cartaoParaEditar?.nome ?: "") }
        var diaFechamento by rememberSaveable(cartaoParaEditarId) { mutableStateOf(cartaoParaEditar?.diaFechamento?.toString() ?: "") }
        var diaVencimento by rememberSaveable(cartaoParaEditarId) { mutableStateOf(cartaoParaEditar?.diaVencimento?.toString() ?: "") }

        fun diaInvalido(valor: String): Boolean {
            val n = valor.toIntOrNull()
            return valor.isNotEmpty() && (n == null || n !in 1..31)
        }

        fun fecharConfig() {
            mostrarConfig = false
            confirmandoExclusao = false
        }

        LaunchedEffect(confirmandoExclusao) {
            if (confirmandoExclusao) focusManager.clearFocus()
        }

        FolhaApp(onDismiss = { fecharConfig() }) {
            AnimatedContent(
                targetState = confirmandoExclusao,
                transitionSpec = {
                    (fadeIn(tween(200)) togetherWith fadeOut(tween(150))) using SizeTransform()
                },
                label = "confirmarExclusaoCartao"
            ) { confirmando ->
                if (confirmando) {
                    ConfirmarAcaoConteudo(
                        titulo = "Excluir cartão?",
                        texto = "O cartão \"${cartaoParaEditar?.nome.orEmpty()}\" será excluído junto com todas as compras, parcelas e " +
                                "assinaturas lançadas nele. Os pagamentos de fatura já registrados no extrato " +
                                "são mantidos. Essa ação não pode ser desfeita.",
                        textoConfirmar = "Excluir",
                        onConfirmar = {
                            cartaoParaEditar?.let { cartao ->
                                viewModel.excluirCartao(cartao) {
                                    cartaoParaEditarId = null
                                    fecharConfig()
                                    mensagem("Cartão excluído")
                                }
                            }
                        },
                        onCancelar = { confirmandoExclusao = false }
                    )
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CabecalhoDeFolha(
                            titulo = if (cartaoParaEditar == null) "Novo cartão" else "Configurar cartão",
                            aoFechar = { fecharConfig() },
                            subtitulo = if (cartaoParaEditar == null) "Defina as datas da fatura"
                            else cartaoParaEditar.nome
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        CampoApp(valor = nomeCartao, aoMudar = { nomeCartao = it }, rotulo = "Nome")

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CampoApp(
                                valor = diaFechamento,
                                aoMudar = { diaFechamento = it.filter { c -> c.isDigit() }.take(2) },
                                rotulo = "Fecha dia",
                                textoApoio = if (diaInvalido(diaFechamento)) "Dia de 1 a 31" else null,
                                isError = diaInvalido(diaFechamento),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            CampoApp(
                                valor = diaVencimento,
                                aoMudar = { diaVencimento = it.filter { c -> c.isDigit() }.take(2) },
                                rotulo = "Vence dia",
                                textoApoio = if (diaInvalido(diaVencimento)) "Dia de 1 a 31" else null,
                                isError = diaInvalido(diaVencimento),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        BotaoPrincipal(
                            texto = "Salvar cartão",
                            onClick = {
                                val fechamentoInt = diaFechamento.toIntOrNull()?.takeIf { it in 1..31 }
                                val vencimentoInt = diaVencimento.toIntOrNull()?.takeIf { it in 1..31 }
                                if (nomeCartao.isNotBlank() && fechamentoInt != null && vencimentoInt != null) {
                                    viewModel.criarOuAtualizarCartao(
                                        cartaoId = cartaoParaEditar?.id,
                                        nome = nomeCartao.trim(),
                                        diaFechamento = fechamentoInt,
                                        diaVencimento = vencimentoInt
                                    ) {
                                        fecharConfig()
                                        mensagem("Cartão salvo")
                                    }
                                } else {
                                    mensagem("Dias devem ser de 1 a 31", erro = true)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            cor = AzulCartao,
                            corConteudo = Color.White,
                        )

                        if (cartaoParaEditar != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            BotaoSecundario(
                                texto = "Excluir cartão",
                                onClick = { confirmandoExclusao = true },
                                modifier = Modifier.fillMaxWidth(),
                                cor = MaterialTheme.cores.textoGasto,
                            )
                        }
                        Spacer(modifier = Modifier.height(Espaco.sm))
                    }
                }
            }
        }
    }
}

// ───────────────────────────── CARTÃO ─────────────────────────────

/** Altura mínima do cartão da fatura; o cartão "Adicionar" usa a mesma para o carrossel ficar alinhado. */
private val AlturaCartaoFatura = 168.dp

/**
 * Cartão da fatura no estilo do app: superfície com borda fina e brilho na cor do cartão (azul, verde
 * quando paga). A cor do banco aparece só na bolinha e no brilho; a engrenagem é um ícone solto.
 */
@Composable
private fun CartaoFatura(
    cartao: CartaoEntity,
    valorTotal: Double,
    faturaPaga: Boolean,
    vencimento: LocalDate,
    formatoMoeda: NumberFormat,
    modifier: Modifier = Modifier,
    onAbrirConfig: () -> Unit
) {
    val cor by animateColorAsState(
        targetValue = if (faturaPaga) Verde else AzulCartao,
        animationSpec = tween(300),
        label = "corFatura"
    )

    // Margem interna menor que a padrão: nome, valor e vencimento ficam perto dos cantos
    CartaoHeroDePagina(
        cor = cor,
        modifier = modifier.heightIn(min = AlturaCartaoFatura),
        padding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(cor, CircleShape))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                cartao.nome,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Toque de 48dp com efeito circular; o ícone fica solto, sem moldura. O deslocamento
            // leva o ícone (não o toque) à mesma distância da borda que a bolinha à esquerda.
            Box(
                modifier = Modifier
                    .offset(x = 12.dp)
                    .size(Tamanho.toque)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onAbrirConfig),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Configurar cartão",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Tamanho.icone)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Total da fatura",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        ValorDoHero(formatoMoeda.format(valorTotal))
        Spacer(modifier = Modifier.height(10.dp))
        EstadoFatura(faturaPaga = faturaPaga, vencimento = vencimento)
    }
}

/** Linha de estado da fatura: o vencimento é o estado (cinza, âmbar com 3 dias ou menos, coral se vencida, verde se paga). */
@Composable
private fun EstadoFatura(faturaPaga: Boolean, vencimento: LocalDate) {
    val dias = ChronoUnit.DAYS.between(LocalDate.now(), vencimento).toInt()
    val dia = vencimento.dayOfMonth
    val cinza = MaterialTheme.colorScheme.onSurfaceVariant

    val cor: Color
    val icone: ImageVector
    val texto: String
    when {
        faturaPaga -> {
            cor = MaterialTheme.cores.textoGanho
            icone = Icons.Outlined.Check
            texto = "Fatura paga"
        }
        dias < 0 -> {
            cor = MaterialTheme.cores.textoGasto
            icone = Icons.Outlined.Warning
            texto = if (dias == -1) "Venceu ontem, dia $dia" else "Venceu dia $dia, há ${-dias} dias"
        }
        dias == 0 -> {
            cor = MaterialTheme.cores.textoGasto
            icone = Icons.Outlined.Warning
            texto = "Vence hoje, dia $dia"
        }
        dias <= 3 -> {
            cor = MaterialTheme.cores.alerta
            icone = Icons.Outlined.Warning
            texto = if (dias == 1) "Vence amanhã, dia $dia" else "Vence dia $dia, em $dias dias"
        }
        else -> {
            cor = cinza
            icone = Icons.Outlined.CalendarToday
            texto = "Vence dia $dia, em $dias dias"
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(Tamanho.iconePequeno))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            texto,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = cor
        )
    }
}

@Composable
private fun CardAdicionarCartao(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val forma = RoundedCornerShape(20.dp)
    val corTraco = MaterialTheme.colorScheme.outline
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(AlturaCartaoFatura)
            .clip(forma)
            .drawBehind {
                drawRoundRect(
                    color = corTraco,
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                )
            }
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Tamanho.iconeGrande)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Adicionar cartão",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Pontos do carrossel: o ponto da página atual se alarga (movimento curto, sem mola). */
@Composable
private fun IndicadorDePaginas(pagerState: PagerState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pagerState.pageCount) { index ->
            val selecionado = pagerState.currentPage == index
            val largura by animateDpAsState(if (selecionado) 16.dp else 6.dp, tween(200), label = "larguraDot")
            val cor by animateColorAsState(
                if (selecionado) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                tween(200),
                label = "corDot"
            )
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(largura)
                    .clip(CircleShape)
                    .background(cor)
            )
        }
    }
}

// ───────────────────────────── FATURA ─────────────────────────────

/** Cabeçalho de um dia dentro do cartão de lançamentos: o dia à esquerda e o total do dia à direita. */
@Composable
private fun DiaDaFatura(titulo: String, total: String, primeiro: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = if (primeiro) 12.dp else 16.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            titulo,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            total,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Linha de lançamento: ícone solto e cinza (sem quadrado), nome, categoria e valor em cor neutra. */
@Composable
private fun LinhaLancamento(icone: ImageVector, titulo: String, categoria: String?, valor: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(44.dp), contentAlignment = Alignment.Center) {
            Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Tamanho.icone))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                titulo,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (categoria != null) {
                Text(
                    categoria,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            valor,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
