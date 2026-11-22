package com.example.financacelular.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.Categoria
import com.example.financacelular.data.FORMATO_DATA
import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.deteccao.PreenchimentoPendente
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.RoxoHero
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.Verde
import com.example.financacelular.ui.theme.cores
import com.example.financacelular.ui.theme.dimens
import java.text.Normalizer
import java.time.LocalDate

/** Quantas categorias aparecem na grade da tela; o resto fica em "Todas". */
private const val CATEGORIAS_NA_GRADE = 7
private const val COLUNAS_DA_GRADE = 4

private fun semAcentoMinusculo(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

/** Cartão padrão do app: fundo `surface`, borda de 1dp e cantos de 20dp. */
@Composable
private fun SecaoCard(
    modifier: Modifier = Modifier,
    conteudo: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = conteudo
        )
    }
}

/** Gasto/Ganho: o único lugar com cor da tela (coral e verde). Blocos de raio 12, como o SeletorQuadrado. */
@Composable
private fun SeletorGastoGanho(
    ehGasto: Boolean,
    aoSelecionar: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        OpcaoGastoGanho("Gasto", ehGasto, Coral, MaterialTheme.cores.textoGasto, Modifier.weight(1f)) { aoSelecionar(true) }
        OpcaoGastoGanho("Ganho", !ehGasto, Verde, MaterialTheme.cores.textoGanho, Modifier.weight(1f)) { aoSelecionar(false) }
    }
}

@Composable
private fun OpcaoGastoGanho(
    texto: String,
    ativo: Boolean,
    corFundo: Color,
    corTexto: Color,
    modifier: Modifier,
    aoClicar: () -> Unit
) {
    val fundo by animateColorAsState(
        targetValue = if (ativo) corFundo.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = tween(200),
        label = "fundoGastoGanho"
    )
    val borda by animateColorAsState(
        targetValue = if (ativo) corFundo else Color.Transparent,
        animationSpec = tween(200),
        label = "bordaGastoGanho"
    )
    val forma = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .height(Tamanho.toque - 4.dp)
            .clip(forma)
            .background(fundo)
            .border(1.dp, borda, forma)
            .selectable(selected = ativo, role = Role.Tab, onClick = aoClicar),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (ativo) FontWeight.Bold else FontWeight.SemiBold,
            color = if (ativo) corTexto else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Ladrilho da grade de categorias: ícone em cima, nome embaixo, 76dp de altura. */
@Composable
private fun LadrilhoDeEscolha(
    texto: String,
    icone: ImageVector,
    selecionado: Boolean,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier
) {
    BlocoDeEscolha(
        selecionado = selecionado,
        onClick = aoClicar,
        modifier = modifier.height(76.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icone,
                contentDescription = null,
                tint = if (selecionado) RoxoHero else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Tamanho.iconeGrande)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = texto,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Medium,
                color = if (selecionado) RoxoHero else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Linha de escolha com ícone e texto lado a lado (forma de pagamento, cartão). */
@Composable
private fun OpcaoComIcone(
    texto: String,
    icone: ImageVector?,
    selecionado: Boolean,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier
) {
    BlocoDeEscolha(selecionado = selecionado, onClick = aoClicar, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icone != null) {
                Icon(
                    imageVector = icone,
                    contentDescription = null,
                    tint = if (selecionado) RoxoHero else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Tamanho.icone)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = texto,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Medium,
                color = if (selecionado) RoxoHero else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LinhaSwitch(
    icone: ImageVector,
    titulo: String,
    subtitulo: String,
    marcado: Boolean,
    aoMudar: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .toggleable(value = marcado, role = Role.Switch, onValueChange = aoMudar)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Tamanho.icone)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                subtitulo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = marcado,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = RoxoHero
            )
        )
    }
}

/** Linha da lista "Todas as categorias": ícone, nome, check quando escolhida e lápis para editar. */
@Composable
private fun LinhaDaCategoria(
    categoria: Categoria,
    selecionada: Boolean,
    aoEscolher: () -> Unit,
    aoEditar: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { selected = selecionada },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(Tamanho.toque + 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(role = Role.RadioButton, onClickLabel = "Escolher ${categoria.nome}", onClick = aoEscolher),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (selecionada) RoxoHero.copy(alpha = 0.14f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconeParaCategoria(categoria.nome, categoria.icone),
                    contentDescription = null,
                    tint = if (selecionada) RoxoHero else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Tamanho.icone)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = categoria.nome,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selecionada) FontWeight.Bold else FontWeight.Medium,
                color = if (selecionada) RoxoHero else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (selecionada) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = "Escolhida",
                    tint = RoxoHero,
                    modifier = Modifier.size(Tamanho.icone)
                )
            }
        }
        IconButton(onClick = aoEditar, modifier = Modifier.size(Tamanho.toque)) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = "Editar ${categoria.nome}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Tamanho.icone)
            )
        }
    }
}

