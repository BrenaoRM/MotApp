package com.example.financacelular.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.Locale

@Entity(tableName = "investimentos")
data class InvestimentoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val nome: String,
    val categoria: String,
    val valorInvestidoCentavos: Long,
    val data: LocalDate = LocalDate.now(),
    val anoMes: String = String.format(Locale.ROOT, "%04d-%02d", data.year, data.monthValue)
) {
    val valorInvestido: Double get() = centavosEmReais(valorInvestidoCentavos)
}
