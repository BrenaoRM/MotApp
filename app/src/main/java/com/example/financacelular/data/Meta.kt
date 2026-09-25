package com.example.financacelular.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "metas")
data class Meta(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val valorAlvo: Double,
    val valorAtual: Double = 0.0
)
