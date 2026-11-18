package com.example.financacelular.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Savings
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.cores
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.roundToInt

private val AlturaBarrasAportes = 104.dp

/**
 * Aportes do período da Análise: valor, variação sobre o período anterior, barras empilhadas por tipo de ativo
 * (dia, semana ou mês, conforme o período) e a divisão por tipo. Tocar numa barra foca nela; tocar de novo volta ao total.
 */
@Composable
fun CartaoAportes(
    dados: AportesDoPeriodo,
    periodo: Periodo,
    formato: NumberFormat,
    modifier: Modifier = Modifier
) {
    if (!dados.temAportes) {
        CartaoAnalise(modifier) { EstadoVazio(Icons.Outlined.Savings, "Sem aportes neste período.") }
        return
    }

    var foco by remember(periodo) { mutableStateOf<Int?>(null) }
    val barraFocada = foco?.let { dados.barras.getOrNull(it) }
    val valores = barraFocada?.valores ?: dados.tipos.map { it.total }
    val totalExibido = valores.sum()
    val corOutros = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val cores = dados.tipos.mapIndexed { i, tipo -> if (tipo.ehOutros) corOutros else PaletaComposicao[i] }

    CartaoAnalise(modifier) {
        ValorDosAportes(totalExibido, formato, descricao = barraFocada?.let { "Aportes em ${it.descricao}: ${formato.format(totalExibido)}" }
            ?: "Aportes no período: ${formato.format(totalExibido)}")
        LinhaDeVariacao(dados, barraFocada)
        Spacer(Modifier.height(12.dp))
        BarrasDeAportes(dados, cores, foco, formato) { indice -> foco = if (foco == indice) null else indice }
        Spacer(Modifier.height(14.dp))
        if (totalExibido > 0.0) {
            DivisaoPorTipo(dados.tipos, valores, cores, totalExibido, formato)
        } else {
            Text(
                "Sem aportes neste intervalo",
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ValorDosAportes(valor: Double, formato: NumberFormat, descricao: String) {
    val corTexto = MaterialTheme.colorScheme.onSurface
    val texto = formato.format(valor).replace("R$", "").trim()
    val posVirgula = texto.lastIndexOf(',')
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontSize = 12.sp, color = corTexto.copy(alpha = 0.6f))) { append("R$ ") }
            if (posVirgula > 0) {
                append(texto.substring(0, posVirgula))
                withStyle(SpanStyle(fontSize = 15.sp, color = corTexto.copy(alpha = 0.55f))) { append(texto.substring(posVirgula)) }
            } else {
                append(texto)
            }
        },
        modifier = Modifier.semantics { contentDescription = descricao },
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = corTexto,
        maxLines = 1,
        softWrap = false
    )
}

/** Com uma barra focada, diz qual é; sem foco, a variação sobre o período anterior (some se não houver base). */
@Composable
private fun LinhaDeVariacao(dados: AportesDoPeriodo, barraFocada: BarraDeAportes?) {
    val variacao = dados.variacaoPercentual
    Row(
        modifier = Modifier.heightIn(min = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (barraFocada == null && variacao != null) {
            val cor = if (variacao >= 0) MaterialTheme.cores.textoGanho else MaterialTheme.cores.textoGasto
            Icon(
                if (variacao >= 0) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                contentDescription = null,
                tint = cor,
                modifier = Modifier.size(Tamanho.iconePequeno)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                "${abs(variacao)}% vs. ${dados.rotuloAnterior}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = cor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun BarrasDeAportes(
    dados: AportesDoPeriodo,
    cores: List<Color>,
    foco: Int?,
    formato: NumberFormat,
    aoTocar: (Int) -> Unit
) {
    val maximo = dados.barras.maxOf { it.total }.coerceAtLeast(1.0)
    val espaco = if (dados.barras.size > 8) 4.dp else 8.dp
    val neutra = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f)

    Row(
        modifier = Modifier.fillMaxWidth().height(AlturaBarrasAportes),
        horizontalArrangement = Arrangement.spacedBy(espaco),
        verticalAlignment = Alignment.Bottom
    ) {
        dados.barras.forEachIndexed { indice, barra ->
            val selecionada = foco == indice
            val altura by animateDpAsState(
                (AlturaBarrasAportes * (barra.total / maximo).toFloat()).coerceAtLeast(4.dp),
                tween(300),
                label = "alturaBarraAporte"
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selecionada) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f) else Color.Transparent)
                    .clickable(role = Role.Button) { aoTocar(indice) }
                    .semantics {
                        this.selected = selecionada
                        contentDescription = "${barra.descricao}: ${formato.format(barra.total)}"
                    },
                verticalArrangement = Arrangement.Bottom
            ) {
                Column(modifier = Modifier.fillMaxWidth().height(altura).clip(RoundedCornerShape(5.dp))) {
                    if (barra.total <= 0.0) {
                        Box(Modifier.fillMaxWidth().weight(1f).background(neutra))
                    } else {
                        val opacidade = if (foco != null && !selecionada) 0.35f else 1f
                        // O maior tipo fica embaixo: a coluna começa pelo menor
                        for (tipo in barra.valores.indices.reversed()) {
                            val valor = barra.valores[tipo]
                            if (valor > 0.0) {
                                Box(Modifier.fillMaxWidth().weight(valor.toFloat()).background(cores[tipo].copy(alpha = opacidade)))
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

internal class RotuloDeBarra(val texto: String, val subtexto: String?, val mostrar: Boolean)

/** Linha de rótulos sob as barras (dia, semana ou mês); o da barra focada fica em destaque. */
@Composable
internal fun RotulosDasBarras(rotulos: List<RotuloDeBarra>, foco: Int?, espaco: Dp) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(espaco)) {
        rotulos.forEachIndexed { indice, rotulo ->
            val cor = if (foco == indice) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (rotulo.mostrar) rotulo.texto else "",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (foco == indice) FontWeight.Bold else FontWeight.Medium,
                    color = cor,
                    maxLines = 1,
                    softWrap = false
                )
                if (rotulo.subtexto != null) {
                    Text(
                        rotulo.subtexto,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/** Até 4 tipos em duas colunas com %, barra fina e valor. */
@Composable
private fun DivisaoPorTipo(
    tipos: List<TipoDeAporte>,
    valores: List<Double>,
    cores: List<Color>,
    total: Double,
    formato: NumberFormat
) {
    val ordem = tipos.indices.filter { valores[it] > 0.0 }.sortedByDescending { valores[it] }
    val percentual = { indice: Int -> (valores[indice] / total * 100).roundToInt() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ordem.take(4).chunked(2).forEach { linha ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                linha.forEach { indice ->
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(cores[indice], CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                tipos[indice].nome,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${percentual(indice)}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(5.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth((valores[indice] / total).toFloat().coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(cores[indice])
                            )
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            formato.format(valores[indice]),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
                if (linha.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
