package com.example.financacelular.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.financacelular.ui.theme.RoxoHero
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val LocaleBr: Locale = Locale.Builder().setLanguage("pt").setRegion("BR").build()

/** Intervalo de datas (inclusive) analisado na tela de Análise. */
data class Periodo(val inicio: LocalDate, val fim: LocalDate) {
    val dias: Int get() = (ChronoUnit.DAYS.between(inicio, fim) + 1).toInt()
    val mesesFechados: Boolean get() = inicio.dayOfMonth == 1 && fim == YearMonth.from(fim).atEndOfMonth()
    val quantidadeDeMeses: Int get() = (fim.year - inicio.year) * 12 + fim.monthValue - inicio.monthValue + 1
    val ehMesUnico: Boolean get() = mesesFechados && quantidadeDeMeses == 1

    /** Período vizinho do mesmo tamanho: meses fechados andam de mês em mês; o resto anda pelo número de dias. */
    fun deslocar(direcao: Int): Periodo =
        if (mesesFechados) {
            val meses = quantidadeDeMeses
            val novoInicio = inicio.plusMonths((direcao * meses).toLong())
            Periodo(novoInicio, YearMonth.from(novoInicio).plusMonths(meses - 1L).atEndOfMonth())
        } else {
            val passo = (direcao * dias).toLong()
            Periodo(inicio.plusDays(passo), fim.plusDays(passo))
        }

    companion object {
        fun mes(anoMes: YearMonth) = Periodo(anoMes.atDay(1), anoMes.atEndOfMonth())
    }
}

fun atalhosDePeriodo(hoje: LocalDate = LocalDate.now()): List<Pair<String, Periodo>> {
    val mes = YearMonth.from(hoje)
    return listOf(
        "Este mês" to Periodo.mes(mes),
        "Mês passado" to Periodo.mes(mes.minusMonths(1)),
        "Últimos 3 meses" to Periodo(mes.minusMonths(2).atDay(1), mes.atEndOfMonth()),
        "Este ano" to Periodo(LocalDate.of(hoje.year, 1, 1), LocalDate.of(hoje.year, 12, 31)),
        "Últimos 12 meses" to Periodo(mes.minusMonths(11).atDay(1), mes.atEndOfMonth())
    )
}

/** "Novembro 2026" para um mês; o nome do atalho quando é um; senão "05/11 – 15/11/26". */
fun Periodo.rotulo(): String {
    if (ehMesUnico) {
        return inicio.month.getDisplayName(TextStyle.FULL, LocaleBr).replaceFirstChar { it.uppercase() } + " " + inicio.year
    }
    atalhosDePeriodo().firstOrNull { it.second == this }?.let { return it.first }
    return inicio.format(DateTimeFormatter.ofPattern("dd/MM")) + " – " + fim.format(DateTimeFormatter.ofPattern("dd/MM/yy"))
}

private fun LocalDate.paraMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.paraData(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Linha única: período anterior, o nome do período (abre a folha de escolha) e próximo. */
@Composable
fun SeletorPeriodo(
    periodo: Periodo,
    aoAnterior: () -> Unit,
    aoSeguinte: () -> Unit,
    aoAbrir: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = aoAnterior, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Período anterior", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clip(CircleShape)
                .then(
                    if (aoAbrir != null) Modifier.clickable(onClickLabel = "Escolher período", role = Role.Button, onClick = aoAbrir)
                    else Modifier
                )
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = RoxoHero, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(
                periodo.rotulo(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 170.dp)
            )
        }
        IconButton(onClick = aoSeguinte, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Próximo período", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Folha de baixo: atalhos e calendário de intervalo. Escolher um atalho, ou o segundo dia, aplica e fecha. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FolhaPeriodo(
    periodo: Periodo,
    aoEscolher: (Periodo) -> Unit,
    aoFechar: () -> Unit
) {
    val atalhos = remember { atalhosDePeriodo() }
    val estado = rememberDateRangePickerState(initialDisplayedMonthMillis = periodo.inicio.paraMillis())

    LaunchedEffect(estado.selectedStartDateMillis, estado.selectedEndDateMillis) {
        val inicio = estado.selectedStartDateMillis
        val fim = estado.selectedEndDateMillis
        if (inicio != null && fim != null) aoEscolher(Periodo(inicio.paraData(), fim.paraData()))
    }

    FolhaApp(onDismiss = aoFechar) {
        CabecalhoDeFolha(titulo = "Período", aoFechar = aoFechar)
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            atalhos.forEach { (nome, valor) ->
                AtalhoDePeriodo(nome, selecionado = valor == periodo, onClick = { aoEscolher(valor) })
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Ou escolha as datas",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        DateRangePicker(
            state = estado,
            modifier = Modifier.height(460.dp),
            title = null,
            headline = null,
            showModeToggle = false,
            colors = DatePickerDefaults.colors(
                containerColor = Color.Transparent,
                selectedDayContainerColor = RoxoHero,
                selectedDayContentColor = Color.White,
                dayInSelectionRangeContainerColor = RoxoHero.copy(alpha = 0.16f),
                dayInSelectionRangeContentColor = MaterialTheme.colorScheme.onSurface,
                todayDateBorderColor = RoxoHero,
                todayContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}

@Composable
private fun AtalhoDePeriodo(texto: String, selecionado: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selecionado) RoxoHero.copy(alpha = 0.16f) else Color.Transparent,
        border = BorderStroke(1.dp, if (selecionado) RoxoHero else MaterialTheme.colorScheme.outline),
        modifier = Modifier.semantics {
            selected = selecionado
            role = Role.Button
        }
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}
