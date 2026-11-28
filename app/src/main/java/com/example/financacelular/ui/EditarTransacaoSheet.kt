package com.example.financacelular.ui

import com.example.financacelular.data.reaisEmCentavos
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.financacelular.data.CartaoEntity
import com.example.financacelular.data.Categoria
import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.anoMesDaCompraNoCartao
import com.example.financacelular.data.ehPagamentoDeFatura
import com.example.financacelular.data.FORMATO_DATA
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Espaco
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.Verde
import com.example.financacelular.ui.theme.cores
import java.util.Locale

// =====================================================================
// Componentes de formulário / filtros
// =====================================================================


/** Campo somente leitura que abre um seletor de data ao toque. */
@Composable
private fun CampoData(
    valor: String,
    rotulo: String,
    descricao: String,
    aoAbrir: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null
) {
    Box(modifier = modifier) {
        CampoApp(
            valor = valor.ifEmpty { placeholder.orEmpty() },
            aoMudar = {},
            rotulo = rotulo,
            leitura = true,
            trailingIcon = {
                Icon(
                    Icons.Outlined.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Tamanho.icone)
                )
            }
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(role = Role.Button, onClickLabel = descricao, onClick = aoAbrir)
        )
    }
}

// =====================================================================
// Edição de lançamento
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BottomSheetEditarTransacao(
    transacao: Transacao,
    categorias: List<Categoria>,
    onDismiss: () -> Unit,
    cartoes: List<CartaoEntity> = emptyList(),
    /** Ainda não gravado: "Cancelar" no lugar de "Excluir", sem confirmação. */
    nova: Boolean = false,
    onSalvar: (Transacao) -> Unit,
    onExcluir: () -> Unit
) {
    var valor by rememberSaveable { mutableStateOf(transacao.valor.toString()) }
    var descricao by rememberSaveable { mutableStateOf(transacao.descricao ?: "") }
    var dataSelecionada by rememberSaveable { mutableStateOf(transacao.data) }
    var categoriaSelecionadaId by rememberSaveable { mutableLongStateOf(transacao.categoriaId) }
    val categoriaSelecionada = categorias.find { it.id == categoriaSelecionadaId }
    var menuExpandido by remember { mutableStateOf(false) }
    var mostrarDatePicker by rememberSaveable { mutableStateOf(false) }
    // Confirmação de exclusão troca o conteúdo deste mesmo sheet (um sheet por vez)
    var confirmando by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val categoriasDoTipo = categorias.filter { it.tipo == transacao.tipo }
    val corTipo = if (transacao.tipo == TipoTransacao.DESPESA) Coral else Verde

    FolhaApp(onDismiss = onDismiss) {
        AnimatedContent(
            targetState = confirmando,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) using SizeTransform() },
            label = "confirmarExclusaoLancamento"
        ) { emConfirmacao ->
          if (emConfirmacao) {
            ConfirmarAcaoConteudo(
                titulo = "Excluir lançamento?",
                texto = "Esse lançamento será removido do extrato. Essa ação não pode ser desfeita.",
                textoConfirmar = "Excluir",
                onConfirmar = onExcluir,
                onCancelar = { confirmando = false }
            )
          } else {
          TrocaPorData(
              escolhendo = mostrarDatePicker,
              data = dataSelecionada,
              aoEscolher = { dataSelecionada = it },
              aoVoltar = { mostrarDatePicker = false }
          ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            CabecalhoDeFolha(
                titulo = if (nova) "Novo lançamento" else "Editar lançamento",
                aoFechar = onDismiss,
                subtitulo = if (transacao.tipo == TipoTransacao.DESPESA) "Gasto" else "Ganho"
            )
            Spacer(modifier = Modifier.height(Espaco.md))

            CampoValor(valor = valor, aoMudar = { valor = it })
            Spacer(modifier = Modifier.height(Espaco.sm))

            ExposedDropdownMenuBox(
                expanded = menuExpandido,
                onExpandedChange = { menuExpandido = it }
            ) {
                CampoApp(
                    valor = categoriaSelecionada?.nome ?: "",
                    aoMudar = {},
                    rotulo = "Categoria",
                    leitura = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpandido) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = menuExpandido,
                    onDismissRequest = { menuExpandido = false }
                ) {
                    categoriasDoTipo.forEach { categoria ->
                        DropdownMenuItem(
                            text = { Text(categoria.nome) },
                            onClick = {
                                categoriaSelecionadaId = categoria.id
                                menuExpandido = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(Espaco.sm))

            CampoApp(valor = descricao, aoMudar = { descricao = it }, rotulo = "Descrição")
            Spacer(modifier = Modifier.height(Espaco.sm))

            CampoData(
                valor = dataSelecionada.format(FORMATO_DATA),
                rotulo = "Data",
                descricao = "Selecionar data",
                aoAbrir = { mostrarDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(Espaco.lg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BotaoSecundario(
                    texto = if (nova) "Cancelar" else "Excluir",
                    onClick = {
                        focusManager.clearFocus()
                        if (nova) onDismiss() else confirmando = true
                    },
                    modifier = Modifier.weight(1f),
                    cor = if (nova) MaterialTheme.colorScheme.onSurface else MaterialTheme.cores.textoGasto,
                )
                BotaoPrincipal(
                    texto = "Salvar",
                    onClick = {
                        val valorDouble = valor.replace(",", ".").toDoubleOrNull()
                        val categoria = categoriaSelecionada
                        if (valorDouble != null && categoria != null) {
                            // anoMes de compra no cartão é o mês da fatura; sem mudar a data, mantém o original
                            val cartao = cartoes.find { it.id == transacao.cartaoId }
                            val novoAnoMes = when {
                                dataSelecionada == transacao.data || transacao.ehPagamentoDeFatura() -> transacao.anoMes
                                cartao != null && transacao.formaPagamento == FormaPagamento.CARTAO_CREDITO ->
                                    anoMesDaCompraNoCartao(dataSelecionada, cartao.diaFechamento)
                                else -> String.format(Locale.ROOT, "%04d-%02d", dataSelecionada.year, dataSelecionada.monthValue)
                            }
                            onSalvar(
                                transacao.copy(
                                    valorCentavos = reaisEmCentavos(valorDouble),
                                    categoriaId = categoria.id,
                                    descricao = descricao.ifBlank { null },
                                    data = dataSelecionada,
                                    anoMes = novoAnoMes
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(Espaco.lg))
          }
          }
          }
        }
    }
}