/** Lista completa de categorias do tipo, com busca. Escolher fecha o sheet. */
@Composable
private fun FolhaTodasCategorias(
    categorias: List<Categoria>,
    selecionadaId: Long?,
    aoEscolher: (Categoria) -> Unit,
    aoEditar: (Categoria) -> Unit,
    aoCriar: () -> Unit,
    aoFechar: () -> Unit
) {
    var busca by rememberSaveable { mutableStateOf("") }
    val termo = semAcentoMinusculo(busca.trim())
    val filtradas = if (termo.isEmpty()) categorias else categorias.filter { semAcentoMinusculo(it.nome).contains(termo) }

    FolhaApp(onDismiss = aoFechar) {
        CabecalhoDeFolha(titulo = "Categoria", aoFechar = aoFechar)
        Spacer(modifier = Modifier.height(12.dp))
        CampoApp(
            valor = busca,
            aoMudar = { busca = it },
            rotulo = "Buscar categoria",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )
        Spacer(modifier = Modifier.height(8.dp))
        filtradas.forEach { categoria ->
            LinhaDaCategoria(
                categoria = categoria,
                selecionada = categoria.id == selecionadaId,
                aoEscolher = { aoEscolher(categoria) },
                aoEditar = { aoEditar(categoria) }
            )
        }
        if (filtradas.isEmpty()) {
            Text(
                "Nenhuma categoria com esse nome.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Tamanho.toque + 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = aoCriar),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(RoxoHero.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = RoxoHero, modifier = Modifier.size(Tamanho.icone))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Criar categoria",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = RoxoHero
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NovaTransacaoScreen(
    viewModel: NovaTransacaoViewModel = viewModel(),
    tipoInicial: TipoTransacao = TipoTransacao.DESPESA,
    isParceladoInicial: Boolean = false,
    isRecorrenteInicial: Boolean = false,
    aoSalvar: () -> Unit = {},
    aoFechar: () -> Unit = {}
) {
    // Declarado antes dos outros efeitos: eles rodam em ordem, então o tipo inicial vale antes da detecção
    LaunchedEffect(tipoInicial) { viewModel.definirTipoInicial(tipoInicial) }

    LaunchedEffect(tipoInicial, isParceladoInicial, isRecorrenteInicial) {
        if (isParceladoInicial) {
            viewModel.onFormaPagamentoChange(FormaPagamento.CARTAO_CREDITO)
            viewModel.onParceladoChange(true)
        }
        if (isRecorrenteInicial) {
            if (tipoInicial == TipoTransacao.DESPESA) {
                viewModel.onFormaPagamentoChange(FormaPagamento.CARTAO_CREDITO)
            }
            viewModel.onRecorrenteChange(true)
        }
    }

    // Transação detectada numa notificação de banco: preenche o que o banco informou
    val transacaoDetectada by PreenchimentoPendente.atual.collectAsStateWithLifecycle()
    LaunchedEffect(transacaoDetectada) {
        transacaoDetectada?.let {
            viewModel.aplicarDetectada(it)
            PreenchimentoPendente.consumir()
        }
    }

    val categorias by viewModel.categorias.collectAsStateWithLifecycle()
    val listaCartoes by viewModel.listaCartoes.collectAsStateWithLifecycle()
    val categoriasViewModel: CategoriasViewModel = viewModel()
    val itensCategoria by categoriasViewModel.itens.collectAsStateWithLifecycle()

    // Um sheet por vez: nenhum, lista completa, editar (id) ou criar categoria
    var mostrarTodas by rememberSaveable { mutableStateOf(false) }
    var categoriaParaEditarId by rememberSaveable { mutableStateOf<Long?>(null) }
    var criandoCategoria by rememberSaveable { mutableStateOf(false) }
    // Nome da categoria recém-criada: quando ela chegar na lista, fica escolhida
    var escolherPorNome by rememberSaveable { mutableStateOf<String?>(null) }
    var mostrarDatePicker by rememberSaveable { mutableStateOf(false) }
    // Abrir a data com o teclado aberto: espera o teclado fechar antes de subir o calendário
    var pedindoData by remember { mutableStateOf(false) }
    val fecharTeclado = rememberFecharTeclado()
    LaunchedEffect(pedindoData) {
        if (pedindoData) {
            fecharTeclado()
            mostrarDatePicker = true
            pedindoData = false
        }
    }

    LaunchedEffect(listaCartoes) {
        if (viewModel.cartaoIdSelecionado == null && listaCartoes.isNotEmpty()) {
            viewModel.onCartaoSelecionado(listaCartoes.first().id)
        }
    }
    LaunchedEffect(categorias, escolherPorNome) {
        val nome = escolherPorNome ?: return@LaunchedEffect
        categorias.find { it.tipo == viewModel.tipo && it.nome.equals(nome, ignoreCase = true) }?.let {
            viewModel.onCategoriaChange(it)
            escolherPorNome = null
        }
    }

    val ehGasto = viewModel.tipo == TipoTransacao.DESPESA
    val ehCredito = ehGasto && viewModel.formaPagamento == FormaPagamento.CARTAO_CREDITO
    val categoriasDoTipo = categorias.filter { it.tipo == viewModel.tipo }.sortedBy { it.nome.lowercase() }

    // Grade: as mais usadas (sem a "Fatura", que é do sistema); a escolhida sempre aparece
    val usoPorCategoria = itensCategoria.orEmpty().associate { it.categoria.id to it.lancamentos }
    val naGrade = remember(categoriasDoTipo, usoPorCategoria, viewModel.categoriaSelecionada) {
        val ranking = categoriasDoTipo
            .filterNot { it.nome.equals("Fatura", ignoreCase = true) }
            .sortedWith(compareByDescending<Categoria> { usoPorCategoria[it.id] ?: 0 }.thenBy { it.nome.lowercase() })
            .take(CATEGORIAS_NA_GRADE)
        val escolhida = viewModel.categoriaSelecionada
        if (escolhida == null || ranking.any { it.id == escolhida.id } || categoriasDoTipo.none { it.id == escolhida.id }) {
            ranking
        } else {
            ranking.take(CATEGORIAS_NA_GRADE - 1) + escolhida
        }
    }

    val mensagem = LocalMensagem.current
    val hapticFeedback = LocalHapticFeedback.current

    val hoje = remember { LocalDate.now() }
    val ontem = remember { hoje.minusDays(1) }
    val scrollState = rememberScrollState()

    val focoValor = remember { FocusRequester() }
    val teclado = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focoValor.requestFocus()
        teclado?.show()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.dimens.paddingScreen)
                        .padding(top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // X solto, sem círculo (como nos sheets)
                    IconButton(onClick = aoFechar, modifier = Modifier.size(Tamanho.toque)) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Fechar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(Tamanho.iconeGrande)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Nova transação",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = MaterialTheme.dimens.paddingScreen)
                        .padding(top = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SeletorGastoGanho(
                        ehGasto = ehGasto,
                        aoSelecionar = { gasto ->
                            val novoTipo = if (gasto) TipoTransacao.DESPESA else TipoTransacao.RECEITA
                            if (viewModel.tipo != novoTipo) {
                                viewModel.definirTipoInicial(novoTipo)
                                viewModel.onCategoriaChange(null)
                                escolherPorNome = null
                            }
                        }
                    )

                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (ehGasto) "Quanto você gastou?" else "Quanto você recebeu?",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        CampoValor(
                            valor = viewModel.valor,
                            aoMudar = viewModel::onValorChange,
                            foco = focoValor
                        )
                    }

                    SecaoCard {
                        Text("Categoria", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val ladrilhos = naGrade.size + 1
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            (0 until ladrilhos).chunked(COLUNAS_DA_GRADE).forEach { linha ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    linha.forEach { posicao ->
                                        if (posicao < naGrade.size) {
                                            val categoria = naGrade[posicao]
                                            LadrilhoDeEscolha(
                                                texto = categoria.nome,
                                                icone = iconeParaCategoria(categoria.nome, categoria.icone),
                                                selecionado = viewModel.categoriaSelecionada?.id == categoria.id,
                                                aoClicar = { viewModel.onCategoriaChange(categoria) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        } else {
                                            LadrilhoDeEscolha(
                                                texto = "Todas",
                                                icone = Icons.Outlined.MoreHoriz,
                                                selecionado = false,
                                                aoClicar = { mostrarTodas = true },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                    repeat(COLUNAS_DA_GRADE - linha.size) { Spacer(modifier = Modifier.weight(1f)) }
                                }
                            }
                        }
                    }

                    // Data (campo + atalhos) e descrição
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1.3f)) {
                            CampoApp(
                                valor = viewModel.data.format(FORMATO_DATA),
                                aoMudar = {},
                                rotulo = "Data",
                                leitura = true,
                                trailingIcon = {
                                    Icon(
                                        Icons.Outlined.CalendarToday,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(Tamanho.icone)
                                    )
                                }
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable(role = Role.Button, onClickLabel = "Escolher a data") { pedindoData = true }
                            )
                        }
                        AtalhosDeValor(
                            opcoes = listOf("Hoje", "Ontem"),
                            selecionado = when (viewModel.data) {
                                hoje -> 0
                                ontem -> 1
                                else -> -1
                            },
                            aoSelecionar = { viewModel.onDataChange(if (it == 0) hoje else ontem) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    CampoApp(
                        valor = viewModel.descricao,
                        aoMudar = viewModel::onDescricaoChange,
                        rotulo = if (viewModel.ehRecorrente || viewModel.ehParcelado) "Nome (ex.: Salário, Netflix)" else "Descrição (opcional)",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )

                    if (ehGasto) {
                        SecaoCard {
                            Text("Forma de pagamento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth().selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OpcaoComIcone(
                                    texto = "Débito",
                                    icone = Icons.Outlined.AccountBalance,
                                    selecionado = viewModel.formaPagamento == FormaPagamento.DEBITO,
                                    aoClicar = { viewModel.onFormaPagamentoChange(FormaPagamento.DEBITO) },
                                    modifier = Modifier.weight(1f)
                                )
                                OpcaoComIcone(
                                    texto = "Crédito",
                                    icone = Icons.Outlined.CreditCard,
                                    selecionado = viewModel.formaPagamento == FormaPagamento.CARTAO_CREDITO,
                                    aoClicar = { viewModel.onFormaPagamentoChange(FormaPagamento.CARTAO_CREDITO) },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            AnimatedVisibility(visible = ehCredito) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (listaCartoes.isEmpty()) {
                                        Text(
                                            "Nenhum cartão cadastrado. Cadastre um em Cartões para lançar no crédito.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            "Cartão",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth().selectableGroup(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listaCartoes.forEach { cartao ->
                                                OpcaoComIcone(
                                                    texto = cartao.nome,
                                                    icone = null,
                                                    selecionado = viewModel.cartaoIdSelecionado == cartao.id,
                                                    aoClicar = { viewModel.onCartaoSelecionado(cartao.id) }
                                                )
                                            }
                                        }
                                    }
                                    LinhaSwitch(
                                        icone = Icons.Outlined.CreditCard,
                                        titulo = "Compra parcelada",
                                        subtitulo = "Dividir em várias vezes",
                                        marcado = viewModel.ehParcelado,
                                        aoMudar = viewModel::onParceladoChange
                                    )
                                    AnimatedVisibility(visible = viewModel.ehParcelado) {
                                        CampoApp(
                                            valor = viewModel.numeroParcelas,
                                            aoMudar = viewModel::onNumeroParcelasChange,
                                            rotulo = "Número de parcelas (2 a 60)",
                                            isError = viewModel.parcelasInvalidas,
                                            textoApoio = if (viewModel.parcelasInvalidas) "Use entre 2 e 60 parcelas" else null,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Number,
                                                imeAction = ImeAction.Done
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Recorrência: vale para ganho, débito e crédito
                    val diaDoMes = viewModel.data.dayOfMonth
                    val cartaoNome = listaCartoes.find { it.id == viewModel.cartaoIdSelecionado }?.nome
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinhaSwitch(
                            icone = Icons.Outlined.Repeat,
                            titulo = "Repetir todo mês",
                            subtitulo = when {
                                !ehGasto -> "Ex.: salário"
                                ehCredito -> "Assinatura no cartão"
                                else -> "Débito automático"
                            },
                            marcado = viewModel.ehRecorrente,
                            aoMudar = viewModel::onRecorrenteChange
                        )
                        AnimatedVisibility(visible = viewModel.ehRecorrente) {
                            Text(
                                text = when {
                                    !ehGasto -> "Todo dia $diaDoMes, entra no saldo."
                                    ehCredito && cartaoNome != null -> "Todo dia $diaDoMes, entra na fatura do $cartaoNome."
                                    ehCredito -> "Todo dia $diaDoMes, entra na fatura do cartão."
                                    else -> "Todo dia $diaDoMes, sai do saldo."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = MaterialTheme.dimens.paddingScreen)
                        .padding(top = 6.dp, bottom = 10.dp)
                ) {
                    val valorTexto = viewModel.valor.takeIf { it.isNotBlank() }
                        ?.replace(".", "")?.replace(',', '.')?.toDoubleOrNull()
                        ?.takeIf { it > 0.0 }
                        ?.let { "R$ " + "%,.2f".format(LocalePtBr, it) }
                    val textoBotao = when {
                        valorTexto == null -> if (viewModel.ehRecorrente) "Salvar recorrência" else "Salvar"
                        viewModel.ehRecorrente -> "Salvar recorrência de $valorTexto"
                        else -> "Salvar $valorTexto"
                    }
                    BotaoPrincipal(
                        texto = textoBotao,
                        onClick = {
                            viewModel.salvar(
                                nomeCategoriaDigitada = "",
                                aoErro = { texto -> mensagem(texto, erro = true) }
                            ) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                mensagem("Transação salva")
                                aoSalvar()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        cor = RoxoHero,
                        corConteudo = Color.White
                    )
                }
            }
        }

        // Sheets: um por vez
        if (mostrarTodas && categoriaParaEditarId == null && !criandoCategoria) {
            FolhaTodasCategorias(
                categorias = categoriasDoTipo,
                selecionadaId = viewModel.categoriaSelecionada?.id,
                aoEscolher = {
                    viewModel.onCategoriaChange(it)
                    mostrarTodas = false
                },
                aoEditar = {
                    mostrarTodas = false
                    categoriaParaEditarId = it.id
                },
                aoCriar = {
                    mostrarTodas = false
                    criandoCategoria = true
                },
                aoFechar = { mostrarTodas = false }
            )
        }

        itensCategoria?.find { it.categoria.id == categoriaParaEditarId }?.let { item ->
            FolhaEditarCategoria(
                item = item,
                tipoNova = item.categoria.tipo,
                nomesDoTipo = itensCategoria.orEmpty().filter { it.categoria.tipo == item.categoria.tipo }.map { it.categoria.nome },
                aoSalvar = { nome, icone ->
                    categoriasViewModel.salvar(item.categoria.id, nome, item.categoria.tipo, icone)
                    // mantém a categoria já escolhida com o nome e o ícone novos
                    if (viewModel.categoriaSelecionada?.id == item.categoria.id) {
                        viewModel.onCategoriaChange(item.categoria.copy(nome = nome, icone = icone))
                    }
                    categoriaParaEditarId = null
                    mensagem("Categoria salva")
                },
                aoExcluir = {
                    if (viewModel.categoriaSelecionada?.id == item.categoria.id) viewModel.onCategoriaChange(null)
                    categoriasViewModel.excluir(item.categoria)
                    categoriaParaEditarId = null
                    mensagem("Categoria excluída")
                },
                aoFechar = { categoriaParaEditarId = null },
                cor = RoxoHero,
                destinos = itensCategoria.orEmpty().map { it.categoria }.filter {
                    it.tipo == item.categoria.tipo && it.id != item.categoria.id && !it.nome.equals("Fatura", ignoreCase = true)
                },
                aoMoverEExcluir = { destino ->
                    if (viewModel.categoriaSelecionada?.id == item.categoria.id) viewModel.onCategoriaChange(destino)
                    categoriasViewModel.moverEExcluir(item.categoria, destino)
                    categoriaParaEditarId = null
                    mensagem("Lançamentos movidos para ${destino.nome}")
                }
            )
        }

        if (criandoCategoria) {
            FolhaEditarCategoria(
                item = null,
                tipoNova = viewModel.tipo,
                nomesDoTipo = categoriasDoTipo.map { it.nome },
                aoSalvar = { nome, icone ->
                    categoriasViewModel.salvar(null, nome, viewModel.tipo, icone)
                    escolherPorNome = nome.trim()
                    criandoCategoria = false
                    mensagem("Categoria criada")
                },
                aoExcluir = {},
                aoFechar = { criandoCategoria = false },
                cor = RoxoHero
            )
        }

        if (mostrarDatePicker) {
            FolhaData(
                data = viewModel.data,
                aoEscolher = { viewModel.onDataChange(it) },
                aoFechar = { mostrarDatePicker = false }
            )
        }
    }
}
