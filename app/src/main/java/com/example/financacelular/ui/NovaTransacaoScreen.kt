package com.example.financacelular.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
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
import com.example.financacelular.ui.theme.Verde
import com.example.financacelular.ui.theme.dimens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import androidx.compose.animation.core.animateFloatAsState

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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
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

@Composable
private fun ItemTipo(
    modifier: Modifier,
    texto: String,
    icone: ImageVector,
    selecionado: Boolean,
    aoClicar: () -> Unit
) {
    val cor by animateColorAsState(
        targetValue = if (selecionado) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "corItemTipo"
    )
    Row(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(50))
            .clickable(onClick = aoClicar),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icone, contentDescription = null, tint = cor, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(texto, fontWeight = FontWeight.Bold, color = cor)
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
    val corBorda by animateColorAsState(
        targetValue = if (selecionado) cor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        animationSpec = tween(200),
        label = "bordaPagamento"
    )
    val corFundo by animateColorAsState(
        targetValue = if (selecionado) cor.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(200),
        label = "fundoPagamento"
    )
    val corConteudo = if (selecionado) cor else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(corFundo)
            .border(1.5.dp, corBorda, RoundedCornerShape(14.dp))
            .clickable(onClick = aoClicar),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icone, contentDescription = null, tint = corConteudo, modifier = Modifier.size(18.dp))
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
            .clickable { aoMudar(!marcado) }
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
            onCheckedChange = aoMudar,
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
            .clip(RoundedCornerShape(50))
            .background(fundo)
            .clickable(onClick = aoClicar)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selecionada) FontWeight.Bold else FontWeight.Medium,
            color = if (selecionada) cor else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovaTransacaoScreen(
    viewModel: NovaTransacaoViewModel = viewModel(),
    tipoInicial: TipoTransacao = TipoTransacao.DESPESA,
    isParceladoInicial: Boolean = false,
    isRecorrenteInicial: Boolean = false,
    aoSalvar: () -> Unit = {},
    aoFechar: () -> Unit = {}
) {
    remember(tipoInicial) {
        viewModel.definirTipoInicial(tipoInicial)
        true
    }

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

    val categorias by viewModel.categorias.collectAsState()
    val listaCartoes by viewModel.listaCartoes.collectAsState()

    var categoriaMenuExpandido by remember { mutableStateOf(false) }
    var cartaoMenuExpandido by remember { mutableStateOf(false) }
    var mostrarDatePicker by remember { mutableStateOf(false) }
    var textoCategoria by remember { mutableStateOf("") }

    // Seleciona o primeiro cartão por defeito se nenhum estiver selecionado
    LaunchedEffect(listaCartoes) {
        if (viewModel.cartaoIdSelecionado == null && listaCartoes.isNotEmpty()) {
            viewModel.onCartaoSelecionado(listaCartoes.first().id)
        }
    }

    LaunchedEffect(viewModel.tipo) {
        textoCategoria = ""
        viewModel.onCategoriaChange(null)
    }

    val categoriasFiltradas = categorias.filter { it.tipo == viewModel.tipo }
    val cartaoAtualNome = listaCartoes.find { it.id == viewModel.cartaoIdSelecionado }?.nome ?: "Selecionar cartão"

    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val corTema by animateColorAsState(
        targetValue = if (viewModel.tipo == TipoTransacao.DESPESA) Coral else Verde,
        animationSpec = tween(300),
        label = "corTemaTransacao"
    )
    val posicaoSeletor by animateFloatAsState(
        targetValue = if (viewModel.tipo == TipoTransacao.RECEITA) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f),
        label = "posicaoSeletorTipo"
    )

    val coresCampo = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = corTema,
        focusedLabelColor = corTema,
        cursorColor = corTema,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    )
    val formaCampo = RoundedCornerShape(16.dp)

    val hoje = remember { LocalDate.now() }
    val ontem = remember { hoje.minusDays(1) }
    val scrollState = rememberScrollState()

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
                            .size(38.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Fechar", modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Nova Transação",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = MaterialTheme.dimens.paddingScreen)
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .fillMaxHeight()
                            .graphicsLayer { translationX = posicaoSeletor * size.width }
                            .shadow(elevation = 4.dp, shape = RoundedCornerShape(50))
                            .background(corTema, RoundedCornerShape(50))
                    )
                    Row(modifier = Modifier.fillMaxSize()) {
                        ItemTipo(
                            modifier = Modifier.weight(1f),
                            texto = "Gasto",
                            icone = Icons.AutoMirrored.Filled.TrendingDown,
                            selecionado = viewModel.tipo == TipoTransacao.DESPESA,
                            aoClicar = {
                                if (viewModel.tipo != TipoTransacao.DESPESA) {
                                    viewModel.definirTipoInicial(TipoTransacao.DESPESA)
                                }
                            }
                        )
                        ItemTipo(
                            modifier = Modifier.weight(1f),
                            texto = "Ganho",
                            icone = Icons.AutoMirrored.Filled.TrendingUp,
                            selecionado = viewModel.tipo == TipoTransacao.RECEITA,
                            aoClicar = {
                                if (viewModel.tipo != TipoTransacao.RECEITA) {
                                    viewModel.definirTipoInicial(TipoTransacao.RECEITA)
                                }
                            }
                        )
                    }
                }

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
                            .border(1.dp, corTema.copy(alpha = 0.28f), RoundedCornerShape(28.dp))
                            .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 2.dp)
                    ) {
                        Column {
                            Text(
                                text = if (viewModel.tipo == TipoTransacao.DESPESA) "QUANTO VOCÊ GASTOU?" else "QUANTO VOCÊ RECEBEU?",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = corTema
                            )
                            Spacer(modifier = Modifier.height(2.dp))

                            val valorExibido = if (viewModel.valor.isEmpty()) "0,00" else viewModel.valor
                            val corTexto = if (viewModel.valor.isEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            } else {
                                corTema
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
                                    color = corTema
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
                                    textStyle = MaterialTheme.typography.headlineLarge.copy(
                                        textAlign = TextAlign.Start,
                                        fontWeight = FontWeight.Bold,
                                        color = corTexto
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        cursorColor = corTema
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    SecaoCard {
                        ExposedDropdownMenuBox(
                            expanded = categoriaMenuExpandido,
                            onExpandedChange = { categoriaMenuExpandido = it }
                        ) {
                            OutlinedTextField(
                                value = textoCategoria,
                                onValueChange = {
                                    textoCategoria = it
                                    categoriaMenuExpandido = true
                                    viewModel.onCategoriaChange(null)
                                },
                                label = { Text("Categoria") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Category, contentDescription = null, tint = corTema)
                                },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoriaMenuExpandido)
                                },
                                shape = formaCampo,
                                colors = coresCampo,
                                modifier = Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                                    .fillMaxWidth()
                            )
                            val categoriasParaMostrar = categoriasFiltradas.filter {
                                it.nome.contains(textoCategoria, ignoreCase = true)
                            }
                            ExposedDropdownMenu(
                                expanded = categoriaMenuExpandido,
                                onDismissRequest = { categoriaMenuExpandido = false }
                            ) {
                                categoriasParaMostrar.forEach { categoria ->
                                    DropdownMenuItem(
                                        text = { Text(categoria.nome) },
                                        onClick = {
                                            viewModel.onCategoriaChange(categoria)
                                            textoCategoria = categoria.nome
                                            categoriaMenuExpandido = false
                                        },
                                        trailingIcon = {
                                            IconButton(onClick = {
                                                viewModel.excluirCategoria(categoria) {
                                                    Toast.makeText(context, "Categoria em uso não pode ser excluída.", Toast.LENGTH_LONG).show()
                                                }
                                            }) {
                                                Icon(Icons.Filled.Close, contentDescription = "Excluir")
                                            }
                                        }
                                    )
                                }
                                val existeExata = categoriasFiltradas.any { it.nome.equals(textoCategoria, ignoreCase = true) }
                                if (textoCategoria.isNotBlank() && !existeExata) {
                                    DropdownMenuItem(
                                        text = { Text("Salvar como nova: \"$textoCategoria\"") },
                                        onClick = { categoriaMenuExpandido = false },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Add, contentDescription = "Adicionar", tint = MaterialTheme.colorScheme.primary)
                                        }
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
                                    .height(48.dp)
                                    .clip(formaCampo)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), formaCampo)
                                    .clickable { mostrarDatePicker = true }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.CalendarMonth,
                                    contentDescription = "Selecionar data",
                                    tint = corTema,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    viewModel.data.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                            PilulaData("Hoje", viewModel.data == hoje, corTema) { viewModel.onDataChange(hoje) }
                            PilulaData("Ontem", viewModel.data == ontem, corTema) { viewModel.onDataChange(ontem) }
                        }

                        OutlinedTextField(
                            value = viewModel.descricao,
                            onValueChange = viewModel::onDescricaoChange,
                            label = { Text(if (viewModel.ehRecorrente || viewModel.ehParcelado) "Nome (Ex: Salário, Netflix) *" else "Descrição (opcional)") },
                            leadingIcon = {
                                Icon(Icons.Filled.Edit, contentDescription = null, tint = corTema)
                            },
                            shape = formaCampo,
                            colors = coresCampo,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    SecaoCard {
                        if (viewModel.tipo == TipoTransacao.DESPESA) {
                            Text(
                                "FORMA DE PAGAMENTO",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OpcaoPagamento(
                                    modifier = Modifier.weight(1f),
                                    icone = Icons.Filled.AccountBalance,
                                    titulo = "Débito",
                                    selecionado = viewModel.formaPagamento == FormaPagamento.DEBITO,
                                    cor = corTema,
                                    aoClicar = { viewModel.onFormaPagamentoChange(FormaPagamento.DEBITO) }
                                )
                                OpcaoPagamento(
                                    modifier = Modifier.weight(1f),
                                    icone = Icons.Filled.CreditCard,
                                    titulo = "Crédito",
                                    selecionado = viewModel.formaPagamento == FormaPagamento.CARTAO_CREDITO,
                                    cor = corTema,
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
                                                Icon(Icons.Filled.CreditCard, contentDescription = null, tint = corTema)
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
                                        icone = Icons.Filled.CreditCard,
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
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            shape = formaCampo,
                                            colors = coresCampo,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    LinhaSwitch(
                                        icone = Icons.Filled.Repeat,
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
                                icone = Icons.Filled.Repeat,
                                cor = corTema,
                                titulo = "Receita Recorrente?",
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
                    Button(
                        onClick = {
                            viewModel.salvar(nomeCategoriaDigitada = textoCategoria) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                Toast.makeText(context, "Transação salva com sucesso!", Toast.LENGTH_SHORT).show()
                                aoSalvar()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation = 16.dp, shape = RoundedCornerShape(20.dp))
                            .height(52.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = corTema,
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Salvar Transação",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
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