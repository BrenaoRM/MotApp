package com.example.financacelular.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "afazeres")
data class AfazerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val data: LocalDate,
    val titulo: String,
    val concluido: Boolean = false
)