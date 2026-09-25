package com.example.financacelular.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.Categoria
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Verde

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriasScreen(viewModel: CategoriasViewModel = viewModel()) {
    val categorias by viewModel.categorias.collectAsState()
    var categoriaEmEdicao by remember { mutableStateOf<Categoria?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text("Categorias", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = viewModel.novoNome,
            onValueChange = viewModel::onNomeChange,
            label = { Text("Nome da categoria") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = viewModel.novoTipo == TipoTransacao.DESPESA,
                onClick = { viewModel.onTipoChange(TipoTransacao.DESPESA) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f),
                    activeContentColor = MaterialTheme.colorScheme.tertiary
                )
            ) { Text("Despesa") }
            SegmentedButton(
                selected = viewModel.novoTipo == TipoTransacao.RECEITA,
                onClick = { viewModel.onTipoChange(TipoTransacao.RECEITA) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    activeContentColor = MaterialTheme.colorScheme.primary
                )
            ) { Text("Receita") }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = viewModel::adicionar,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Adicionar categoria")
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = espacoParaBarraFlutuante())
        ) {
            items(categorias) { categoria ->
                val cor = if (categoria.tipo == TipoTransacao.DESPESA) Coral else Verde
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { categoriaEmEdicao = categoria }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(cor.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(iconeParaCategoria(categoria.nome), contentDescription = null, tint = cor)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "${categoria.nome} (${if (categoria.tipo == TipoTransacao.DESPESA) "Despesa" else "Receita"})",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    IconButton(onClick = { viewModel.excluir(categoria) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Excluir")
                    }
                }
            }
        }
    }

    categoriaEmEdicao?.let { categoria ->
        DialogoEditarCategoria(
            categoria = categoria,
            onDismiss = { categoriaEmEdicao = null },
            onConfirmar = { novoNome ->
                viewModel.atualizar(categoria.copy(nome = novoNome))
                categoriaEmEdicao = null
            }
        )
    }
}

@Composable
private fun DialogoEditarCategoria(
    categoria: Categoria,
    onDismiss: () -> Unit,
    onConfirmar: (String) -> Unit
) {
    var nome by remember { mutableStateOf(categoria.nome) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar categoria") },
        text = {
            OutlinedTextField(
                value = nome,
                onValueChange = { nome = it },
                label = { Text("Nome") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (nome.isNotBlank()) onConfirmar(nome) }) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
