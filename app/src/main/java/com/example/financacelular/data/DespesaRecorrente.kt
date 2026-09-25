package com.example.financacelular.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "despesas_recorrentes",
    foreignKeys = [
        ForeignKey(
            entity = Categoria::class,
            parentColumns = ["id"],
            childColumns = ["categoriaId"],
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class DespesaRecorrente(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val valor: Double,
    val categoriaId: Long,
    val diaDoMes: Int,
    val tipo: TipoTransacao = TipoTransacao.DESPESA,
    val dataCriacao: LocalDate = LocalDate.now()
)