package com.example.financacelular.data

import androidx.room.TypeConverter
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter
    fun fromTipo(tipo: TipoTransacao?): String? = tipo?.name

    @TypeConverter
    fun toTipo(value: String?): TipoTransacao? = value?.let { TipoTransacao.valueOf(it) }

    @TypeConverter
    fun fromFormaPagamento(forma: FormaPagamento?): String? = forma?.name

    @TypeConverter
    fun toFormaPagamento(value: String?): FormaPagamento? = value?.let { FormaPagamento.valueOf(it) }
}
