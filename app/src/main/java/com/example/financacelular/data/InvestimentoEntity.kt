package com.example.financacelular.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "investimentos")
data class InvestimentoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val nome: String,
    val categoria: String,
    val valorInvestido: Double,
    val data: LocalDate = LocalDate.now(),
    val anoMes: String = String.format("%04d-%02d", data.year, data.monthValue)
)