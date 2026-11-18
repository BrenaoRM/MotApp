package com.example.financacelular.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.financacelular.deteccao.PreenchimentoPendente
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.Verde
import com.example.financacelular.ui.theme.cores
import com.example.financacelular.ui.theme.dimens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private fun formatarValorMoeda(entrada: String): String {
    val digitos = entrada.filter { it.isDigit() }
    if (digitos.isEmpty()) return ""
    val digitosLimitados = digitos.take(9)
    val valorLong = digitosLimitados.toLongOrNull() ?: 0L
    val centavos = (valorLong % 100).toString().padStart(2, '0')
    val reaisStr = (valorLong / 100).toString()

    val reaisFormatado = buildString {
        for ((indice, caractere) in reaisStr.withIndex()) {
            val posicaoDaDireita = reaisStr.length - indice
            append(caractere)
            if (posicaoDaDireita > 1 && posicaoDaDireita % 3 == 1) {
                append('.')
            }
        }
    }
    return "$reaisFormatado,$centavos"
}

@Composable
private fun SecaoCard(
    modifier: Modifier = Modifier,
    conteudo: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = conteudo
        )
    }
}

@Composable
private fun IconeEmCirculo(icone: ImageVector, cor: Color, tamanho: Dp = 40.dp) {
    Box(
        modifier = Modifier
            .size(tamanho)
            .background(cor.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icone,
            contentDescription = null,
            tint = cor,
            modifier = Modifier.size(tamanho * 0.5f)
        )
    }
}

