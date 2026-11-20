package com.example.financacelular.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.financacelular.ui.theme.RoxoHero
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.YearMonth
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

private fun LocalDate.mesCurto(): String = month.getDisplayName(TextStyle.SHORT, LocaleBr).removeSuffix(".")

/** Linha grande do cabeçalho: "Novembro" para um mês; "3 meses" para meses fechados; senão "05–15 nov" ou "05/11 – 03/12". */
fun Periodo.tituloCurto(): String = when {
    ehMesUnico -> inicio.month.getDisplayName(TextStyle.FULL, LocaleBr).replaceFirstChar { it.uppercase() }
    mesesFechados -> "$quantidadeDeMeses meses"
    inicio == fim -> "%02d %s".format(inicio.dayOfMonth, inicio.mesCurto())
    YearMonth.from(inicio) == YearMonth.from(fim) -> "%02d–%02d %s".format(inicio.dayOfMonth, fim.dayOfMonth, inicio.mesCurto())
    else -> inicio.format(DateTimeFormatter.ofPattern("dd/MM")) + " – " + fim.format(DateTimeFormatter.ofPattern("dd/MM"))
}

/** Linha pequena do cabeçalho: o ano ("2026", ou "2025–2026"); para vários meses fechados, "set–nov 2026". */
fun Periodo.subtituloCurto(): String {
    val anos = if (inicio.year == fim.year) inicio.year.toString() else "${inicio.year}–${fim.year}"
    return when {
        !mesesFechados || ehMesUnico -> anos
        inicio.year == fim.year -> "${inicio.mesCurto()}–${fim.mesCurto()} ${inicio.year}"
        else -> "${inicio.mesCurto()} ${inicio.year} – ${fim.mesCurto()} ${fim.year}"
    }
}

