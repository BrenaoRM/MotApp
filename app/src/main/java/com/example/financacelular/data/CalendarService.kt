package com.example.financacelular.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

data class EventoGoogleAgenda(
    val id: Long,
    val titulo: String,
    val descricao: String?,
    /** Data real de início do evento (e não apenas o dia que está sendo exibido). */
    val dataInicio: LocalDate,
    val horaInicioMillis: Long,
    val horaFimMillis: Long,
    val diaInteiro: Boolean = false,
    /** Eventos que se repetem: editar/excluir mexeria na série inteira, então ficam só para leitura. */
    val recorrente: Boolean = false,
    /** Calendários em que o app não pode gravar (feriados, aniversários, agendas de terceiros...). */
    val somenteLeitura: Boolean = false
) {
    val editavel: Boolean get() = !recorrente && !somenteLeitura
}

enum class ResultadoCalendario {
    SUCESSO,
    SEM_PERMISSAO,
    SEM_CALENDARIO,
    ERRO
}

object CalendarService {

    /** Descrição gravada nos eventos criados pelo app (também usada para não exibir duplicatas antigas). */
    const val DESCRICAO_EVENTO_APP = "Lembrete financeiro - Finança Celular"

    private const val MILLIS_HORA = 60L * 60L * 1000L
    private const val MILLIS_DIA = 24L * MILLIS_HORA

    fun temPermissaoLeitura(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED

    fun temPermissaoEscrita(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED

    private data class InfoEvento(val recorrente: Boolean, val somenteLeitura: Boolean)

    /**
     * Fim do evento em millis. Se [horaFim] não for depois de [hora], o evento termina
     * no dia seguinte (ex.: 22:00 até 01:00).
     */
    private fun fimEmMillis(data: LocalDate, hora: LocalTime, horaFim: LocalTime, zona: ZoneId): Long {
        val inicio = data.atTime(hora)
        var fim = data.atTime(horaFim)
        if (!fim.isAfter(inicio)) fim = fim.plusDays(1)
        return fim.atZone(zona).toInstant().toEpochMilli()
    }

    /**
     * Lê os eventos do Google Agenda (apenas da conta [contaEmail]) que acontecem em um dia específico.
     *
     * Usa a tabela Instances (e não Events) porque ela já expande eventos recorrentes.
     * Eventos de "dia inteiro" são gravados em UTC, então são comparados por data em UTC;
     * os demais, pelo fuso do aparelho.
     *
     * É uma chamada bloqueante: execute fora da thread principal (Dispatchers.IO).
     */
    fun buscarEventosDoDia(context: Context, data: LocalDate, contaEmail: String?): List<EventoGoogleAgenda> {
        if (!temPermissaoLeitura(context)) return emptyList()

        // Só lê os calendários da conta Google conectada (sem feriados, aniversários ou outras contas).
        val calendarios = calendariosDaConta(context, contaEmail)
        if (calendarios.isEmpty()) return emptyList()
        val filtroCalendarios = "${CalendarContract.Instances.CALENDAR_ID} IN (${calendarios.joinToString(",") { it.id.toString() }})"

        val zona = ZoneId.systemDefault()
        val inicioLocal = data.atStartOfDay(zona).toInstant().toEpochMilli()
        val fimLocal = data.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli()
        val inicioUtc = data.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val fimUtc = data.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        // Janela larga o bastante para pegar tanto eventos com horário quanto de dia inteiro;
        // o filtro exato é feito abaixo.
        val inicioBusca = minOf(inicioLocal, inicioUtc)
        val fimBusca = maxOf(fimLocal, fimUtc)

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, inicioBusca)
            ContentUris.appendId(it, fimBusca)
        }.build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.STATUS
        )

        val eventos = mutableListOf<EventoGoogleAgenda>()

        try {
            context.contentResolver.query(
                uri,
                projection,
                filtroCalendarios,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                val tituloCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val descCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.DESCRIPTION)
                val beginCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val allDayCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                val statusCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.STATUS)

