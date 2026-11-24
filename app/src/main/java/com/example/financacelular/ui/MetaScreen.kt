package com.example.financacelular.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.example.financacelular.ui.theme.Verde
import com.example.financacelular.ui.theme.cores
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.example.financacelular.ui.theme.Espaco
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.Meta
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.RoxoMeta
import com.example.financacelular.ui.theme.Tamanho
import java.text.NumberFormat
import java.util.Locale

private val ValoresRapidos = listOf(50, 100, 200, 500)

private fun valorParaTexto(valor: Double): String =
    if (valor % 1.0 == 0.0) valor.toLong().toString() else "%.2f".format(Locale.US, valor).replace(".", ",")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetaScreen(
    viewModel: MetaViewModel = viewModel(),
    acionarNovaMetaExterna: Boolean = false,
    aoMetaAcionada: () -> Unit = {},
    acionarGuardarValorExterno: Boolean = false,
    aoGuardarValorAcionado: () -> Unit = {},
    estadoLista: LazyListState
) {
    val metasCarregadas by viewModel.metas.collectAsStateWithLifecycle()
    val metas = metasCarregadas ?: emptyList()
    val formato = remember { NumberFormat.getCurrencyInstance(LocalePtBr) }

    var mostrarSheetNovaMeta by rememberSaveable { mutableStateOf(false) }
    var metaParaEditarId by rememberSaveable { mutableStateOf<Long?>(null) }
    var metaParaAdicionarValorId by rememberSaveable { mutableStateOf<Long?>(null) }
    var mostrarSheetGuardarValor by rememberSaveable { mutableStateOf(false) }
    val metaParaEditar: Meta? = metas.firstOrNull { it.id == metaParaEditarId }
    val metaParaAdicionarValor: Meta? = metas.firstOrNull { it.id == metaParaAdicionarValorId }

    var novoNome by rememberSaveable { mutableStateOf("") }
    var novoValorAlvo by rememberSaveable { mutableStateOf("") }

    val totalAcumulado = metas.sumOf { it.valorAtual }
    val totalAlvo = metas.sumOf { it.valorAlvo }

    LaunchedEffect(acionarNovaMetaExterna) {
        if (acionarNovaMetaExterna) {
            metaParaEditarId = null
            novoNome = ""
            novoValorAlvo = ""
            mostrarSheetNovaMeta = true
            aoMetaAcionada()
        }
    }

    LaunchedEffect(acionarGuardarValorExterno, metasCarregadas != null) {
        if (acionarGuardarValorExterno && metasCarregadas != null) {
            if (metas.isEmpty()) {
                // Sem metas ainda: leva direto para criar a primeira
                metaParaEditarId = null
                novoNome = ""
                novoValorAlvo = ""
                mostrarSheetNovaMeta = true
            } else {
                metaParaAdicionarValorId = (metas.firstOrNull { it.valorAtual < it.valorAlvo } ?: metas.first()).id
                mostrarSheetGuardarValor = true
            }
            aoGuardarValorAcionado()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ListaDePagina(estadoLista) {
            item {
                val metasConcluidas = metas.count { it.valorAlvo > 0 && it.valorAtual >= it.valorAlvo }
                // Cada meta contribui no máximo com o próprio alvo: meta acima de 100% não infla o progresso das outras
                val acumuladoLimitado = metas.sumOf { minOf(it.valorAtual, it.valorAlvo) }
                val progressoGeral = if (totalAlvo > 0) (acumuladoLimitado / totalAlvo).toFloat() else 0f
                CartaoHeroDePagina(RoxoMeta) {
                    Text(
                        "Total acumulado",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ValorDoHero(formato.format(totalAcumulado))
                    if (totalAlvo > 0) {
                        Spacer(modifier = Modifier.height(14.dp))
                        BarraDoHero(progressoGeral, RoxoMeta)
                        Spacer(modifier = Modifier.height(10.dp))
                        val destaque = SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            buildAnnotatedString {
                                withStyle(destaque) { append("${(progressoGeral.coerceIn(0f, 1f) * 100).toInt()}%") }
                                append(" de ${formato.format(totalAlvo)} · ")
                                withStyle(destaque) { append("$metasConcluidas de ${metas.size}") }
                                append(if (metasConcluidas == 1) " concluída" else " concluídas")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Seus objetivos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (metasCarregadas != null && metas.isEmpty()) {
                item {
                    EstadoVazio(
                        icone = Icons.Outlined.Flag,
                        mensagem = "Nenhuma meta cadastrada ainda. Crie o seu primeiro objetivo.",
                        textoAcao = "Nova meta",
                        aoAcao = {
                            metaParaEditarId = null
                            novoNome = ""
                            novoValorAlvo = ""
                            mostrarSheetNovaMeta = true
                        }
                    )
                }
            } else if (metas.isNotEmpty()) {
                item {
                    CartaoUnicoDeLinhas {
                        metas.forEachIndexed { indice, meta ->
                            if (indice > 0) DivisorDeLinha()
                            val progresso = if (meta.valorAlvo > 0)
                                (meta.valorAtual / meta.valorAlvo).toFloat().coerceIn(0f, 1f) else 0f
                            val concluida = meta.valorAlvo > 0 && meta.valorAtual >= meta.valorAlvo
                            val cor = if (concluida) Verde else RoxoMeta
                            LinhaDePagina(
                                inicio = {
                                    AnelDeProgresso(progresso, cor) {
                                        if (concluida) {
                                            Icon(Icons.Outlined.Check, contentDescription = "Concluída", tint = cor, modifier = Modifier.size(Tamanho.icone))
                                        } else {
                                            Text(
                                                "${(progresso * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                },
                                titulo = meta.nome,
                                valor = formato.format(meta.valorAtual),
                                subtitulo = if (concluida) "Concluída" else "de ${formato.format(meta.valorAlvo)}",
                                corSubtitulo = if (concluida) MaterialTheme.cores.textoGanho else Color.Unspecified,
                                onClick = {
                                    metaParaEditarId = meta.id
                                    novoNome = meta.nome
                                    novoValorAlvo = meta.valorAlvo.toString()
                                    mostrarSheetNovaMeta = true
                                }
                            )
                        }
                    }
                }
            }
        }

        // --- Sheet para Criar/Editar Meta (a confirmação de excluir troca o conteúdo do mesmo sheet) ---
        if (mostrarSheetNovaMeta) {
            var confirmandoExclusao by rememberSaveable { mutableStateOf(false) }
            FolhaApp(onDismiss = { mostrarSheetNovaMeta = false }) {
                val focusManager = LocalFocusManager.current
                AnimatedContent(
                    targetState = confirmandoExclusao,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) using SizeTransform() },
                    label = "confirmarExclusaoMeta"
                ) { confirmando ->
                    if (confirmando && metaParaEditar != null) {
                        ConfirmarAcaoConteudo(
                            titulo = "Excluir meta?",
                            texto = "A meta \"${metaParaEditar.nome}\" e o valor já guardado nela serão removidos. Essa ação não pode ser desfeita.",
                            textoConfirmar = "Excluir",
                            onConfirmar = {
                                viewModel.excluir(metaParaEditar)
                                mostrarSheetNovaMeta = false
                            },
                            onCancelar = { confirmandoExclusao = false }
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            CabecalhoDeFolha(
                                titulo = if (metaParaEditar == null) "Nova Meta" else "Editar Meta",
                                aoFechar = { mostrarSheetNovaMeta = false }
                            )

                            Spacer(modifier = Modifier.height(Espaco.md))

                            CampoApp(valor = novoNome, aoMudar = { novoNome = it }, rotulo = "Nome")

                            Spacer(modifier = Modifier.height(Espaco.sm))

                            CampoValor(valor = novoValorAlvo, aoMudar = { novoValorAlvo = it }, legenda = "Valor alvo")

                            Spacer(modifier = Modifier.height(Espaco.lg))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (metaParaEditar != null) {
                                    BotaoSecundario(
                                        texto = "Excluir",
                                        onClick = {
                                            focusManager.clearFocus()
                                            confirmandoExclusao = true
                                        },
                                        modifier = Modifier.weight(1f),
                                        cor = Coral,
                                    )
                                }

                                BotaoPrincipal(
                                    texto = if (metaParaEditar == null) "Criar" else "Salvar",
                                    onClick = {
                                        val valorDouble = novoValorAlvo.replace(",", ".").toDoubleOrNull() ?: 0.0
                                        if (novoNome.isNotBlank() && valorDouble > 0) {
                                            if (metaParaEditar == null) {
                                                viewModel.criar(novoNome, valorDouble)
                                            } else {
                                                viewModel.atualizar(metaParaEditar.copy(nome = novoNome, valorAlvo = valorDouble))
                                            }
                                            mostrarSheetNovaMeta = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    cor = RoxoMeta,
                                    corConteudo = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Folha "Guardar valor": escolhe a meta, valores rápidos e prévia do progresso ---
        if (mostrarSheetGuardarValor) {
            var valorInput by rememberSaveable { mutableStateOf("") }
            val metaAtual = metaParaAdicionarValor
            val valorDigitado = valorInput.replace(",", ".").toDoubleOrNull() ?: 0.0

            val focoGuardar = rememberFocoValorAutomatico()
            FolhaApp(onDismiss = { mostrarSheetGuardarValor = false }) {
                CabecalhoDeFolha(titulo = "Guardar valor", aoFechar = { mostrarSheetGuardarValor = false })
                Spacer(modifier = Modifier.height(Espaco.md))

                // Seletor de meta (blocos com mini-progresso)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(metas) { m ->
                        val prog = if (m.valorAlvo > 0) (m.valorAtual / m.valorAlvo).toFloat().coerceIn(0f, 1f) else 0f
                        BlocoDeEscolha(
                            selecionado = m == metaAtual,
                            onClick = { metaParaAdicionarValorId = m.id },
                            modifier = Modifier.width(132.dp)
                        ) {
                            Column {
                                Text(
                                    m.nome,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { prog },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = RoxoMeta,
                                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "${(prog * 100).toInt()}% · ${formato.format(m.valorAtual)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Espaco.md))
                CampoValor(valor = valorInput, aoMudar = { valorInput = it }, foco = focoGuardar)
                Spacer(modifier = Modifier.height(Espaco.sm))

                // Valores rápidos: cada toque soma ao valor digitado
                AtalhosDeValor(
                    opcoes = ValoresRapidos.map { "+$it" },
                    selecionado = -1,
                    aoSelecionar = { i -> valorInput = valorParaTexto(valorDigitado + ValoresRapidos[i]) }
                )

                // Prévia: como a meta fica depois do aporte
                if (metaAtual != null && valorDigitado > 0 && metaAtual.valorAlvo > 0) {
                    val novoTotal = metaAtual.valorAtual + valorDigitado
                    val progAntes = (metaAtual.valorAtual / metaAtual.valorAlvo).toFloat().coerceIn(0f, 1f)
                    val progDepois = (novoTotal / metaAtual.valorAlvo).toFloat().coerceIn(0f, 1f)
                    val progAnimado by animateFloatAsState(targetValue = progDepois, label = "previaProgresso")

                    Spacer(modifier = Modifier.height(Espaco.lg))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            metaAtual.nome,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (novoTotal >= metaAtual.valorAlvo) "Meta alcançada" else "${(progAntes * 100).toInt()}% → ${(progDepois * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progAnimado },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = RoxoMeta,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                }

                Spacer(modifier = Modifier.height(Espaco.lg))

                BotaoPrincipal(
                    texto = if (valorDigitado > 0) "Guardar ${formato.format(valorDigitado)}" else "Guardar",
                    onClick = {
                        if (metaAtual != null && valorDigitado > 0) {
                            viewModel.adicionarValor(metaAtual, valorDigitado)
                            mostrarSheetGuardarValor = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    cor = RoxoMeta,
                    corConteudo = Color.White,
                    enabled = metaAtual != null && valorDigitado > 0
                )
                Spacer(modifier = Modifier.height(Espaco.sm))
            }
        }
    }
}
