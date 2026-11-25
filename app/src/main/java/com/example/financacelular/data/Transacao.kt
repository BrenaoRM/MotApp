package com.example.financacelular.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class TipoTransacao { RECEITA, DESPESA }
enum class FormaPagamento { DINHEIRO, DEBITO, CARTAO_CREDITO }

@Entity(
    tableName = "transacoes",
    foreignKeys = [
        ForeignKey(
            entity = Categoria::class,
            parentColumns = ["id"],
            childColumns = ["categoriaId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("anoMes"),
        Index("data"),
        Index("categoriaId"),
        Index("cartaoId"),
        Index("recorrenteId")
    ]
)
data class Transacao(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val valor: Double,
    val data: LocalDate,
    val categoriaId: Long,
    val tipo: TipoTransacao,
    val descricao: String? = null,
    val formaPagamento: FormaPagamento = FormaPagamento.DEBITO,
    val cartaoId: Long? = null,
    val anoMes: String? = null,
    val numeroParcela: Int = 1,
    val totalParcelas: Int = 1,
    val grupoParcelamentoId: String? = null,
    // ID da assinatura/recorrente que gerou esta transação (null se foi lançamento avulso)
    val recorrenteId: Long? = null,
    // Dia em que a compra parcelada foi feita (cada parcela fica na data do vencimento); null nas antigas e nas avulsas
    val dataCompra: LocalDate? = null
)