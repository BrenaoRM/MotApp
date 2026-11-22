package com.example.financacelular.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.dimens

/** Tela Categorias (Configurações › Categorias): lista por tipo; tocar abre a folha de editar nome e ícone. */
@Composable
fun CategoriasScreen(
    viewModel: CategoriasViewModel = viewModel(),
    acionarNovaExterna: Boolean = false,
    aoNovaAcionada: () -> Unit = {},
    aoVoltar: (() -> Unit)? = null
) {
    val mensagem = LocalMensagem.current
    val itensCarregados by viewModel.itens.collectAsStateWithLifecycle()
    val itens = itensCarregados.orEmpty()

    var tipo by rememberSaveable { mutableStateOf(TipoTransacao.DESPESA) }
    var editandoId by rememberSaveable { mutableStateOf<Long?>(null) }
    var criando by rememberSaveable { mutableStateOf(false) }

    val itensDoTipo = itens.filter { it.categoria.tipo == tipo }
    val itemEditando = editandoId?.let { id -> itens.find { it.categoria.id == id } }

    // Botão "Nova Categoria" da ilha de baixo
    LaunchedEffect(acionarNovaExterna) {
        if (acionarNovaExterna) {
            criando = true
            aoNovaAcionada()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = MaterialTheme.dimens.paddingScreen),
        contentPadding = PaddingValues(top = 16.dp, bottom = espacoParaBarraFlutuante())
    ) {
        item {
            CabecalhoPadrao("Categorias", aoVoltar = aoVoltar)
            Spacer(modifier = Modifier.height(16.dp))
            SeletorQuadrado(
                opcoes = listOf("Gastos", "Ganhos"),
                indiceSelecionado = if (tipo == TipoTransacao.DESPESA) 0 else 1,
                aoSelecionar = { tipo = if (it == 0) TipoTransacao.DESPESA else TipoTransacao.RECEITA }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (itensCarregados != null && itensDoTipo.isEmpty()) {
            item {
                EstadoVazio(
                    icone = Icons.Outlined.Category,
                    mensagem = if (tipo == TipoTransacao.DESPESA) "Nenhuma categoria de gasto ainda." else "Nenhuma categoria de ganho ainda.",
                    textoAcao = "Nova categoria",
                    aoAcao = { criando = true }
                )
            }
        } else if (itensDoTipo.isNotEmpty()) {
            item {
                CartaoUnicoDeLinhas {
                    itensDoTipo.forEachIndexed { indice, item ->
                        if (indice > 0) DivisorDeLinha()
                        LinhaDePagina(
                            inicio = {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f), RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        iconeParaCategoria(item.categoria.nome, item.categoria.icone),
                                        contentDescription = null,
                                        modifier = Modifier.size(Tamanho.icone)
                                    )
                                }
                            },
                            titulo = item.categoria.nome,
                            valor = "",
                            subtitulo = when {
                                item.lancamentos == 0 -> "Sem lançamentos"
                                item.lancamentos == 1 -> "1 lançamento"
                                else -> "${item.lancamentos} lançamentos"
                            },
                            fim = if (item.sistema) {
                                {
                                    Icon(
                                        Icons.Outlined.Lock,
                                        contentDescription = "Categoria do sistema",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(Tamanho.iconePequeno)
                                    )
                                }
                            } else null,
                            onClick = { editandoId = item.categoria.id }
                        )
                    }
                }
            }
        }
    }

    // ---------------- SHEETS: EDITAR / CRIAR ----------------
    itemEditando?.let { item ->
        FolhaEditarCategoria(
            item = item,
            tipoNova = item.categoria.tipo,
            nomesDoTipo = itens.filter { it.categoria.tipo == item.categoria.tipo }.map { it.categoria.nome },
            aoSalvar = { nome, icone ->
                viewModel.salvar(item.categoria.id, nome, item.categoria.tipo, icone)
                editandoId = null
                mensagem("Categoria salva.")
            },
            aoExcluir = {
                viewModel.excluir(item.categoria)
                editandoId = null
                mensagem("Categoria excluída.")
            },
            aoFechar = { editandoId = null },
            destinos = itens.map { it.categoria }.filter {
                it.tipo == item.categoria.tipo && it.id != item.categoria.id && !it.nome.equals("Fatura", ignoreCase = true)
            },
            aoMoverEExcluir = { destino ->
                viewModel.moverEExcluir(item.categoria, destino)
                editandoId = null
                mensagem("Lançamentos movidos para ${destino.nome}.")
            }
        )
    }

    if (criando) {
        FolhaEditarCategoria(
            item = null,
            tipoNova = tipo,
            nomesDoTipo = itensDoTipo.map { it.categoria.nome },
            aoSalvar = { nome, icone ->
                viewModel.salvar(null, nome, tipo, icone)
                criando = false
                mensagem("Categoria criada.")
            },
            aoExcluir = {},
            aoFechar = { criando = false }
        )
    }
}