                while (cursor.moveToNext()) {
                    if (cursor.getInt(statusCol) == CalendarContract.Events.STATUS_CANCELED) continue

                    val begin = cursor.getLong(beginCol)
                    val end = cursor.getLong(endCol)
                    val diaInteiro = cursor.getInt(allDayCol) == 1

                    val dataInicioReal = Instant.ofEpochMilli(begin)
                        .atZone(if (diaInteiro) ZoneOffset.UTC else zona)
                        .toLocalDate()

                    val ocorreNoDia = if (diaInteiro) {
                        val dataFimBruta = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate()
                        // O fim é exclusivo; garante ao menos 1 dia de duração.
                        val dataFim = if (dataFimBruta.isAfter(dataInicioReal)) dataFimBruta else dataInicioReal.plusDays(1)
                        !data.isBefore(dataInicioReal) && data.isBefore(dataFim)
                    } else {
                        begin < fimLocal && (end > inicioLocal || begin >= inicioLocal)
                    }
                    if (!ocorreNoDia) continue

                    eventos.add(
                        EventoGoogleAgenda(
                            id = cursor.getLong(idCol),
                            titulo = cursor.getString(tituloCol)?.takeIf { it.isNotBlank() } ?: "Sem título",
                            descricao = cursor.getString(descCol)?.takeIf { it.isNotBlank() },
                            dataInicio = dataInicioReal,
                            horaInicioMillis = begin,
                            horaFimMillis = end,
                            diaInteiro = diaInteiro
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        val info = buscarInfoEventos(context, eventos.map { it.id }.toSet())

        // Dia inteiro primeiro, depois por horário.
        return eventos
            .map { evento ->
                val i = info[evento.id]
                if (i == null) evento else evento.copy(recorrente = i.recorrente, somenteLeitura = i.somenteLeitura)
            }
            .sortedWith(compareByDescending<EventoGoogleAgenda> { it.diaInteiro }.thenBy { it.horaInicioMillis })
    }

    /**
     * Devolve os dias de um mês que têm pelo menos um evento no Google Agenda
     * (usado para desenhar o pontinho no calendário). Eventos de vários dias marcam todos os dias.
     * É uma chamada bloqueante: execute fora da thread principal (Dispatchers.IO).
     */
    fun buscarDiasComEventos(context: Context, mes: YearMonth, contaEmail: String?): Set<LocalDate> {
        if (!temPermissaoLeitura(context)) return emptySet()

        val calendarios = calendariosDaConta(context, contaEmail)
        if (calendarios.isEmpty()) return emptySet()
        val filtroCalendarios = "${CalendarContract.Instances.CALENDAR_ID} IN (${calendarios.joinToString(",") { it.id.toString() }})"

        val zona = ZoneId.systemDefault()
        val primeiroDia = mes.atDay(1)
        val ultimoDia = mes.atEndOfMonth()

        val inicioBusca = minOf(
            primeiroDia.atStartOfDay(zona).toInstant().toEpochMilli(),
            primeiroDia.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        val fimBusca = maxOf(
            ultimoDia.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli(),
            ultimoDia.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, inicioBusca)
            ContentUris.appendId(it, fimBusca)
        }.build()

        val projection = arrayOf(
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.STATUS
        )

        val dias = mutableSetOf<LocalDate>()

        try {
            context.contentResolver.query(uri, projection, filtroCalendarios, null, null)?.use { cursor ->
                val beginCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val allDayCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                val statusCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.STATUS)

                while (cursor.moveToNext()) {
                    if (cursor.getInt(statusCol) == CalendarContract.Events.STATUS_CANCELED) continue

                    val begin = cursor.getLong(beginCol)
                    val end = cursor.getLong(endCol)
                    val diaInteiro = cursor.getInt(allDayCol) == 1

                    val primeiro: LocalDate
                    val ultimo: LocalDate
                    if (diaInteiro) {
                        primeiro = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
                        val fimExclusivo = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate()
                        ultimo = if (fimExclusivo.isAfter(primeiro)) fimExclusivo.minusDays(1) else primeiro
                    } else {
                        primeiro = Instant.ofEpochMilli(begin).atZone(zona).toLocalDate()
                        ultimo = if (end > begin) {
                            Instant.ofEpochMilli(end - 1).atZone(zona).toLocalDate()
                        } else {
                            primeiro
                        }
                    }

                    var dia = if (primeiro.isBefore(primeiroDia)) primeiroDia else primeiro
                    val limite = if (ultimo.isAfter(ultimoDia)) ultimoDia else ultimo
                    while (!dia.isAfter(limite)) {
                        dias.add(dia)
                        dia = dia.plusDays(1)
                    }
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        return dias
    }

    /** Descobre quais eventos são recorrentes ou estão em calendários sem permissão de escrita. */
    private fun buscarInfoEventos(context: Context, ids: Set<Long>): Map<Long, InfoEvento> {
        if (ids.isEmpty()) return emptyMap()

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.RDATE,
            CalendarContract.Events.ORIGINAL_ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        val selection = "${CalendarContract.Events._ID} IN (${ids.joinToString(",")})"
        val resultado = mutableMapOf<Long, InfoEvento>()

        try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val rruleCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.RRULE)
                val rdateCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.RDATE)
                val origemCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.ORIGINAL_ID)
                val acessoCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)

                while (cursor.moveToNext()) {
                    val recorrente = !cursor.getString(rruleCol).isNullOrBlank() ||
                            !cursor.getString(rdateCol).isNullOrBlank() ||
                            !cursor.isNull(origemCol)
                    val somenteLeitura = cursor.getInt(acessoCol) < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                    resultado[cursor.getLong(idCol)] = InfoEvento(recorrente, somenteLeitura)
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }

        return resultado
    }

