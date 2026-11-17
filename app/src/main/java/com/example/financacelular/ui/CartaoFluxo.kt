package com.example.financacelular.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.Verde
import com.example.financacelular.ui.theme.cores
import java.text.NumberFormat
import kotlin.math.abs

private val AlturaBarrasFluxo = 104.dp

private enum class VisaoDoFluxo(val texto: String) { GANHOS_E_GASTOS("Ganhos e gastos"), SALDO("Saldo") }

/**
 * Fluxo de caixa do período da Análise: saldo com variação, barras de ganhos e gastos (ou do saldo) por dia, semana ou mês,
 * e os números do período. Tocar numa barra foca nela; tocar de novo volta ao total.
 */
@Composable
fun CartaoFluxo(
    dados: FluxoDoPeriodo,
    periodo: Periodo,
    formato: NumberFormat,
    modifier: Modifier = Modifier
) {
    if (!dados.temMovimento) {
        CartaoAnalise(modifier) { EstadoVazio(Icons.Outlined.BarChart, "Sem movimentações neste período.") }
        return
    }

    var foco by remember(periodo) { mutableStateOf<Int?>(null) }
    var visao by remember { mutableStateOf(VisaoDoFluxo.GANHOS_E_GASTOS) }
    val indiceFocado = foco
    val barraFocada = indiceFocado?.let { dados.barras.getOrNull(it) }
    val ganhos = barraFocada?.ganhos ?: dados.ganhos
    val gastos = barraFocada?.gastos ?: dados.gastos
    val saldo = ganhos - gastos

    // Sem foco compara com o período anterior; com foco, com a barra de antes
    val referencia: Pair<Double, String>? = when {
        barraFocada == null -> if (dados.temBase) dados.saldoAnterior to dados.rotuloAnterior.removePrefix("período ") else null
        indiceFocado != null && indiceFocado > 0 -> dados.barras[indiceFocado - 1].let { it.saldo to it.rotulo }
        else -> null
    }

    CartaoAnalise(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 24.dp)) {
            Text(
                "Saldo · " + (barraFocada?.descricao ?: periodo.tituloCurto()),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (referencia != null) {
                Spacer(Modifier.width(8.dp))
                PilulaDeVariacao(saldo - referencia.first, referencia.second, formato)
            }
        }
        Text(
            (if (saldo >= 0) "+ " else "- ") + formato.format(abs(saldo)),
            modifier = Modifier.semantics {
                contentDescription = "Saldo ${if (barraFocada != null) "em ${barraFocada.descricao}" else "no período"}: ${formato.format(saldo)}"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (saldo >= 0) MaterialTheme.cores.textoGanho else MaterialTheme.cores.textoGasto,
            maxLines = 1,
            softWrap = false
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 28.dp)) {
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (visao == VisaoDoFluxo.GANHOS_E_GASTOS) {
                    LegendaPonto(Verde, "Ganhos")
                    LegendaPonto(Coral, "Gastos")
                }
            }
            SeletorDeVisao(visao) { visao = it }
        }
        Spacer(Modifier.height(10.dp))
        BarrasDeFluxo(dados, visao, foco, formato) { indice -> foco = if (foco == indice) null else indice }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumeroDoFluxo("Ganhos", formato.format(ganhos), MaterialTheme.cores.textoGanho, Modifier.weight(1f))
            NumeroDoFluxo("Gastos", formato.format(gastos), MaterialTheme.cores.textoGasto, Modifier.weight(1f))
            val poupou = percentualPoupado(ganhos, gastos)
            NumeroDoFluxo(
                "Poupou",
                poupou?.let { "$it%" } ?: "—",
                if (poupou != null && poupou < 0) MaterialTheme.cores.textoGasto else MaterialTheme.colorScheme.onSurface,
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PilulaDeVariacao(diferenca: Double, referencia: String, formato: NumberFormat) {
    val positiva = diferenca >= 0
    val cor = if (positiva) MaterialTheme.cores.textoGanho else MaterialTheme.cores.textoGasto
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background((if (positiva) Verde else Coral).copy(alpha = 0.16f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (positiva) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
            contentDescription = null,
            tint = cor,
            modifier = Modifier.size(Tamanho.iconePequeno)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            "${formato.format(abs(diferenca))} vs. $referencia",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = cor,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun SeletorDeVisao(visao: VisaoDoFluxo, aoMudar: (VisaoDoFluxo) -> Unit) {
    val forma = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(forma)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(2.dp)
    ) {
        VisaoDoFluxo.values().forEach { opcao ->
            val ativa = opcao == visao
            Text(
                opcao.texto,
                modifier = Modifier
                    .clip(forma)
                    .then(
                        if (ativa) Modifier
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, forma)
                        else Modifier
                    )
                    .clickable(role = Role.Tab) { aoMudar(opcao) }
                    .semantics { selected = ativa }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (ativa) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun BarrasDeFluxo(
    dados: FluxoDoPeriodo,
    visao: VisaoDoFluxo,
    foco: Int?,
    formato: NumberFormat,
    aoTocar: (Int) -> Unit
) {
    val quantidade = dados.barras.size
    val espaco = if (quantidade > 8) 4.dp else 8.dp
    val maiorValor = dados.barras.maxOf { maxOf(it.ganhos, it.gastos) }.coerceAtLeast(1.0)
    val maiorSaldo = dados.barras.maxOf { abs(it.saldo) }.coerceAtLeast(1.0)
    val corLinha = MaterialTheme.colorScheme.outline
    val saldoAtivo = visao == VisaoDoFluxo.SALDO

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(AlturaBarrasFluxo)) {
        val coluna = (maxWidth - espaco * (quantidade - 1)) / quantidade
        val larguraPar = (coluna * 0.34f).coerceIn(3.dp, 10.dp)
        val larguraSaldo = (coluna * 0.55f).coerceIn(4.dp, 24.dp)

        // Linha de base (ou a do zero, no meio, na visão de saldo)
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(if (saldoAtivo) Alignment.Center else Alignment.BottomCenter)
                .background(corLinha)
        )
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(espaco)) {
            dados.barras.forEachIndexed { indice, barra ->
                val selecionada = foco == indice
                val opacidade = if (foco != null && !selecionada) 0.4f else 1f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(role = Role.Button) { aoTocar(indice) }
                        .semantics {
                            this.selected = selecionada
                            contentDescription = if (saldoAtivo) {
                                "${barra.descricao}: saldo ${formato.format(barra.saldo)}"
                            } else {
                                "${barra.descricao}: ganhos ${formato.format(barra.ganhos)}, gastos ${formato.format(barra.gastos)}"
                            }
                        }
                ) {
                    if (!saldoAtivo) {
                        Row(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            ColunaDeFluxo((barra.ganhos / maiorValor).toFloat(), larguraPar, Verde.copy(alpha = opacidade), AlturaBarrasFluxo)
                            ColunaDeFluxo((barra.gastos / maiorValor).toFloat(), larguraPar, Coral.copy(alpha = opacidade), AlturaBarrasFluxo)
                        }
                    } else {
                        val metade = AlturaBarrasFluxo / 2
                        val fracao = (abs(barra.saldo) / maiorSaldo).toFloat()
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                                if (barra.saldo > 0.0) ColunaDeFluxo(fracao, larguraSaldo, Verde.copy(alpha = opacidade), metade)
                            }
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                if (barra.saldo < 0.0) ColunaDeFluxo(fracao, larguraSaldo, Coral.copy(alpha = opacidade), metade, paraBaixo = true)
                            }
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    RotulosDasBarras(dados.barras.map { RotuloDeBarra(it.rotulo, it.subrotulo, it.mostrarRotulo) }, foco, espaco)
}

@Composable
private fun ColunaDeFluxo(fracao: Float, largura: Dp, cor: Color, alturaMaxima: Dp, paraBaixo: Boolean = false) {
    val altura by animateDpAsState((alturaMaxima * fracao).coerceAtLeast(2.dp), tween(300), label = "alturaBarraFluxo")
    Box(
        Modifier
            .width(largura)
            .height(altura)
            .clip(
                if (paraBaixo) RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                else RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 1.dp, bottomEnd = 1.dp)
            )
            .background(cor)
    )
}

@Composable
private fun NumeroDoFluxo(rotulo: String, valor: String, cor: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(rotulo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            valor,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = cor,
            maxLines = 1,
            softWrap = false
        )
    }
}