/** Folha de baixo: atalhos e calendário de intervalo. Escolher um atalho, ou confirmar o intervalo, aplica e fecha. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolhaPeriodo(
    periodo: Periodo,
    aoEscolher: (Periodo) -> Unit,
    aoFechar: () -> Unit
) {
    val atalhos = remember { atalhosDePeriodo() }
    FolhaApp(onDismiss = aoFechar) {
        CabecalhoDeFolha(titulo = "Período", aoFechar = aoFechar)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            atalhos.forEach { (nome, valor) ->
                AtalhoDePeriodo(nome, selecionado = valor == periodo, onClick = { aoEscolher(valor) })
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Ou escolha as datas",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        CalendarioDeIntervalo(
            mesInicial = YearMonth.from(periodo.inicio),
            aoEscolher = aoEscolher
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

/** Calendário de intervalo com ‹ mês ›. Primeiro toque = início, segundo = fim; um toque antes do início recomeça. Confirmar aplica (só o início = um único dia). */
@Composable
private fun CalendarioDeIntervalo(
    mesInicial: YearMonth,
    aoEscolher: (Periodo) -> Unit,
    /** Modo data única: um toque escolhe o dia (já vem marcado [selecionado]) e Confirmar devolve esse dia. */
    unico: Boolean = false,
    selecionado: LocalDate? = null
) {
    var mes by remember { mutableStateOf(mesInicial) }
    var inicio by remember { mutableStateOf(selecionado) }
    var fim by remember { mutableStateOf<LocalDate?>(null) }
    val hoje = remember { LocalDate.now() }

    fun tocar(dia: LocalDate) {
        val i = inicio
        if (unico) {
            inicio = dia
        } else if (i == null || fim != null || dia.isBefore(i)) {
            inicio = dia
            fim = null
        } else {
            fim = dia
        }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { mes = mes.minusMonths(1) }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Mês anterior", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            Periodo.mes(mes).rotulo(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { mes = mes.plusMonths(1) }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "Próximo mês", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Row(Modifier.fillMaxWidth()) {
        listOf("D", "S", "T", "Q", "Q", "S", "S").forEach {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(vertical = 6.dp)
            )
        }
    }
    val vazios = mes.atDay(1).dayOfWeek.value % 7 // domingo = 0
    val celulas: List<LocalDate?> = List(vazios) { null } + (1..mes.lengthOfMonth()).map { mes.atDay(it) }
    celulas.chunked(7).forEach { semana ->
        Row(Modifier.fillMaxWidth()) {
            for (c in 0 until 7) {
                val dia = semana.getOrNull(c)
                val ultimo = fim ?: inicio
                val noIntervalo = dia != null && inicio != null && ultimo != null && !dia.isBefore(inicio) && !dia.isAfter(ultimo)
                val extremo = noIntervalo && (dia == inicio || dia == ultimo)
                // Faixa contínua: cantos arredondados nas pontas do intervalo e na quebra de semana/mês
                val raioEsq = if (!noIntervalo || dia == inicio || c == 0 || dia?.dayOfMonth == 1) 12.dp else 0.dp
                val raioDir = if (!noIntervalo || dia == ultimo || c == 6 || dia == mes.atEndOfMonth()) 12.dp else 0.dp
                val forma = RoundedCornerShape(topStart = raioEsq, bottomStart = raioEsq, topEnd = raioDir, bottomEnd = raioDir)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .padding(horizontal = if (noIntervalo) 0.dp else 2.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (dia != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(forma)
                                .background(
                                    when {
                                        extremo -> RoxoHero
                                        noIntervalo -> RoxoHero.copy(alpha = 0.16f)
                                        else -> Color.Transparent
                                    }
                                )
                                .then(if (dia == hoje && !noIntervalo) Modifier.border(1.dp, RoxoHero, forma) else Modifier)
                                .clickable(role = Role.Button) { tocar(dia) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                dia.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (extremo) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    BotaoPrincipal(
        texto = "Confirmar",
        onClick = { inicio?.let { aoEscolher(Periodo(it, fim ?: it)) } },
        enabled = inicio != null,
        modifier = Modifier.fillMaxWidth()
    )
}

/** Conteúdo "Data" (calendário de um dia só), para usar dentro de um sheet já aberto trocando o conteúdo. Escolher chama [aoEscolher]; o X chama [aoVoltar]. */
@Composable
fun ConteudoData(
    data: LocalDate,
    aoEscolher: (LocalDate) -> Unit,
    aoVoltar: () -> Unit,
    titulo: String = "Data"
) {
    Column(Modifier.fillMaxWidth()) {
        CabecalhoDeFolha(titulo = titulo, aoFechar = aoVoltar)
        Spacer(Modifier.height(8.dp))
        CalendarioDeIntervalo(
            mesInicial = YearMonth.from(data),
            aoEscolher = { aoEscolher(it.inicio) },
            unico = true,
            selecionado = data
        )
    }
}

/** Sheet de data para telas sem sheet pai. Dentro de um sheet, use [TrocaPorData]. */
@Composable
fun FolhaData(
    data: LocalDate,
    aoEscolher: (LocalDate) -> Unit,
    aoFechar: () -> Unit
) {
    FolhaApp(onDismiss = aoFechar) {
        ConteudoData(data = data, aoEscolher = { aoEscolher(it); aoFechar() }, aoVoltar = aoFechar)
    }
}

/**
 * Devolve uma função que tira o foco e fecha o teclado e **espera o teclado terminar de recolher** (no máximo 300 ms),
 * para que o calendário só entre depois, com a altura da folha já estável (sem "pular" com o `imePadding`).
 * Se o teclado já estava fechado, só tira o foco e volta na hora.
 */
@Composable
fun rememberFecharTeclado(): suspend () -> Unit {
    val foco = LocalFocusManager.current
    val teclado = LocalSoftwareKeyboardController.current
    val ime = WindowInsets.ime
    val densidade = LocalDensity.current
    return remember(foco, teclado, ime, densidade) {
        {
            val aberto = ime.getBottom(densidade) > 0
            foco.clearFocus(force = true)
            if (aberto) {
                teclado?.hide()
                withTimeoutOrNull(300) { snapshotFlow { ime.getBottom(densidade) }.first { it == 0 } }
            }
        }
    }
}

/**
 * Dentro de um sheet: troca o formulário pelo calendário de data (só fade, sem animar a altura) e volta ao escolher ou fechar.
 * Se o teclado estiver aberto, o formulário fica parado até ele fechar e só então o calendário entra.
 */
@Composable
fun TrocaPorData(
    escolhendo: Boolean,
    data: LocalDate,
    aoEscolher: (LocalDate) -> Unit,
    aoVoltar: () -> Unit,
    formulario: @Composable () -> Unit
) {
    val fecharTeclado = rememberFecharTeclado()
    var mostrando by remember { mutableStateOf(false) }
    LaunchedEffect(escolhendo) {
        if (escolhendo) fecharTeclado()
        mostrando = escolhendo
    }
    AnimatedContent(
        targetState = mostrando,
        transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) using SizeTransform(clip = false) { _, _ -> snap() } },
        label = "trocaPorData"
    ) { emData ->
        if (emData) {
            ConteudoData(data = data, aoEscolher = { aoEscolher(it); aoVoltar() }, aoVoltar = aoVoltar)
        } else {
            formulario()
        }
    }
}
