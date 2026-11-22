package com.example.financacelular.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.Espaco
import com.example.financacelular.ui.theme.TealParcelados
import com.example.financacelular.ui.theme.Verde
import com.example.financacelular.ui.theme.cores
import java.text.NumberFormat
import com.example.financacelular.data.FORMATO_DATA

@Composable
fun ParceladosScreen(
    viewModel: ParceladosViewModel = viewModel(),
    estadoLista: LazyListState
) {
    val comprasCarregadas by viewModel.comprasAgrupadas.collectAsStateWithLifecycle()
    val comprasAgrupadas = comprasCarregadas.orEmpty()
    val formatoMoeda = remember { NumberFormat.getCurrencyInstance(LocalePtBr) }
    val totalRestante = comprasAgrupadas.sumOf { it.valorRestante }
    val parcelasPagas = comprasAgrupadas.sumOf { it.parcelasPagas }
    val parcelasTotais = comprasAgrupadas.sumOf { it.totalParcelas }

    // O detalhe é buscado na lista atual (a chave é estável), assim ele acompanha o cancelamento de parcelas
    var compraDetalheChave by remember { mutableStateOf<String?>(null) }
    var confirmandoCancelamento by remember { mutableStateOf(false) }
    val compraDetalhe = compraDetalheChave?.let { chave -> comprasAgrupadas.find { it.chave == chave } }

    ListaDePagina(estadoLista) {
        item {
            CartaoHeroDePagina(TealParcelados) {
                Text(
                    "Restante a pagar",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                ValorDoHero(formatoMoeda.format(totalRestante))
                if (parcelasTotais > 0) {
                    Spacer(modifier = Modifier.height(14.dp))
                    BarraDoHero(parcelasPagas.toFloat() / parcelasTotais, TealParcelados)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(Tamanho.iconePequeno)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) {
                                    append("$parcelasPagas de $parcelasTotais")
                                }
                                append(" parcelas pagas")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            Text("Compras parceladas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (comprasCarregadas?.isEmpty() == true) {
            item(key = "vazio") {
                EstadoVazio(
                    icone = Icons.Outlined.ShoppingCart,
                    mensagem = "Nenhuma compra parcelada em andamento.",
                    modifier = Modifier.animateItem()
                )
            }
        } else if (comprasAgrupadas.isNotEmpty()) {
            item(key = "compras") {
                CartaoUnicoDeLinhas {
                    comprasAgrupadas.forEachIndexed { indice, compra ->
                        if (indice > 0) DivisorDeLinha()
                        val quitada = compra.quitada
                        val cor = corDaCompra(compra)
                        LinhaDePagina(
                            inicio = { AnelDaCompra(compra, cor) },
                            titulo = compra.descricaoBase,
                            valor = when {
                                quitada -> "Quitada"
                                compra.encerrada -> "Encerrada"
                                else -> formatoMoeda.format(compra.valorRestante)
                            },
                            corValor = if (quitada) MaterialTheme.cores.textoGanho else Color.Unspecified,
                            subtitulo = "${compra.totalParcelas}x de ${formatoMoeda.format(compra.valorParcela)}" +
                                (compra.cartaoNome?.let { " · $it" } ?: ""),
                            onClick = {
                                confirmandoCancelamento = false
                                compraDetalheChave = compra.chave
                            }
                        )
                    }
                }
            }
        }
    }

    // ---------------- SHEET: DETALHE DA COMPRA (a confirmação troca o conteúdo do mesmo sheet) ----------------
    compraDetalhe?.let { compra ->
        val fechar = { compraDetalheChave = null; confirmandoCancelamento = false }
        FolhaApp(onDismiss = fechar) {
            AnimatedContent(
                targetState = confirmandoCancelamento,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) using SizeTransform() },
                label = "confirmarCancelamentoParcelas"
            ) { confirmando ->
                if (confirmando) {
                    ConfirmarAcaoConteudo(
                        titulo = "Cancelar parcelas?",
                        texto = "As ${compra.parcelasRestantes} parcelas restantes de \"${compra.descricaoBase}\" serão removidas. Essa ação não pode ser desfeita.",
                        textoConfirmar = "Cancelar parcelas",
                        onConfirmar = {
                            viewModel.cancelarParcelasRestantes(compra.transacoesPendentes)
                            fechar()
                        },
                        onCancelar = { confirmandoCancelamento = false }
                    )
                } else {
                    DetalheDaCompra(
                        compra = compra,
                        formatoMoeda = formatoMoeda,
                        aoFechar = fechar,
                        aoCancelar = { confirmandoCancelamento = true }
                    )
                }
            }
        }
    }
}

/** Verde quando quitada, neutra quando encerrada (parcelas canceladas), teal em andamento. */
@Composable
private fun corDaCompra(compra: CompraParceladaAgrupada): Color = when {
    compra.quitada -> Verde
    compra.encerrada -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> TealParcelados
}

/** Anel da compra: parcelas pagas / total dentro do anel; verde com check quando quitada. */
@Composable
private fun AnelDaCompra(compra: CompraParceladaAgrupada, cor: Color) {
    val progresso = if (compra.totalParcelas > 0) compra.parcelasPagas.toFloat() / compra.totalParcelas else 0f
    AnelDeProgresso(progresso, cor) {
        if (compra.quitada) {
            Icon(Icons.Outlined.Check, contentDescription = "Quitada", tint = cor, modifier = Modifier.size(Tamanho.icone))
        } else {
            Text(
                "${compra.parcelasPagas}/${compra.totalParcelas}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DetalheDaCompra(
    compra: CompraParceladaAgrupada,
    formatoMoeda: NumberFormat,
    aoFechar: () -> Unit,
    aoCancelar: () -> Unit
) {
    val quitada = compra.quitada
    Column(modifier = Modifier.fillMaxWidth()) {
        CabecalhoDeFolha(
            titulo = compra.descricaoBase,
            aoFechar = aoFechar,
            subtitulo = "${compra.totalParcelas}x de ${formatoMoeda.format(compra.valorParcela)}" +
                (compra.cartaoNome?.let { " · $it" } ?: "")
        )
        Spacer(modifier = Modifier.height(Espaco.lg))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                ValorDoHero(formatoMoeda.format(compra.valorRestante))
                Text(
                    if (quitada) "Compra totalmente quitada"
                    else if (compra.encerrada) "Compra encerrada"
                    else "restante · " + if (compra.parcelasRestantes == 1) "1 parcela" else "${compra.parcelasRestantes} parcelas",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnelDaCompra(compra, corDaCompra(compra))
        }

        Spacer(modifier = Modifier.height(Espaco.md))
        LinhaDeDetalhe("Data da compra", compra.dataCompra.format(FORMATO_DATA))
        LinhaDeDetalhe("Parcelas pagas", "${compra.parcelasPagas} de ${compra.totalParcelas}")
        LinhaDeDetalhe("Total já pago", formatoMoeda.format(compra.valorPago))
        LinhaDeDetalhe("Valor total", formatoMoeda.format(compra.valorTotal))

        if (compra.transacoesPendentes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Espaco.xl))
            BotaoSecundario(
                texto = "Cancelar ${compra.parcelasRestantes} parcelas",
                onClick = aoCancelar,
                modifier = Modifier.fillMaxWidth(),
                cor = MaterialTheme.cores.textoGasto
            )
        }
        Spacer(modifier = Modifier.height(Espaco.lg))
    }
}
