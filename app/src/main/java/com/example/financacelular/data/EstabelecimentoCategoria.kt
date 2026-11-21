package com.example.financacelular.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.text.Normalizer

/**
 * Categoria que a pessoa usou da última vez para um estabelecimento detectado pela notificação do banco.
 * Fica escondida: a descrição do lançamento pode ser renomeada à vontade que a sugestão continua valendo.
 */
@Entity(tableName = "estabelecimento_categoria", primaryKeys = ["chave", "tipo"])
data class EstabelecimentoCategoria(
    val chave: String,
    val tipo: TipoTransacao,
    val categoriaId: Long
)

/** Nome do estabelecimento sem acento, caixa e espaços repetidos, para "MERCADO  Zé" casar com "mercado ze". */
fun chaveDoEstabelecimento(nome: String): String =
    Normalizer.normalize(nome, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .trim()

@Dao
interface EstabelecimentoCategoriaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun definir(item: EstabelecimentoCategoria)

    @Query("SELECT categoriaId FROM estabelecimento_categoria WHERE chave = :chave AND tipo = :tipo")
    suspend fun buscarCategoria(chave: String, tipo: TipoTransacao): Long?

    @Query("DELETE FROM estabelecimento_categoria WHERE categoriaId = :categoriaId")
    suspend fun excluirPorCategoria(categoriaId: Long)

    @Query("UPDATE estabelecimento_categoria SET categoriaId = :para WHERE categoriaId = :de")
    suspend fun moverPorCategoria(de: Long, para: Long)
}
