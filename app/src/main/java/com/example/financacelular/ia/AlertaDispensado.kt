package com.example.financacelular.ia

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Alerta de lançamento atípico que a pessoa dispensou; [dispensadoEm] em epoch millis. */
@Entity(tableName = "ia_alerta_dispensado")
data class AlertaDispensado(
    @PrimaryKey val transacaoId: Long,
    val dispensadoEm: Long
)

@Dao
interface AlertaDispensadoDao {
    @Query("SELECT transacaoId FROM ia_alerta_dispensado")
    fun listarIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun dispensar(a: AlertaDispensado)
}
