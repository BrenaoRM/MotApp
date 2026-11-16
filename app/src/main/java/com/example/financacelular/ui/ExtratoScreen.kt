package com.example.financacelular.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.CartaoEntity
import com.example.financacelular.data.Categoria
import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Espaco
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.Verde
import com.example.financacelular.ui.theme.cores
import com.example.financacelular.ui.theme.dimens
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val localePtBr: Locale = Locale.Builder().setLanguage("pt").setRegion("BR").build()
private val formatoRotuloDia: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd 'de' MMM", localePtBr)
private val formatoRotuloDiaAno: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd 'de' MMM 'de' yyyy", localePtBr)

/** "Hoje", "Ontem", "Amanhã" ou "Seg, 29 de set". */
private fun rotuloDia(data: LocalDate): String {
    val hoje = LocalDate.now()
    return when (data) {
        hoje -> "Hoje"
        hoje.minusDays(1) -> "Ontem"
        hoje.plusDays(1) -> "Amanhã"
        else -> data
            .format(if (data.year == hoje.year) formatoRotuloDia else formatoRotuloDiaAno)
            .replace(".", "")
            .replaceFirstChar { it.uppercase() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtratoScreen(
    viewModel: ExtratoViewModel = viewModel(),
    textoPesquisa: String = "",
    aoVoltar: (() -> Unit)? = null
) {
    val transacoesOuNull by viewModel.todasTransacoes.collectAsState()
    val transacoes = transacoesOuNull ?: emptyList()
    val futuros by viewModel.futurosLancamentos.collectAsState()
    val categorias by viewModel.categorias.collectAsState()
    val cartoes by viewModel.cartoes.collectAsState()

    var filtroTipo by rememberSaveable { mutableStateOf<TipoTransacao?>(null) }
    var filtroCategoriaId by rememberSaveable { mutableStateOf<Long?>(null) }
    val filtroCategoria = categorias.find { it.id == filtroCategoriaId }
    var menuCategoriaExpandido by remember { mutableStateOf(false) }
    var transacaoEmEdicaoId by rememberSaveable { mutableStateOf<Long?>(null) }
    val transacaoEmEdicao = transacaoEmEdicaoId?.let { id ->
        transacoes.find { it.id == id } ?: futuros.find { it.id == id }
    }

    val formato = remember { NumberFormat.getCurrencyInstance(localePtBr) }
    var mostrarFiltros by rememberSaveable { mutableStateOf(false) }
    // Período do extrato: abre no mês atual
    var periodoInicio by rememberSaveable { mutableStateOf(YearMonth.now().atDay(1)) }
    var periodoFim by rememberSaveable { mutableStateOf(YearMonth.now().atEndOfMonth()) }
    val periodo = Periodo(periodoInicio, periodoFim)
    var mostrarFolhaPeriodo by rememberSaveable { mutableStateOf(false) }

    val algumFiltroAtivo = filtroTipo != null || filtroCategoriaId != null

    val quantidadeFiltros = listOf(
        filtroTipo != null,
        filtroCategoriaId != null
    ).count { it }

    fun passaPeriodo(t: Transacao): Boolean {
        val inicio = periodo.inicio
        val fim = periodo.fim

        val dataReal = t.data
        val dataFatura = if (t.formaPagamento == FormaPagamento.CARTAO_CREDITO && t.anoMes != null) {
            try {
                val partes = t.anoMes.split("-")
                LocalDate.of(partes[0].toInt(), partes[1].toInt(), 1)
            } catch (_: Exception) {
                dataReal
            }
        } else {
            dataReal
        }

        val realNoPeriodo = !dataReal.isBefore(inicio) && !dataReal.isAfter(fim)
        val faturaNoPeriodo = !dataFatura.isBefore(inicio) && !dataFatura.isAfter(fim)

        return realNoPeriodo || faturaNoPeriodo
    }

    val transacoesFiltradas = transacoes.filter { t ->
        val categoria = categorias.find { it.id == t.categoriaId }
        val passaTipo = filtroTipo == null || t.tipo == filtroTipo
        val passaCategoria = filtroCategoriaId == null || t.categoriaId == filtroCategoriaId
        val passaBusca = textoPesquisa.isBlank() ||
                (t.descricao?.contains(textoPesquisa, ignoreCase = true) == true) ||
                (categoria?.nome?.contains(textoPesquisa, ignoreCase = true) == true)

        passaTipo && passaCategoria && passaBusca && passaPeriodo(t)
    }.sortedByDescending { it.data }

    val futurosFiltrados = futuros.filter { t ->
        val categoria = categorias.find { it.id == t.categoriaId }
        val passaTipo = filtroTipo == null || t.tipo == filtroTipo
        val passaCategoria = filtroCategoriaId == null || t.categoriaId == filtroCategoriaId
        val passaBusca = textoPesquisa.isBlank() ||
                (t.descricao?.contains(textoPesquisa, ignoreCase = true) == true) ||
                (categoria?.nome?.contains(textoPesquisa, ignoreCase = true) == true)

        passaTipo && passaCategoria && passaBusca && passaPeriodo(t)
    }.sortedBy { it.data }

    val totalReceitas = transacoesFiltradas
        .filter { it.tipo == TipoTransacao.RECEITA }
        .sumOf { it.valor }
    val totalDespesas = transacoesFiltradas
        .filter { it.tipo == TipoTransacao.DESPESA }
        .sumOf { it.valor }

    val gruposRealizados = transacoesFiltradas.groupBy { it.data }.entries.toList()
    val gruposFuturos = futurosFiltrados.groupBy { it.data }.entries.toList()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = MaterialTheme.dimens.paddingScreen),
        contentPadding = PaddingValues(
            top = 16.dp,
            bottom = espacoParaBarraFlutuante()
        )
    ) {
        // ---------- Cabeçalho ----------
        item(key = "cabecalho") {
            CabecalhoDePagina(
                titulo = "Extrato",
                corIcone = MaterialTheme.colorScheme.primary,
                aoVoltar = aoVoltar,
                acao = {
                    BotaoFiltros(
                        quantidade = quantidadeFiltros,
                        onClick = { mostrarFiltros = true }
                    )
                }
            )
        }

        // ---------- Período ----------
        item(key = "periodo") {
            Spacer(modifier = Modifier.height(16.dp))
            SeletorPeriodo(
                periodo = periodo,
                aoAnterior = { periodo.deslocar(-1).let { periodoInicio = it.inicio; periodoFim = it.fim } },
                aoSeguinte = { periodo.deslocar(1).let { periodoInicio = it.inicio; periodoFim = it.fim } },
                aoAbrir = { mostrarFolhaPeriodo = true },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ---------- Filtros ativos (chips removíveis) ----------
        if (algumFiltroAtivo) {
            item(key = "filtros-ativos") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filtroTipo?.let { tipo ->
                        ChipFiltroAtivo(
                            texto = if (tipo == TipoTransacao.DESPESA) "Gastos" else "Ganhos",
                            aoRemover = { filtroTipo = null }
                        )
                    }
                    filtroCategoria?.let { categoria ->
                        ChipFiltroAtivo(
                            texto = categoria.nome,
                            aoRemover = { filtroCategoriaId = null }
                        )
                    }
                }
            }
        }

        // ---------- Resumo ----------
        if (transacoesFiltradas.isNotEmpty()) {
            item(key = "resumo") {
                Spacer(modifier = Modifier.height(20.dp))
                ResumoExtratoCard(
                    saldo = totalReceitas - totalDespesas,
                    receitas = totalReceitas,
                    despesas = totalDespesas,
                    quantidade = transacoesFiltradas.size,
                    formato = formato
                )
            }
        }

        // ---------- Lançamentos realizados ----------
        item(key = "titulo-realizados") {
            Spacer(modifier = Modifier.height(28.dp))
            TituloSecao(titulo = "Lançamentos realizados", quantidade = transacoesFiltradas.size)
        }

        if (transacoesOuNull != null && transacoesFiltradas.isEmpty()) {
            item(key = "vazio") {
                EstadoVazio(
                    icone = Icons.Filled.SearchOff,
                    mensagem = if (textoPesquisa.isBlank()) {
                        "Nenhuma transação realizada encontrada."
                    } else {
                        "Nenhuma transação encontrada para \"$textoPesquisa\"."
                    }
                )
            }
        }

        items(gruposRealizados, key = { "r-${it.key}" }) { grupo ->
            GrupoDia(
                data = grupo.key,
                transacoes = grupo.value,
                categorias = categorias,
                cartoes = cartoes,
                formato = formato,
                pendente = false,
                aoClicar = { transacaoEmEdicaoId = it.id }
            )
        }

        // ---------- Futuros / pendentes ----------
        if (futurosFiltrados.isNotEmpty()) {
            item(key = "titulo-futuros") {
                Spacer(modifier = Modifier.height(36.dp))
                TituloSecao(
                    titulo = "Próximos lançamentos",
                    quantidade = futurosFiltrados.size,
                    icone = Icons.Filled.Schedule
                )
            }

            items(gruposFuturos, key = { "f-${it.key}" }) { grupo ->
                GrupoDia(
                    data = grupo.key,
                    transacoes = grupo.value,
                    categorias = categorias,
                    cartoes = cartoes,
                    formato = formato,
                    pendente = true,
                    aoClicar = { transacaoEmEdicaoId = it.id }
                )
            }
        }
    }

    transacaoEmEdicao?.let { transacao ->
        BottomSheetEditarTransacao(
            transacao = transacao,
            categorias = categorias,
            onDismiss = { transacaoEmEdicaoId = null },
            onSalvar = {
                viewModel.atualizar(it)
                transacaoEmEdicaoId = null
            },
            onExcluir = {
                viewModel.excluir(transacao)
                transacaoEmEdicaoId = null
            }
        )
    }

    if (mostrarFiltros) {
        FolhaApp(onDismiss = { mostrarFiltros = false }) {
                CabecalhoDeFolha(titulo = "Filtros", aoFechar = { mostrarFiltros = false })
                Spacer(modifier = Modifier.height(24.dp))

                RotuloFiltro("Tipo de movimentação")
                SeletorSegmentado(
                    opcoes = listOf("Todas", "Gastos", "Ganhos"),
                    indiceSelecionado = when (filtroTipo) {
                        TipoTransacao.DESPESA -> 1
                        TipoTransacao.RECEITA -> 2
                        else -> 0
                    },
                    aoSelecionar = { indice ->
                        filtroTipo = when (indice) {
                            1 -> TipoTransacao.DESPESA
                            2 -> TipoTransacao.RECEITA
                            else -> null
                        }
                    },
                    cor = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(24.dp))
                RotuloFiltro("Categoria")
                ExposedDropdownMenuBox(
                    expanded = menuCategoriaExpandido,
                    onExpandedChange = { menuCategoriaExpandido = it }
                ) {
                    OutlinedTextField(
                        value = filtroCategoria?.nome ?: "Todas as categorias",
                        onValueChange = {},
                        readOnly = true,
                        shape = RoundedCornerShape(14.dp),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuCategoriaExpandido) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = menuCategoriaExpandido,
                        onDismissRequest = { menuCategoriaExpandido = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Todas as categorias") },
                            onClick = { filtroCategoriaId = null; menuCategoriaExpandido = false }
                        )
                        categorias.forEach { categoria ->
                            DropdownMenuItem(
                                text = { Text(categoria.nome) },
                                onClick = { filtroCategoriaId = categoria.id; menuCategoriaExpandido = false }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BotaoSecundario(
                        texto = "Limpar filtros",
                        onClick = {
                            filtroTipo = null
                            filtroCategoriaId = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                    BotaoPrincipal(
                        texto = "Aplicar",
                        onClick = { mostrarFiltros = false },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(Espaco.lg))
        }
    }

    if (mostrarFolhaPeriodo) {
        FolhaPeriodo(
            periodo = periodo,
            aoEscolher = {
                periodoInicio = it.inicio
                periodoFim = it.fim
                mostrarFolhaPeriodo = false
            },
            aoFechar = { mostrarFolhaPeriodo = false }
        )
    }
}

// =====================================================================
// Componentes da lista
// =====================================================================

@Composable
private fun BotaoFiltros(quantidade: Int, onClick: () -> Unit) {
    val ativo = quantidade > 0
    val cores = MaterialTheme.colorScheme
    val corFundo = if (ativo) cores.primary else cores.primary.copy(alpha = 0.10f)
    val corConteudo = if (ativo) cores.onPrimary else cores.primary

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = corFundo
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.FilterList,
                contentDescription = null,
                tint = corConteudo,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "Filtros",
                color = corConteudo,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            if (ativo) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(cores.onPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = quantidade.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = cores.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ChipFiltroAtivo(texto: String, aoRemover: () -> Unit) {
    FilterChip(
        selected = true,
        onClick = aoRemover,
        label = { Text(texto, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingIcon = {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remover filtro $texto",
                modifier = Modifier.size(16.dp)
            )
        },
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun ResumoExtratoCard(
    saldo: Double,
    receitas: Double,
    despesas: Double,
    quantidade: Int,
    formato: NumberFormat
) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val gradiente = Brush.linearGradient(
        colors = listOf(primary, lerp(primary, Color.Black, 0.35f))
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(gradiente)
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Saldo do extrato",
                    style = MaterialTheme.typography.labelLarge,
                    color = onPrimary.copy(alpha = 0.80f)
                )
                Text(
                    if (quantidade == 1) "1 lançamento" else "$quantidade lançamentos",
                    style = MaterialTheme.typography.labelMedium,
                    color = onPrimary.copy(alpha = 0.80f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                formato.format(saldo),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ResumoMini(
                    modifier = Modifier.weight(1f),
                    rotulo = "Ganhos",
                    valor = formato.format(receitas),
                    icone = Icons.Filled.ArrowDownward
                )
                ResumoMini(
                    modifier = Modifier.weight(1f),
                    rotulo = "Gastos",
                    valor = formato.format(despesas),
                    icone = Icons.Filled.ArrowUpward
                )
            }
        }
    }
}

@Composable
private fun ResumoMini(
    modifier: Modifier,
    rotulo: String,
    valor: String,
    icone: ImageVector
) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(onPrimary.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(onPrimary.copy(alpha = 0.20f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icone, contentDescription = null, tint = onPrimary, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                rotulo,
                style = MaterialTheme.typography.labelSmall,
                color = onPrimary.copy(alpha = 0.80f)
            )
            Text(
                valor,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TituloSecao(
    titulo: String,
    quantidade: Int,
    icone: ImageVector? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icone != null) {
            Icon(
                icone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            titulo,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                .padding(horizontal = 9.dp, vertical = 2.dp)
        ) {
            Text(
                quantidade.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GrupoDia(
    data: LocalDate,
    transacoes: List<Transacao>,
    categorias: List<Categoria>,
    cartoes: List<CartaoEntity>,
    formato: NumberFormat,
    pendente: Boolean,
    aoClicar: (Transacao) -> Unit
) {
    val saldoDia = transacoes.sumOf { if (it.tipo == TipoTransacao.DESPESA) -it.valor else it.valor }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                rotuloDia(data),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                (if (saldoDia < 0) "- " else "+ ") + formato.format(abs(saldoDia)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        GrupoDeLista(itens = transacoes) { transacao ->
            LinhaTransacao(
                transacao = transacao,
                categoria = categorias.find { it.id == transacao.categoriaId },
                nomeCartao = transacao.cartaoId?.let { id -> cartoes.find { it.id == id }?.nome },
                formato = formato,
                pendente = pendente,
                aoClicar = { aoClicar(transacao) }
            )
        }
    }
}

@Composable
private fun LinhaTransacao(
    transacao: Transacao,
    categoria: Categoria?,
    nomeCartao: String?,
    formato: NumberFormat,
    pendente: Boolean,
    aoClicar: () -> Unit
) {
    val cor = if (transacao.tipo == TipoTransacao.DESPESA) Coral else Verde
    val corTexto = if (transacao.tipo == TipoTransacao.DESPESA) MaterialTheme.cores.textoGasto else MaterialTheme.cores.textoGanho
    val intensidade = if (pendente) 0.7f else 1f
    val anoMes = transacao.anoMes
    val temDescricao = !transacao.descricao.isNullOrBlank()
    // Sem descrição o título já é o nome da categoria: não repetir no subtítulo
    val mostrarCategoria = temDescricao || categoria == null
    val temFatura = transacao.formaPagamento == FormaPagamento.CARTAO_CREDITO && anoMes != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = transacao.id >= 0, role = Role.Button, onClick = aoClicar)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(cor.copy(alpha = if (pendente) 0.10f else 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                iconeParaCategoria(categoria?.nome ?: ""),
                contentDescription = null,
                tint = cor.copy(alpha = intensidade),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                transacao.descricao?.takeIf { it.isNotBlank() }
                    ?: (categoria?.nome ?: if (pendente) "Transação futura" else "Transação"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (mostrarCategoria || temFatura) Spacer(modifier = Modifier.height(3.dp))
            if (mostrarCategoria || temFatura) Row(verticalAlignment = Alignment.CenterVertically) {
                if (mostrarCategoria) {
                    Text(
                        categoria?.nome ?: "Sem categoria",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (temFatura && anoMes != null) {
                    val partes = anoMes.split("-")
                    val mesFatura = partes.getOrNull(1) ?: ""
                    val anoFatura = partes.getOrNull(0)?.takeLast(2) ?: ""
                    if (mostrarCategoria) Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (pendente) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.primaryContainer
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Fatura $mesFatura/$anoFatura",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (pendente) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
            if (nomeCartao != null) {
                Spacer(modifier = Modifier.height(4.dp))
                EtiquetaCartao(nome = nomeCartao)
            }
        }

        Spacer(modifier = Modifier.width(10.dp))
        Text(
            (if (transacao.tipo == TipoTransacao.DESPESA) "- " else "+ ") +
                    formato.format(transacao.valor),
            color = corTexto.copy(alpha = intensidade),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

// =====================================================================
// Componentes de formulário / filtros
// =====================================================================

@Composable
private fun RotuloFiltro(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(10.dp))
}

/** Campo somente leitura que abre um seletor de data ao toque. */
@Composable
private fun CampoData(
    valor: String,
    rotulo: String,
    descricao: String,
    aoAbrir: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null
) {
    val interacao = remember { MutableInteractionSource() }
    LaunchedEffect(interacao) {
        interacao.interactions.collect {
            if (it is PressInteraction.Release) aoAbrir()
        }
    }
    OutlinedTextField(
        value = valor,
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(rotulo) },
        placeholder = placeholder?.let { { Text(it) } },
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        trailingIcon = {
            Icon(
                Icons.Filled.CalendarToday,
                contentDescription = descricao,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        },
        interactionSource = interacao,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeletorData(
    inicial: LocalDate,
    aoConfirmar: (LocalDate) -> Unit,
    aoFechar: () -> Unit
) {
    val estado = rememberDatePickerState(
        initialSelectedDateMillis = inicial.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = aoFechar,
        confirmButton = {
            TextButton(onClick = {
                estado.selectedDateMillis?.let { millis ->
                    aoConfirmar(Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate())
                }
                aoFechar()
            }) { Text("Confirmar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = aoFechar) { Text("Cancelar") }
        }
    ) {
        DatePicker(state = estado)
    }
}

// =====================================================================
// Edição de lançamento
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomSheetEditarTransacao(
    transacao: Transacao,
    categorias: List<Categoria>,
    onDismiss: () -> Unit,
    onSalvar: (Transacao) -> Unit,
    onExcluir: () -> Unit
) {
    var valor by rememberSaveable { mutableStateOf(transacao.valor.toString()) }
    var descricao by rememberSaveable { mutableStateOf(transacao.descricao ?: "") }
    var dataSelecionada by rememberSaveable { mutableStateOf(transacao.data) }
    var categoriaSelecionadaId by rememberSaveable { mutableStateOf(transacao.categoriaId) }
    val categoriaSelecionada = categorias.find { it.id == categoriaSelecionadaId }
    var menuExpandido by remember { mutableStateOf(false) }
    var mostrarDatePicker by rememberSaveable { mutableStateOf(false) }
    // Confirmação de exclusão troca o conteúdo deste mesmo sheet (um sheet por vez)
    var confirmando by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val categoriasDoTipo = categorias.filter { it.tipo == transacao.tipo }
    val formatoData = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val corTipo = if (transacao.tipo == TipoTransacao.DESPESA) Coral else Verde
    val corTextoTipo = if (transacao.tipo == TipoTransacao.DESPESA) MaterialTheme.cores.textoGasto else MaterialTheme.cores.textoGanho

    FolhaApp(onDismiss = onDismiss) {
        AnimatedContent(
            targetState = confirmando,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) using SizeTransform() },
            label = "confirmarExclusaoLancamento"
        ) { emConfirmacao ->
          if (emConfirmacao) {
            ConfirmarAcaoConteudo(
                titulo = "Excluir lançamento?",
                texto = "Esse lançamento será removido do extrato. Essa ação não pode ser desfeita.",
                textoConfirmar = "Excluir",
                onConfirmar = onExcluir,
                onCancelar = { confirmando = false }
            )
          } else {
          Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Editar lançamento",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(corTipo.copy(alpha = 0.14f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (transacao.tipo == TipoTransacao.DESPESA) "Gasto" else "Ganho",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = corTextoTipo
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(Tamanho.toque).background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        CircleShape
                    )
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Fechar")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = valor,
                onValueChange = { valor = it },
                label = { Text("Valor") },
                prefix = { Text("R$ ", fontWeight = FontWeight.Bold) },
                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            ExposedDropdownMenuBox(
                expanded = menuExpandido,
                onExpandedChange = { menuExpandido = it }
            ) {
                OutlinedTextField(
                    value = categoriaSelecionada?.nome ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Categoria") },
                    shape = RoundedCornerShape(14.dp),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpandido) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = menuExpandido,
                    onDismissRequest = { menuExpandido = false }
                ) {
                    categoriasDoTipo.forEach { categoria ->
                        DropdownMenuItem(
                            text = { Text(categoria.nome) },
                            onClick = {
                                categoriaSelecionadaId = categoria.id
                                menuExpandido = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = descricao,
                onValueChange = { descricao = it },
                label = { Text("Descrição (opcional)") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            CampoData(
                valor = dataSelecionada.format(formatoData),
                rotulo = "Data do lançamento",
                descricao = "Selecionar data",
                aoAbrir = { mostrarDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BotaoSecundario(
                    texto = "Excluir",
                    onClick = {
                        focusManager.clearFocus()
                        confirmando = true
                    },
                    modifier = Modifier.weight(1f),
                    cor = MaterialTheme.cores.textoGasto,
                    icone = Icons.Filled.DeleteOutline
                )
                BotaoPrincipal(
                    texto = "Salvar",
                    onClick = {
                        val valorDouble = valor.replace(",", ".").toDoubleOrNull()
                        val categoria = categoriaSelecionada
                        if (valorDouble != null && categoria != null) {
                            val novoAnoMes = String.format(Locale.ROOT, "%04d-%02d", dataSelecionada.year, dataSelecionada.monthValue)
                            onSalvar(
                                transacao.copy(
                                    valor = valorDouble,
                                    categoriaId = categoria.id,
                                    descricao = descricao.ifBlank { null },
                                    data = dataSelecionada,
                                    anoMes = novoAnoMes
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(Espaco.lg))
          }
          }
        }
    }

    if (mostrarDatePicker) {
        SeletorData(
            inicial = dataSelecionada,
            aoConfirmar = { dataSelecionada = it },
            aoFechar = { mostrarDatePicker = false }
        )
    }
}