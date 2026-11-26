package com.example.financacelular.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "metas")
data class Meta(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val valorAlvoCentavos: Long,
    val valorAtualCentavos: Long = 0L
) {
    val valorAlvo: Double get() = centavosEmReais(valorAlvoCentavos)
    val valorAtual: Double get() = centavosEmReais(valorAtualCentavos)
}
