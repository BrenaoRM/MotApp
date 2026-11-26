package com.example.financacelular.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "orcamentos",
    foreignKeys = [
        ForeignKey(
            entity = Categoria::class,
            parentColumns = ["id"],
            childColumns = ["categoriaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("categoriaId")]
)
data class Orcamento(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoriaId: Long,
    val anoMes: String, // formato "2026-09"
    val valorLimiteCentavos: Long
) {
    val valorLimite: Double get() = centavosEmReais(valorLimiteCentavos)
}
