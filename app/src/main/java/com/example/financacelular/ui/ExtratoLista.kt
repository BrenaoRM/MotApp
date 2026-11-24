package com.example.financacelular.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.financacelular.data.CartaoEntity
import com.example.financacelular.data.Categoria
import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.Verde
import com.example.financacelular.ui.theme.cores
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val formatoRotuloDia: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd 'de' MMM", LocalePtBr)
private val formatoRotuloDiaAno: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd 'de' MMM 'de' yyyy", LocalePtBr)

/** "Hoje", "Ontem", "Amanhã" ou "Seg, 29 de set". */
private fun rotuloDia(data: LocalDate): String {
    val hoje = LocalDate.now()
    return when (data) {
        hoje -> "Hoje"
        hoje.minusDays(1) -> "Ontem"
        hoje.plusDays(1) -> "Amanhã"
        else -> data
            .format(if (data.year == hoje.year) formatoRotuloDia else formatoRotuloDiaAno)
            .replace(".", "")
            .replaceFirstChar { it.uppercase() }
    }
}

@Composable
internal fun GrupoDia(
    data: LocalDate,
    transacoes: List<Transacao>,
    categorias: List<Categoria>,
    cartoes: List<CartaoEntity>,
    formato: NumberFormat,
    pendente: Boolean,
    aoClicar: (Transacao) -> Unit
) {
    val saldoDia = transacoes.sumOf { if (it.tipo == TipoTransacao.DESPESA) -it.valor else it.valor }.arredondadoACentavos()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                rotuloDia(data),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                (if (saldoDia < 0) "- " else "+ ") + formato.format(abs(saldoDia)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        GrupoDeLista(itens = transacoes) { transacao ->
            LinhaTransacao(
                transacao = transacao,
                categoria = categorias.find { it.id == transacao.categoriaId },
                nomeCartao = transacao.cartaoId?.let { id -> cartoes.find { it.id == id }?.nome },
                formato = formato,
                pendente = pendente,
                aoClicar = { aoClicar(transacao) }
            )
        }
    }
}

@Composable
private fun LinhaTransacao(
    transacao: Transacao,
    categoria: Categoria?,
    nomeCartao: String?,
    formato: NumberFormat,
    pendente: Boolean,
    aoClicar: () -> Unit
) {
    val cor = if (transacao.tipo == TipoTransacao.DESPESA) Coral else Verde
    val corTexto = if (transacao.tipo == TipoTransacao.DESPESA) MaterialTheme.cores.textoGasto else MaterialTheme.cores.textoGanho
    val intensidade = if (pendente) 0.7f else 1f
    val anoMes = transacao.anoMes
    val temDescricao = !transacao.descricao.isNullOrBlank()
    // Sem descrição o título já é o nome da categoria: não repetir no subtítulo
    val mostrarCategoria = temDescricao || categoria == null
    val temFatura = transacao.formaPagamento == FormaPagamento.CARTAO_CREDITO && anoMes != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = transacao.id >= 0, role = Role.Button, onClick = aoClicar)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(cor.copy(alpha = if (pendente) 0.10f else 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                iconeParaCategoria(categoria?.nome ?: "", categoria?.icone),
                contentDescription = null,
                tint = cor.copy(alpha = intensidade),
                modifier = Modifier.size(Tamanho.icone)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                transacao.descricao?.takeIf { it.isNotBlank() }
                    ?: (categoria?.nome ?: if (pendente) "Transação futura" else "Transação"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (mostrarCategoria || temFatura) Spacer(modifier = Modifier.height(3.dp))
            if (mostrarCategoria || temFatura) Row(verticalAlignment = Alignment.CenterVertically) {
                if (mostrarCategoria) {
                    Text(
                        categoria?.nome ?: "Sem categoria",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (temFatura) {
                    val partes = anoMes.split("-")
                    val mesFatura = partes.getOrNull(1) ?: ""
                    val anoFatura = partes.getOrNull(0)?.takeLast(2) ?: ""
                    if (mostrarCategoria) Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (pendente) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.primaryContainer
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Fatura $mesFatura/$anoFatura",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (pendente) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
            if (nomeCartao != null) {
                Spacer(modifier = Modifier.height(4.dp))
                EtiquetaCartao(nome = nomeCartao)
            }
        }

        Spacer(modifier = Modifier.width(10.dp))
        Text(
            (if (transacao.tipo == TipoTransacao.DESPESA) "- " else "+ ") +
                    formato.format(transacao.valor),
            color = corTexto.copy(alpha = intensidade),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
