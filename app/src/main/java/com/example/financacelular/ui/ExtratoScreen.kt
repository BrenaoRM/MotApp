package com.example.financacelular.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import com.example.financacelular.ui.theme.Espaco
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.dimens
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtratoScreen(
    viewModel: ExtratoViewModel = viewModel(),
    textoPesquisa: String = "",
    acionarFiltrosExterno: Boolean = false,
    aoFiltrosAcionado: () -> Unit = {},
    aoVoltar: (() -> Unit)? = null
) {
    val transacoesOuNull by viewModel.todasTransacoes.collectAsStateWithLifecycle()
    val transacoes = transacoesOuNull ?: emptyList()
    val futuros by viewModel.futurosLancamentos.collectAsStateWithLifecycle()
    val categorias by viewModel.categorias.collectAsStateWithLifecycle()
    val cartoes by viewModel.cartoes.collectAsStateWithLifecycle()

    var filtroTipo by rememberSaveable { mutableStateOf<TipoTransacao?>(null) }
    var filtroCategoriaId by rememberSaveable { mutableStateOf<Long?>(null) }
    val filtroCategoria = categorias.find { it.id == filtroCategoriaId }
    var menuCategoriaExpandido by remember { mutableStateOf(false) }
    var transacaoEmEdicaoId by rememberSaveable { mutableStateOf<Long?>(null) }
    val transacaoEmEdicao = transacaoEmEdicaoId?.let { id ->
        transacoes.find { it.id == id } ?: futuros.find { it.id == id }
    }

    val formato = remember { NumberFormat.getCurrencyInstance(LocalePtBr) }
    var mostrarFiltros by rememberSaveable { mutableStateOf(false) }
    // Período do extrato: abre no mês atual
    var periodoInicio by rememberSaveable { mutableStateOf(YearMonth.now().atDay(1)) }
    var periodoFim by rememberSaveable { mutableStateOf(YearMonth.now().atEndOfMonth()) }
    val periodo = Periodo(periodoInicio, periodoFim)
    var mostrarFolhaPeriodo by rememberSaveable { mutableStateOf(false) }

    val algumFiltroAtivo = filtroTipo != null || filtroCategoriaId != null

    // O botão "Filtros" fica na ilha de ação (AppNavigation)
    LaunchedEffect(acionarFiltrosExterno) {
        if (acionarFiltrosExterno) {
            mostrarFiltros = true
            aoFiltrosAcionado()
        }
    }

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
            // O período fica no próprio cabeçalho; tocar nele abre a folha de escolha
            CabecalhoPadrao(
                titulo = periodo.tituloCurto(),
                subtitulo = "Extrato · " + periodo.subtituloCurto(),
                aoVoltar = aoVoltar,
                aoAnterior = { periodo.deslocar(-1).let { periodoInicio = it.inicio; periodoFim = it.fim } },
                aoSeguinte = { periodo.deslocar(1).let { periodoInicio = it.inicio; periodoFim = it.fim } },
                aoTocarTitulo = { mostrarFolhaPeriodo = true }
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
                    icone = Icons.Outlined.SearchOff,
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
                    icone = Icons.Outlined.Schedule
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
            cartoes = cartoes,
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
                Spacer(modifier = Modifier.height(Espaco.md))

                SeletorQuadrado(
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
                    }
                )

                Spacer(modifier = Modifier.height(Espaco.md))
                ExposedDropdownMenuBox(
                    expanded = menuCategoriaExpandido,
                    onExpandedChange = { menuCategoriaExpandido = it }
                ) {
                    CampoApp(
                        valor = filtroCategoria?.nome ?: "Todas as categorias",
                        aoMudar = {},
                        rotulo = "Categoria",
                        leitura = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuCategoriaExpandido) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
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
private fun ChipFiltroAtivo(texto: String, aoRemover: () -> Unit) {
    FilterChip(
        selected = true,
        onClick = aoRemover,
        label = { Text(texto, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingIcon = {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Remover filtro $texto",
                modifier = Modifier.size(Tamanho.iconePequeno)
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
                    icone = Icons.Outlined.ArrowDownward
                )
                ResumoMini(
                    modifier = Modifier.weight(1f),
                    rotulo = "Gastos",
                    valor = formato.format(despesas),
                    icone = Icons.Outlined.ArrowUpward
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
            Icon(icone, contentDescription = null, tint = onPrimary, modifier = Modifier.size(Tamanho.iconePequeno))
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
                modifier = Modifier.size(Tamanho.icone)
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