    private data class CalendarioConta(val id: Long, val principal: Boolean)

    /**
     * Calendários da conta Google [contaEmail] em que o usuário pode criar eventos.
     *
     * Feriados, aniversários e agendas de terceiros são calendários somente leitura,
     * então o filtro por nível de acesso deixa só o que a própria pessoa adiciona.
     * Se [contaEmail] for nulo (nenhuma conta conectada), devolve lista vazia.
     */
    private fun calendariosDaConta(context: Context, contaEmail: String?): List<CalendarioConta> {
        if (contaEmail.isNullOrBlank()) return emptyList()

        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.OWNER_ACCOUNT
        )
        val selection = "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND " +
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND " +
                "${CalendarContract.Calendars.VISIBLE} = 1"
        val args = arrayOf("com.google", CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

        val calendarios = mutableListOf<CalendarioConta>()
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                args,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                val contaCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
                val donoCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.OWNER_ACCOUNT)

                while (cursor.moveToNext()) {
                    val conta = cursor.getString(contaCol) ?: continue
                    if (!conta.equals(contaEmail, ignoreCase = true)) continue

                    val dono = cursor.getString(donoCol).orEmpty()
                    // Por segurança, ignora calendários de feriados/contatos mesmo que venham com escrita.
                    if (dono.contains("#holiday@") || dono.contains("#contacts@")) continue

                    calendarios.add(CalendarioConta(cursor.getLong(idCol), dono.equals(conta, ignoreCase = true)))
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        return calendarios
    }

    /** Calendário principal da conta conectada (ou outro gravável da mesma conta). */
    private fun obterCalendarioGravavelId(context: Context, contaEmail: String?): Long? {
        val calendarios = calendariosDaConta(context, contaEmail)
        return (calendarios.firstOrNull { it.principal } ?: calendarios.firstOrNull())?.id
    }

    fun adicionarEvento(
        context: Context,
        titulo: String,
        descricao: String?,
        data: LocalDate,
        contaEmail: String?,
        hora: LocalTime = LocalTime.of(9, 0),
        diaInteiro: Boolean = false,
        horaFim: LocalTime? = null
    ): ResultadoCalendario {
        if (!temPermissaoLeitura(context) || !temPermissaoEscrita(context)) {
            return ResultadoCalendario.SEM_PERMISSAO
        }

        val calendarioId = obterCalendarioGravavelId(context, contaEmail) ?: return ResultadoCalendario.SEM_CALENDARIO

        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, titulo)
            put(CalendarContract.Events.DESCRIPTION, descricao ?: "Adicionado pelo Finança Celular")
            put(CalendarContract.Events.CALENDAR_ID, calendarioId)

