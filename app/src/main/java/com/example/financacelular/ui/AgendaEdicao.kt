package com.example.financacelular.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.financacelular.data.AfazerEntity
import com.example.financacelular.data.CalendarService
import com.example.financacelular.data.EventoGoogleAgenda
import com.example.financacelular.ui.theme.RoxoHero
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.Verde
import com.example.financacelular.ui.theme.cores
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter


/** Dados de um item novo, guardados enquanto o usuário responde ao pedido de permissão. */
internal data class NovoItemAgenda(
    val titulo: String,
    val data: LocalDate,
    val hora: LocalTime,
    val horaFim: LocalTime,
    val diaInteiro: Boolean
)

/** Item da aba Agenda que pode ser editado: afazer local ou evento do Google Agenda. */
internal sealed interface ItemAgenda {
    data class Afazer(val afazer: AfazerEntity) : ItemAgenda
    data class Evento(val evento: EventoGoogleAgenda) : ItemAgenda
}

/** Cartão da aba Agenda no estilo do Google Agenda: usado por afazeres e eventos. */
@Composable
internal fun CartaoAgenda(
    titulo: String,
    horario: String,
    descricao: String?,
    onClick: () -> Unit,
    concluido: Boolean = false,
    onClickIcone: (() -> Unit)? = null
) {
    val formaCartao = RoundedCornerShape(20.dp)
    val corIcone = if (concluido) Verde else RoxoHero

    Card(
        shape = formaCartao,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(formaCartao)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(Tamanho.toque)
                    .clip(CircleShape)
                    .background(corIcone.copy(alpha = 0.15f))
                    .then(if (onClickIcone != null) Modifier.clickable(role = Role.Button, onClick = onClickIcone) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (concluido) Icons.Outlined.Check else Icons.Outlined.Event,
                    contentDescription = if (onClickIcone != null) {
                        if (concluido) "Marcar como pendente" else "Concluir"
                    } else null,
                    tint = corIcone,
                    modifier = Modifier.size(Tamanho.icone)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (concluido) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (concluido) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = horario,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (descricao != null) {
                    Text(
                        text = descricao,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Folha para editar (ou excluir) um afazer ou um evento do Google Agenda.
 * Ao tocar em Excluir, a confirmação troca o conteúdo da própria folha (campos preservados ao cancelar).
 */
@Composable
internal fun FolhaEdicaoAgenda(
    item: ItemAgenda,
    onDismiss: () -> Unit,
    onSalvar: (titulo: String, descricao: String?, data: LocalDate, hora: LocalTime, horaFim: LocalTime?, diaInteiro: Boolean) -> Unit,
    onExcluir: () -> Unit
) {
    val evento = (item as? ItemAgenda.Evento)?.evento
    val afazer = (item as? ItemAgenda.Afazer)?.afazer

    var titulo by remember(item) { mutableStateOf(evento?.titulo ?: afazer?.titulo ?: "") }
    var descricao by remember(item) {
        mutableStateOf(evento?.descricao?.takeIf { it != CalendarService.DESCRICAO_EVENTO_APP } ?: "")
    }
    var data by remember(item) { mutableStateOf(evento?.dataInicio ?: afazer?.data ?: LocalDate.now()) }
    var hora by remember(item) {
        mutableStateOf(if (evento != null && !evento.diaInteiro) horaDeMillis(evento.horaInicioMillis) else LocalTime.of(9, 0))
    }
    var horaFim by remember(item) {
        mutableStateOf(
            if (evento != null && !evento.diaInteiro && evento.horaFimMillis > evento.horaInicioMillis) {
                horaDeMillis(evento.horaFimMillis)
            } else {
                hora.plusHours(1)
            }
        )
    }
    var diaInteiro by remember(item) { mutableStateOf(evento?.diaInteiro ?: false) }
    val horarioInalterado = evento != null && !evento.diaInteiro && !diaInteiro &&
        evento.horaFimMillis > evento.horaInicioMillis &&
        hora == horaDeMillis(evento.horaInicioMillis) && horaFim == horaDeMillis(evento.horaFimMillis)
    var confirmandoExclusao by remember(item) { mutableStateOf(false) }
    var escolhendoData by remember(item) { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val coresCampo = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = RoxoHero,
        focusedLabelColor = RoxoHero,
        cursorColor = RoxoHero
    )

    FolhaApp(onDismiss = onDismiss) {
        AnimatedContent(
            targetState = confirmandoExclusao,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) using SizeTransform() },
            label = "confirmacaoExclusaoAgenda"
        ) { confirmando ->
            if (confirmando) {
                ConfirmarAcaoConteudo(
                    titulo = if (evento != null) "Excluir evento?" else "Excluir afazer?",
                    texto = if (evento != null) {
                        "Ele também será removido do Google Agenda. Essa ação não pode ser desfeita."
                    } else {
                        "Esse afazer será removido. Essa ação não pode ser desfeita."
                    },
                    textoConfirmar = "Excluir",
                    onConfirmar = onExcluir,
                    onCancelar = { confirmandoExclusao = false },
                    resumo = {
                        Text(
                            evento?.titulo ?: afazer?.titulo ?: "",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                )
            } else {
              TrocaPorData(
                escolhendo = escolhendoData,
                data = data,
                aoEscolher = { data = it },
                aoVoltar = { escolhendoData = false }
              ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CabecalhoDeFolha(
                        titulo = if (evento != null) "Editar evento" else "Editar afazer",
                        aoFechar = onDismiss
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    CampoApp(valor = titulo, aoMudar = { titulo = it }, rotulo = "Título")

                    if (evento != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = descricao,
                            onValueChange = { descricao = it },
                            label = { Text("Descrição") },
                            maxLines = 4,
                            shape = RoundedCornerShape(14.dp),
                            colors = coresCampo,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (evento != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Dia inteiro", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Switch(
                                checked = diaInteiro,
                                onCheckedChange = { diaInteiro = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = RoxoHero
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    SeletorDataHora(
                        data = data,
                        hora = hora,
                        horaFim = horaFim,
                        diaInteiro = diaInteiro,
                        mostrarHorario = evento != null,
                        aoEscolherData = { escolhendoData = true },
                        onHorario = { inicio, fim -> hora = inicio; horaFim = fim }
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    BotaoPrincipal(
                        texto = "Salvar alterações",
                        onClick = {
                            val descricaoFinal = if (evento != null) {
                                // Mantém a marca interna dos eventos criados pelo app quando o campo fica vazio
                                descricao.trim().ifEmpty {
                                    if (evento.descricao == CalendarService.DESCRICAO_EVENTO_APP) CalendarService.DESCRICAO_EVENTO_APP else ""
                                }
                            } else null
                            onSalvar(titulo.trim(), descricaoFinal, data, hora, if (horarioInalterado) null else horaFim, diaInteiro)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        cor = RoxoHero,
                        corConteudo = Color.White,
                        enabled = titulo.isNotBlank() && !(evento != null && !diaInteiro && horaFim == hora && !horarioInalterado)
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            focusManager.clearFocus()
                            confirmandoExclusao = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Tamanho.toque)
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.cores.textoGasto, modifier = Modifier.size(Tamanho.icone))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (evento != null) "Excluir evento" else "Excluir afazer",
                            color = MaterialTheme.cores.textoGasto,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
              }
            }
        }
    }
}

private fun horaDeMillis(millis: Long): LocalTime =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .withSecond(0)
        .withNano(0)

/** "Dia inteiro" ou o intervalo de horário (HH:mm – HH:mm) de um evento do Google Agenda. */
internal fun textoHorarioEvento(evento: EventoGoogleAgenda): String {
    if (evento.diaInteiro) return "Dia inteiro"
    val formato = DateTimeFormatter.ofPattern("HH:mm")
    val inicio = horaDeMillis(evento.horaInicioMillis).format(formato)
    return if (evento.horaFimMillis > evento.horaInicioMillis) {
        inicio + " – " + horaDeMillis(evento.horaFimMillis).format(formato)
    } else {
        inicio
    }
}