/** Chip de categoria (só fundo, sem borda). Toque longo abre a exclusão quando [aoTocarLongo] existe. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChipCategoria(
    texto: String,
    icone: ImageVector,
    selecionado: Boolean,
    cor: Color,
    aoClicar: () -> Unit,
    role: Role = Role.RadioButton,
    aoTocarLongo: (() -> Unit)? = null
) {
    val fundo by animateColorAsState(
        targetValue = if (selecionado) cor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        animationSpec = tween(200),
        label = "fundoChipCategoria"
    )
    Row(
        modifier = Modifier
            .heightIn(min = Tamanho.toque)
            .clip(RoundedCornerShape(50))
            .background(fundo)
            .semantics { selected = selecionado }
            .combinedClickable(
                role = role,
                onLongClickLabel = if (aoTocarLongo != null) "Excluir categoria $texto" else null,
                onLongClick = aoTocarLongo,
                onClick = aoClicar
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icone, contentDescription = null, tint = cor, modifier = Modifier.size(Tamanho.icone))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = texto,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Medium,
            color = if (selecionado) cor else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun OpcaoPagamento(
    modifier: Modifier,
    icone: ImageVector,
    titulo: String,
    selecionado: Boolean,
    cor: Color,
    aoClicar: () -> Unit
) {
    val corFundo by animateColorAsState(
        targetValue = if (selecionado) cor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        animationSpec = tween(200),
        label = "fundoPagamento"
    )
    val corConteudo = if (selecionado) cor else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .height(Tamanho.toque)
            .clip(RoundedCornerShape(14.dp))
            .background(corFundo)
            .selectable(selected = selecionado, role = Role.RadioButton, onClick = aoClicar),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icone, contentDescription = null, tint = corConteudo, modifier = Modifier.size(Tamanho.icone))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = titulo,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Medium,
            color = corConteudo
        )
    }
}

@Composable
private fun LinhaSwitch(
    icone: ImageVector,
    cor: Color,
    titulo: String,
    subtitulo: String,
    marcado: Boolean,
    aoMudar: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .toggleable(value = marcado, role = Role.Switch, onValueChange = aoMudar)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconeEmCirculo(icone = icone, cor = cor, tamanho = 34.dp)
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
                checkedTrackColor = cor
            )
        )
    }
}

@Composable
private fun PilulaData(texto: String, selecionada: Boolean, cor: Color, aoClicar: () -> Unit) {
    val fundo by animateColorAsState(
        targetValue = if (selecionada) cor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        animationSpec = tween(200),
        label = "fundoPilulaData"
    )
    Box(
        modifier = Modifier
            .heightIn(min = Tamanho.toque)
            .clip(RoundedCornerShape(50))
            .background(fundo)
            .selectable(selected = selecionada, role = Role.RadioButton, onClick = aoClicar)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selecionada) FontWeight.Bold else FontWeight.Medium,
            color = if (selecionada) cor else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NovaTransacaoScreen(
    viewModel: NovaTransacaoViewModel = viewModel(),
    tipoInicial: TipoTransacao = TipoTransacao.DESPESA,
    isParceladoInicial: Boolean = false,
    isRecorrenteInicial: Boolean = false,
    aoSalvar: () -> Unit = {},
    aoFechar: () -> Unit = {}
) {
    remember(tipoInicial) { viewModel.definirTipoInicial(tipoInicial) }

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
    val transacaoDetectada by PreenchimentoPendente.atual.collectAsState()
    LaunchedEffect(transacaoDetectada) {
        transacaoDetectada?.let {
            viewModel.aplicarDetectada(it)
            PreenchimentoPendente.consumir()
        }
    }

    val categorias by viewModel.categorias.collectAsState()
    val listaCartoes by viewModel.listaCartoes.collectAsState()

    var categoriaParaEditarId by rememberSaveable { mutableStateOf<Long?>(null) }
    val categoriasViewModel: CategoriasViewModel = viewModel()
    val itensCategoria by categoriasViewModel.itens.collectAsState()
    var cartaoMenuExpandido by rememberSaveable { mutableStateOf(false) }
    var mostrarDatePicker by rememberSaveable { mutableStateOf(false) }
    var textoCategoria by rememberSaveable { mutableStateOf("") }
    var iconeNovaCategoria by rememberSaveable { mutableStateOf<String?>(null) }
    var criandoCategoria by rememberSaveable { mutableStateOf(false) }

    // Seleciona o primeiro cartão por defeito se nenhum estiver selecionado
    LaunchedEffect(listaCartoes) {
        if (viewModel.cartaoIdSelecionado == null && listaCartoes.isNotEmpty()) {
            viewModel.onCartaoSelecionado(listaCartoes.first().id)
        }
    }

    val categoriasFiltradas = categorias.filter { it.tipo == viewModel.tipo }
    val cartaoAtualNome = listaCartoes.find { it.id == viewModel.cartaoIdSelecionado }?.nome ?: "Selecionar cartão"

    val mensagem = LocalMensagem.current
    val hapticFeedback = LocalHapticFeedback.current
    val corTema by animateColorAsState(
        targetValue = if (viewModel.tipo == TipoTransacao.DESPESA) Coral else Verde,
        animationSpec = tween(300),
        label = "corTemaTransacao"
    )
    // Verde/Coral como texto vêm do tema (contraste); corTema fica para preenchimentos.
    val corTextoTema by animateColorAsState(
        targetValue = if (viewModel.tipo == TipoTransacao.DESPESA) MaterialTheme.cores.textoGasto else MaterialTheme.cores.textoGanho,
        animationSpec = tween(300),
        label = "corTextoTemaTransacao"
    )

    val coresCampo = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = corTema,
        focusedLabelColor = corTextoTema,
        cursorColor = corTema,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    )
    val formaCampo = RoundedCornerShape(14.dp)

    val hoje = remember { LocalDate.now() }
    val ontem = remember { hoje.minusDays(1) }
    val scrollState = rememberScrollState()

    val focoValor = remember { FocusRequester() }
    val focoNovaCategoria = remember { FocusRequester() }
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(corTema.copy(alpha = 0.18f), Color.Transparent)
                        )
                    )
            )

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
                        .padding(top = 8.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = aoFechar,
                        modifier = Modifier
                            .size(Tamanho.toque)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Fechar", modifier = Modifier.size(Tamanho.icone))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Nova Transação",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                SeletorSegmentado(
                    opcoes = listOf("Gasto", "Ganho"),
                    indiceSelecionado = if (viewModel.tipo == TipoTransacao.RECEITA) 1 else 0,
                    aoSelecionar = { indice ->
                        val novoTipo = if (indice == 1) TipoTransacao.RECEITA else TipoTransacao.DESPESA
                        if (viewModel.tipo != novoTipo) {
                            viewModel.definirTipoInicial(novoTipo)
                            textoCategoria = ""
                            iconeNovaCategoria = null
                            criandoCategoria = false
                            viewModel.onCategoriaChange(null)
                        }
                    },
                    cor = corTema,
                    modifier = Modifier.padding(horizontal = MaterialTheme.dimens.paddingScreen)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = MaterialTheme.dimens.paddingScreen)
                        .padding(top = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(corTema.copy(alpha = 0.20f), corTema.copy(alpha = 0.06f))
                                )
                            )
                            .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 2.dp)
                    ) {
                        Column {
                            Text(
                                text = if (viewModel.tipo == TipoTransacao.DESPESA) "QUANTO VOCÊ GASTOU?" else "QUANTO VOCÊ RECEBEU?",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = corTextoTema
                            )
                            Spacer(modifier = Modifier.height(2.dp))

                            val valorExibido = if (viewModel.valor.isEmpty()) "0,00" else viewModel.valor
                            val corTexto = if (viewModel.valor.isEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            } else {
                                corTextoTema
                            }

                            var textFieldValue by remember(valorExibido) {
                                mutableStateOf(
                                    TextFieldValue(
                                        text = valorExibido,
                                        selection = TextRange(valorExibido.length)
                                    )
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "R$",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = corTextoTema
                                )
                                OutlinedTextField(
                                    value = textFieldValue,
                                    onValueChange = { novoTfv ->
                                        val valorFormatado = formatarValorMoeda(novoTfv.text)
                                        viewModel.onValorChange(valorFormatado)
                                        val textoFinal = if (valorFormatado.isEmpty()) "0,00" else valorFormatado
                                        textFieldValue = TextFieldValue(
                                            text = textoFinal,
                                            selection = TextRange(textoFinal.length)
                                        )
                                    },
                                    textStyle = MaterialTheme.typography.displayMedium.copy(
                                        textAlign = TextAlign.Start,
                                        fontWeight = FontWeight.Bold,
                                        color = corTexto
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Done
                                    ),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        cursorColor = corTema
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(focoValor)
                                )
                            }
                        }
                    }

                    SecaoCard {
                        Text("Categoria", style = MaterialTheme.typography.titleMedium)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categoriasFiltradas.forEach { categoria ->
                                ChipCategoria(
                                    texto = categoria.nome,
                                    icone = iconeParaCategoria(categoria.nome, categoria.icone),
                                    selecionado = viewModel.categoriaSelecionada?.id == categoria.id,
                                    cor = corTextoTema,
                                    aoClicar = {
                                        viewModel.onCategoriaChange(categoria)
                                        textoCategoria = ""
                                        iconeNovaCategoria = null
                                        criandoCategoria = false
                                    },
                                    aoTocarLongo = { categoriaParaEditarId = categoria.id }
                                )
                            }
                            ChipCategoria(
                                texto = "Nova",
                                icone = Icons.Outlined.Add,
                                selecionado = criandoCategoria,
                                cor = corTextoTema,
                                role = Role.Button,
                                aoClicar = {
                                    criandoCategoria = true
                                    viewModel.onCategoriaChange(null)
                                }
                            )
                        }
                        AnimatedVisibility(visible = criandoCategoria) {
                            LaunchedEffect(Unit) { focoNovaCategoria.requestFocus() }
                            val existeExata = categoriasFiltradas.any { it.nome.equals(textoCategoria.trim(), ignoreCase = true) }
                            Column {
                                OutlinedTextField(
                                    value = textoCategoria,
                                    onValueChange = { texto ->
                                        textoCategoria = texto
                                        // Nome igual ao de uma categoria existente usa ela em vez de criar outra
                                        viewModel.onCategoriaChange(
                                            categoriasFiltradas.find { it.nome.equals(texto.trim(), ignoreCase = true) }
                                        )
                                    },
                                    label = { Text("Nome da nova categoria") },
                                    supportingText = {
                                        if (textoCategoria.isNotBlank() && !existeExata) {
                                            Text("Será salva como nova: \"${textoCategoria.trim()}\"")
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Category, contentDescription = null, tint = corTextoTema)
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    shape = formaCampo,
                                    colors = coresCampo,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focoNovaCategoria)
                                )
                                if (textoCategoria.isNotBlank() && !existeExata) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    SeletorDeIconeCategoria(
                                        selecionada = iconeNovaCategoria,
                                        aoSelecionar = { iconeNovaCategoria = it },
                                        cor = corTextoTema
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(Tamanho.toque)
                                    .clip(formaCampo)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), formaCampo)
                                    .clickable(onClickLabel = "Selecionar data", role = Role.Button) { mostrarDatePicker = true }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.CalendarMonth,
                                    contentDescription = "Selecionar data",
                                    tint = corTextoTema,
                                    modifier = Modifier.size(Tamanho.icone)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    viewModel.data.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                            PilulaData("Hoje", viewModel.data == hoje, corTextoTema) { viewModel.onDataChange(hoje) }
                            PilulaData("Ontem", viewModel.data == ontem, corTextoTema) { viewModel.onDataChange(ontem) }
                        }

                        OutlinedTextField(
                            value = viewModel.descricao,
                            onValueChange = viewModel::onDescricaoChange,
                            label = { Text(if (viewModel.ehRecorrente || viewModel.ehParcelado) "Nome (Ex: Salário, Netflix) *" else "Descrição (opcional)") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Edit, contentDescription = null, tint = corTextoTema)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            shape = formaCampo,
                            colors = coresCampo,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    SecaoCard {
                        if (viewModel.tipo == TipoTransacao.DESPESA) {
                            Text("Forma de pagamento", style = MaterialTheme.typography.titleMedium)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OpcaoPagamento(
                                    modifier = Modifier.weight(1f),
                                    icone = Icons.Outlined.AccountBalance,
                                    titulo = "Débito",
                                    selecionado = viewModel.formaPagamento == FormaPagamento.DEBITO,
                                    cor = corTextoTema,
                                    aoClicar = { viewModel.onFormaPagamentoChange(FormaPagamento.DEBITO) }
                                )
                                OpcaoPagamento(
                                    modifier = Modifier.weight(1f),
                                    icone = Icons.Outlined.CreditCard,
                                    titulo = "Crédito",
                                    selecionado = viewModel.formaPagamento == FormaPagamento.CARTAO_CREDITO,
                                    cor = corTextoTema,
                                    aoClicar = { viewModel.onFormaPagamentoChange(FormaPagamento.CARTAO_CREDITO) }
                                )
                            }

                            // SELETOR DE CARTÃO QUANDO FOR CRÉDITO
                            AnimatedVisibility(visible = viewModel.formaPagamento == FormaPagamento.CARTAO_CREDITO) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    ExposedDropdownMenuBox(
                                        expanded = cartaoMenuExpandido,
                                        onExpandedChange = { cartaoMenuExpandido = it }
                                    ) {
                                        OutlinedTextField(
                                            value = cartaoAtualNome,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Cartão de Crédito") },
                                            leadingIcon = {
                                                Icon(Icons.Outlined.CreditCard, contentDescription = null, tint = corTextoTema)
                                            },
                                            trailingIcon = {
                                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = cartaoMenuExpandido)
                                            },
                                            shape = formaCampo,
                                            colors = coresCampo,
                                            modifier = Modifier
                                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                                .fillMaxWidth()
                                        )
                                        ExposedDropdownMenu(
                                            expanded = cartaoMenuExpandido,
                                            onDismissRequest = { cartaoMenuExpandido = false }
                                        ) {
                                            if (listaCartoes.isEmpty()) {
                                                DropdownMenuItem(
                                                    text = { Text("Nenhum cartão cadastrado") },
                                                    onClick = { cartaoMenuExpandido = false }
                                                )
                                            } else {
                                                listaCartoes.forEach { cartao ->
                                                    DropdownMenuItem(
                                                        text = { Text(cartao.nome) },
                                                        onClick = {
                                                            viewModel.onCartaoSelecionado(cartao.id)
                                                            cartaoMenuExpandido = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    LinhaSwitch(
                                        icone = Icons.Outlined.CreditCard,
                                        cor = corTema,
                                        titulo = "Compra Parcelada?",
                                        subtitulo = "Dividir em várias vezes",
                                        marcado = viewModel.ehParcelado,
                                        aoMudar = viewModel::onParceladoChange
                                    )
                                    AnimatedVisibility(visible = viewModel.ehParcelado) {
                                        OutlinedTextField(
                                            value = viewModel.numeroParcelas,
                                            onValueChange = viewModel::onNumeroParcelasChange,
                                            label = { Text("Número de Parcelas (ex: 10)") },
                                            isError = viewModel.parcelasInvalidas,
                                            supportingText = {
                                                if (viewModel.parcelasInvalidas) Text("Use entre 2 e 60 parcelas")
                                            },
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Number,
                                                imeAction = ImeAction.Done
                                            ),
                                            shape = formaCampo,
                                            colors = coresCampo,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    LinhaSwitch(
                                        icone = Icons.Outlined.Repeat,
                                        cor = corTema,
                                        titulo = "Repetir todo mês (Assinatura)",
                                        subtitulo = "Cobrança recorrente no cartão",
                                        marcado = viewModel.ehRecorrente,
                                        aoMudar = viewModel::onRecorrenteChange
                                    )
                                }
                            }
                        } else {
                            LinhaSwitch(
                                icone = Icons.Outlined.Repeat,
                                cor = corTema,
                                titulo = "Ganho recorrente?",
                                subtitulo = "Repetir todo mês (ex: Salário)",
                                marcado = viewModel.ehRecorrente,
                                aoMudar = viewModel::onRecorrenteChange
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
                    BotaoPrincipal(
                        texto = "Salvar Transação",
                        onClick = {
                            viewModel.salvar(
                                nomeCategoriaDigitada = textoCategoria,
                                iconeNovaCategoria = iconeNovaCategoria,
                                aoErro = { erro -> mensagem(erro) }
                            ) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                mensagem("Transação salva com sucesso!")
                                aoSalvar()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        cor = corTema,
                        corConteudo = Color.White,
                    )
                }
            }
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
                    mensagem("Categoria salva.")
                },
                aoExcluir = {
                    if (viewModel.categoriaSelecionada?.id == item.categoria.id) viewModel.onCategoriaChange(null)
                    categoriasViewModel.excluir(item.categoria)
                    categoriaParaEditarId = null
                    mensagem("Categoria excluída.")
                },
                aoFechar = { categoriaParaEditarId = null },
                cor = corTema
            )
        }

        if (mostrarDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = viewModel.data
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { mostrarDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val novaData = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            viewModel.onDataChange(novaData)
                        }
                        mostrarDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { mostrarDatePicker = false }) { Text("Cancelar") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}
