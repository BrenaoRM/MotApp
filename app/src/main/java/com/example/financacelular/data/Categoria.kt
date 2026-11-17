package com.example.financacelular.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categorias")
data class Categoria(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val tipo: TipoTransacao,
    // Chave do ícone escolhido pelo usuário (ver IconesDeCategoria); null = ícone sugerido pelo nome
    val icone: String? = null
)
