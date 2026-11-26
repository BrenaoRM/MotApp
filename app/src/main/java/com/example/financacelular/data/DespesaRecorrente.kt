package com.example.financacelular.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
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
    ],
    indices = [Index("categoriaId")]
)
data class DespesaRecorrente(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val valorCentavos: Long,
    val categoriaId: Long,
    val diaDoMes: Int,
    val tipo: TipoTransacao = TipoTransacao.DESPESA,
    val dataCriacao: LocalDate = LocalDate.now(),
    // Cartão em que a assinatura é cobrada (null para receitas recorrentes)
    val cartaoId: Long? = null,
    // Como a cobrança é paga. Receita recorrente é sempre débito; as antigas ficam como crédito.
    @ColumnInfo(defaultValue = "'CARTAO_CREDITO'")
    val formaPagamento: FormaPagamento = FormaPagamento.CARTAO_CREDITO
) {
    val valor: Double get() = centavosEmReais(valorCentavos)

    /** Entra direto no saldo (receita ou despesa no débito), sem cartão e sem fatura. */
    val semCartao: Boolean
        get() = tipo == TipoTransacao.RECEITA || formaPagamento == FormaPagamento.DEBITO
}