            if (diaInteiro) {
                // Dia inteiro é gravado em UTC, com fim exclusivo (meia-noite do dia seguinte).
                put(CalendarContract.Events.DTSTART, data.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                put(CalendarContract.Events.DTEND, data.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            } else {
                val zona = ZoneId.systemDefault()
                val inicio = data.atTime(hora).atZone(zona).toInstant().toEpochMilli()
                put(CalendarContract.Events.DTSTART, inicio)
                put(
                    CalendarContract.Events.DTEND,
                    if (horaFim != null) fimEmMillis(data, hora, horaFim, zona) else inicio + MILLIS_HORA
                )
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
        }

        return try {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            if (uri != null) ResultadoCalendario.SUCESSO else ResultadoCalendario.ERRO
        } catch (e: SecurityException) {
            e.printStackTrace()
            ResultadoCalendario.SEM_PERMISSAO
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            ResultadoCalendario.ERRO
        }
    }

    /**
     * Atualiza título, descrição, data e horário de um evento existente.
     * Com [horaFim] o término é o informado; sem ele mantém a duração original
     * (ou 1 hora, se o evento era de dia inteiro e passou a ter horário).
     * É uma chamada bloqueante: execute fora da thread principal (Dispatchers.IO).
     */
    fun atualizarEvento(
        context: Context,
        evento: EventoGoogleAgenda,
        titulo: String,
        descricao: String?,
        data: LocalDate,
        hora: LocalTime,
        diaInteiro: Boolean,
        horaFim: LocalTime? = null
    ): ResultadoCalendario {
        if (!temPermissaoLeitura(context) || !temPermissaoEscrita(context)) {
            return ResultadoCalendario.SEM_PERMISSAO
        }
        if (!evento.editavel) return ResultadoCalendario.ERRO

        val duracaoOriginal = evento.horaFimMillis - evento.horaInicioMillis
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, titulo)
            put(CalendarContract.Events.DESCRIPTION, descricao ?: "")

            if (diaInteiro) {
                // Dia inteiro é gravado em UTC, com fim exclusivo (meia-noite do dia seguinte).
                val dias = if (evento.diaInteiro) maxOf(1L, duracaoOriginal / MILLIS_DIA) else 1L
                put(CalendarContract.Events.DTSTART, data.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                put(CalendarContract.Events.DTEND, data.plusDays(dias).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            } else {
                val zona = ZoneId.systemDefault()
                val duracao = if (!evento.diaInteiro && duracaoOriginal > 0) duracaoOriginal else MILLIS_HORA
                val inicio = data.atTime(hora).atZone(zona).toInstant().toEpochMilli()
                put(CalendarContract.Events.DTSTART, inicio)
                put(
                    CalendarContract.Events.DTEND,
                    if (horaFim != null) fimEmMillis(data, hora, horaFim, zona) else inicio + duracao
                )
                put(CalendarContract.Events.ALL_DAY, 0)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
        }

        return try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, evento.id)
            if (context.contentResolver.update(uri, values, null, null) > 0) {
                ResultadoCalendario.SUCESSO
            } else {
                ResultadoCalendario.ERRO
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            ResultadoCalendario.SEM_PERMISSAO
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            ResultadoCalendario.ERRO
        }
    }

    /**
     * Exclui um evento do Google Agenda (some também do app Google Agenda e dos outros aparelhos).
     * É uma chamada bloqueante: execute fora da thread principal (Dispatchers.IO).
     */
    fun excluirEvento(context: Context, evento: EventoGoogleAgenda): ResultadoCalendario {
        if (!temPermissaoLeitura(context) || !temPermissaoEscrita(context)) {
            return ResultadoCalendario.SEM_PERMISSAO
        }
        if (!evento.editavel) return ResultadoCalendario.ERRO

        return try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, evento.id)
            if (context.contentResolver.delete(uri, null, null) > 0) {
                ResultadoCalendario.SUCESSO
            } else {
                ResultadoCalendario.ERRO
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            ResultadoCalendario.SEM_PERMISSAO
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            ResultadoCalendario.ERRO
        }
    }
}