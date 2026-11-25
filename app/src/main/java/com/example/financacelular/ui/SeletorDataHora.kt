package com.example.financacelular.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.RoxoHero
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.cores
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter


private enum class DialogoSeletor { INICIO, FIM }

/**
 * Cartão para escolher data e horário: uma linha com a data e dois blocos grandes (Início e Fim),
 * com atalhos de duração. Se o fim for antes do início, o evento termina no dia seguinte.
 * Ao mudar o início, o fim acompanha mantendo a mesma duração.
 */
@Composable
internal fun SeletorDataHora(
    data: LocalDate,
    hora: LocalTime,
    horaFim: LocalTime,
    diaInteiro: Boolean,
    mostrarHorario: Boolean,
    aoEscolherData: () -> Unit,
    onHorario: (inicio: LocalTime, fim: LocalTime) -> Unit,
    modifier: Modifier = Modifier
) {
    var dialogo by remember { mutableStateOf<DialogoSeletor?>(null) }
    val formatoHora = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val textoData = remember(data) {
        data.format(DateTimeFormatter.ofPattern("EEE, dd 'de' MMM 'de' yyyy", LocalePtBr))
            .replaceFirstChar { it.uppercase() }
    }
    val duracaoMin = ((Duration.between(hora, horaFim).toMinutes() % 1440) + 1440) % 1440

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .heightIn(min = Tamanho.toque)
                    .clickable(role = Role.Button, onClick = aoEscolherData),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(RoxoHero.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = RoxoHero, modifier = Modifier.size(Tamanho.icone))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Data",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        textoData,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "Alterar",
                    style = MaterialTheme.typography.labelMedium,
                    color = RoxoHero,
                    fontWeight = FontWeight.Bold
                )
            }

            if (mostrarHorario && !diaInteiro) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = RoxoHero.copy(alpha = 0.15f)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    BlocoHorario(
                        rotulo = "Início",
                        texto = hora.format(formatoHora),
                        modifier = Modifier.weight(1f)
                    ) { dialogo = DialogoSeletor.INICIO }
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(18.dp)
                    )
                    BlocoHorario(
                        rotulo = "Fim",
                        texto = horaFim.format(formatoHora),
                        erro = duracaoMin == 0L,
                        modifier = Modifier.weight(1f)
                    ) { dialogo = DialogoSeletor.FIM }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30L to "30 min", 60L to "1 h", 120L to "2 h").forEach { (minutos, rotulo) ->
                        ChipDuracao(rotulo, selecionado = duracaoMin == minutos) {
                            onHorario(hora, hora.plusMinutes(minutos))
                        }
                    }
                }

                if (duracaoMin == 0L) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "O fim precisa ser diferente do início.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.cores.textoGasto
                    )
                } else if (horaFim.isBefore(hora)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Termina no dia seguinte.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    when (dialogo) {
        DialogoSeletor.INICIO -> DialogoHora(
            titulo = "Horário de início",
            inicial = hora,
            onConfirmar = { nova ->
                val duracao = if (duracaoMin == 0L) 60L else duracaoMin
                onHorario(nova, nova.plusMinutes(duracao))
                dialogo = null
            },
            onDismiss = { dialogo = null }
        )
        DialogoSeletor.FIM -> DialogoHora(
            titulo = "Horário de término",
            inicial = horaFim,
            onConfirmar = { onHorario(hora, it); dialogo = null },
            onDismiss = { dialogo = null }
        )
        null -> Unit
    }
}

@Composable
private fun BlocoHorario(
    rotulo: String,
    texto: String,
    modifier: Modifier = Modifier,
    erro: Boolean = false,
    onClick: () -> Unit
) {
    val cor = if (erro) Coral else RoxoHero
    val forma = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(forma)
            .background(cor.copy(alpha = 0.10f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            rotulo,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = cor
        )
        Text(
            texto,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ChipDuracao(texto: String, selecionado: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selecionado) RoxoHero else RoxoHero.copy(alpha = 0.10f))
            .heightIn(min = Tamanho.toque)
            .selectable(selected = selecionado, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (selecionado) Color.White else RoxoHero
        )
    }
}

/** Relógio do Material 3 dentro de um diálogo arredondado, em formato 24 h. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoHora(
    titulo: String,
    inicial: LocalTime,
    onConfirmar: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val estado = rememberTimePickerState(
        initialHour = inicial.hour,
        initialMinute = inicial.minute,
        is24Hour = true
    )
    if (BloqueioApp.bloqueado) return // janela própria, ficaria por cima da tela bloqueada
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                )
                TimePicker(
                    state = estado,
                    colors = TimePickerDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        // Mostrador e ponteiro
                        clockDialColor = RoxoHero.copy(alpha = 0.10f),
                        selectorColor = RoxoHero,
                        clockDialSelectedContentColor = Color.White,
                        clockDialUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
                        // Caixas de hora e minuto
                        timeSelectorSelectedContainerColor = RoxoHero.copy(alpha = 0.15f),
                        timeSelectorSelectedContentColor = RoxoHero,
                        timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        timeSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirmar(LocalTime.of(estado.hour, estado.minute)) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoxoHero, contentColor = Color.White)
                    ) { Text("OK", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
