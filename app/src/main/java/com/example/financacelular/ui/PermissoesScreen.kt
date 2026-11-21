package com.example.financacelular.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.financacelular.ui.theme.CorConfiguracoes
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.cores
import com.example.financacelular.ui.theme.dimens

@Composable
fun PermissoesScreen(aoVoltar: () -> Unit) {
    val context = LocalContext.current
    val estados = rememberEstadosPermissoes()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        estados.recalcular()
    }

    fun tocar(permissao: Permissao, estado: EstadoPermissao) {
        when {
            estado == EstadoPermissao.PENDENTE && permissao.permissoesRuntime.isNotEmpty() -> {
                permissao.marcarPedida(context)
                launcher.launch(permissao.permissoesRuntime.toTypedArray())
            }
            estado == EstadoPermissao.PENDENTE -> permissao.pedirNaTelaDoSistema(context)
            else -> permissao.abrirConfiguracoes(context)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = MaterialTheme.dimens.paddingScreen),
        contentPadding = PaddingValues(top = 16.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp)
    ) {
        item {
            CabecalhoPadrao("Permissões", aoVoltar = aoVoltar)
            Spacer(modifier = Modifier.height(16.dp))
            Spacer(modifier = Modifier.height(24.dp))
        }
        GrupoPermissao.entries.forEach { grupo ->
            val doGrupo = Permissao.entries.filter { it.grupo == grupo }
            item {
                Text(grupo.titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                CartaoUnicoDeLinhas {
                    doGrupo.forEachIndexed { indice, permissao ->
                        if (indice > 0) DivisorDeLinha()
                        val estado = estados[permissao]
                        val liberada = estado == EstadoPermissao.LIBERADA
                        LinhaDePagina(
                            inicio = { IconeDeLinha(permissao.icone) },
                            titulo = permissao.titulo,
                            valor = "",
                            subtitulo = when (estado) {
                                EstadoPermissao.LIBERADA -> "Liberada"
                                EstadoPermissao.PENDENTE -> permissao.paraQue
                                EstadoPermissao.BLOQUEADA -> "Bloqueada · abrir configurações"
                            },
                            corSubtitulo = if (estado == EstadoPermissao.BLOQUEADA) MaterialTheme.cores.textoGasto else Color.Unspecified,
                            fim = {
                                Icon(
                                    if (liberada) Icons.Outlined.Check else Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    tint = if (liberada) CorConfiguracoes else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(Tamanho.iconeGrande)
                                )
                            },
                            onClick = { tocar(permissao, estado) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
