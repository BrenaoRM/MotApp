package com.example.financacelular.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.financacelular.ia.llm.EstadoModelo
import com.example.financacelular.ia.llm.GerenciadorDeModelo
import com.example.financacelular.ia.llm.ModoIa
import com.example.financacelular.ui.theme.RoxoHero
import com.example.financacelular.ui.theme.cores

/** A IA avançada só aparece em aparelho arm64 com RAM para algum modo do catálogo (catálogo vazio = sem suporte). */
internal fun iaAvancadaSuportada(context: Context): Boolean = GerenciadorDeModelo.modosSuportados(context).isNotEmpty()

/** Estado em uma linha, para a linha de Configurações. */
internal fun textoDoEstadoIa(estado: EstadoModelo): String = when (estado) {
    EstadoModelo.Desativado -> "Desligada"
    is EstadoModelo.Baixando -> "Baixando… ${(estado.progresso * 100).toInt()}%"
    is EstadoModelo.Pronto -> "${estado.modo.titulo} · pronta"
    is EstadoModelo.Falhou -> "Falhou · toque para tentar de novo"
}

private class ModoNaLista(val modo: ModoIa, val tamanho: String, val tempo: String)

// Tamanho e tempo por pergunta medidos no S20 FE (iguais aos do protótipo aprovado)
private val ModosNaLista = listOf(
    ModoNaLista(ModoIa.RAPIDA, "~400 MB", "~6 s"),
    ModoNaLista(ModoIa.MEDIA, "~1,1 GB", "~14 s"),
    ModoNaLista(ModoIa.INTELIGENTE, "~1,1 GB", "~14 s"),
)

private enum class Vista { PRINCIPAL, TROCAR, DESATIVAR }

private enum class Etiqueta(val texto: String) { EM_BREVE("Em breve"), EM_USO("Em uso"), PRONTA("Pronta") }

/** Pílula "IA avançada" ao lado do título Perguntas: abre a folha; mostra o progresso do download e o estado. */
@Composable
internal fun PilulaIaAvancada(estado: EstadoModelo, onClick: () -> Unit) {
    val pronta = estado is EstadoModelo.Pronto
    val falhou = estado is EstadoModelo.Falhou
    val texto = if (estado is EstadoModelo.Baixando) "Baixando ${(estado.progresso * 100).toInt()}%" else "IA avançada"
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClickLabel = "Abrir IA avançada", onClick = onClick)
            .semantics { contentDescription = "IA avançada, " + textoDoEstadoIa(estado) },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = if (pronta) RoxoHero.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, if (pronta) RoxoHero.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline),
            modifier = Modifier.height(32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = RoxoHero, modifier = Modifier.size(14.dp))
                Text(texto, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                if (pronta || falhou) {
                    Box(
                        Modifier.size(6.dp).background(if (falhou) MaterialTheme.cores.textoGasto else MaterialTheme.cores.textoGanho, CircleShape)
                    )
                }
            }
        }
    }
}

/**
 * Folha "IA avançada": escolher o modo e baixar, acompanhar o download, trocar de modo ou desativar (protótipo aprovado de
 * 2026-11-27). O download roda no WorkManager; o estado vem do [GerenciadorDeModelo].
 */
@Composable
internal fun FolhaIaAvancada(aoFechar: () -> Unit) {
    val context = LocalContext.current
    val mensagem = LocalMensagem.current
    val estado by GerenciadorDeModelo.estado.collectAsStateWithLifecycle()
    val suportados = remember { GerenciadorDeModelo.modosSuportados(context).map { it.modo } }
    var vista by remember { mutableStateOf(Vista.PRINCIPAL) }
    var alvo by remember { mutableStateOf(if (ModoIa.MEDIA in suportados) ModoIa.MEDIA else suportados.firstOrNull() ?: ModoIa.RAPIDA) }
    var baixou by remember { mutableStateOf(estado is EstadoModelo.Baixando) }

    LaunchedEffect(estado) {
        when (estado) {
            is EstadoModelo.Baixando -> {
                baixou = true
                vista = Vista.PRINCIPAL
            }
            is EstadoModelo.Pronto -> if (baixou) {
                baixou = false
                vista = Vista.PRINCIPAL
                mensagem("IA avançada pronta", false)
            }
            else -> baixou = false
        }
    }

    val baixando = estado is EstadoModelo.Baixando
    FolhaApp(onDismiss = aoFechar, bloquearFechamento = baixando) {
        val atual = estado
        when {
            vista == Vista.DESATIVAR && atual is EstadoModelo.Pronto -> ConfirmarAcaoConteudo(
                titulo = "Desativar a IA avançada?",
                texto = "O modelo (${tamanhoDe(atual.modo)}) é apagado do aparelho. Dá para baixar de novo.",
                textoConfirmar = "Desativar",
                onConfirmar = {
                    GerenciadorDeModelo.desativar(context)
                    aoFechar()
                    mensagem("IA avançada desativada", false)
                },
                onCancelar = { vista = Vista.PRINCIPAL },
            )
            vista == Vista.TROCAR && atual is EstadoModelo.Pronto -> {
                CabecalhoDeFolha("Trocar de modo", aoFechar)
                ListaDeModos(suportados, alvo, emUso = atual.modo) { alvo = it }
                AvisoDeWifi()
                Spacer(Modifier.height(20.dp))
                BotaoPrincipal(
                    if (alvo == atual.modo) "Em uso" else "Baixar",
                    { GerenciadorDeModelo.ativar(context, alvo) },
                    Modifier.fillMaxWidth(),
                    enabled = alvo != atual.modo,
                )
                Spacer(Modifier.height(8.dp))
                BotaoSecundario("Voltar", { vista = Vista.PRINCIPAL }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
            }
            else -> when (atual) {
                EstadoModelo.Desativado -> {
                    CabecalhoDeFolha("IA avançada", aoFechar, subtitulo = "Roda no seu celular")
                    ListaDeModos(suportados, alvo, emUso = null) { alvo = it }
                    AvisoDeWifi()
                    Spacer(Modifier.height(20.dp))
                    BotaoPrincipal("Baixar", { GerenciadorDeModelo.ativar(context, alvo) }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                }
                is EstadoModelo.Baixando -> {
                    CabecalhoDeFolha("IA avançada", aoFechar, subtitulo = "Roda no seu celular", fecharAtivo = false)
                    LinhaDeModo(ModosNaLista.first { it.modo == atual.modo }, selecionado = true, etiqueta = null, radio = false)
                    val pct = (atual.progresso * 100).toInt()
                    Spacer(Modifier.height(18.dp))
                    BarraDeProgresso(atual.progresso, "$pct%")
                    Text(
                        if (atual.progresso >= 1f) "Instalando…" else "Baixando… $pct%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    BotaoSecundario("Cancelar", { GerenciadorDeModelo.cancelar(context) }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                }
                is EstadoModelo.Falhou -> {
                    CabecalhoDeFolha("IA avançada", aoFechar, subtitulo = "Roda no seu celular")
                    LinhaDeModo(ModosNaLista.first { it.modo == atual.modo }, selecionado = true, etiqueta = null, radio = false)
                    Text(
                        "O download falhou. Confira a conexão.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.cores.textoGasto,
                        modifier = Modifier.padding(start = 2.dp, top = 16.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    BotaoPrincipal("Tentar de novo", { GerenciadorDeModelo.ativar(context, atual.modo) }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    BotaoSecundario("Cancelar", { GerenciadorDeModelo.cancelar(context) }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                }
                is EstadoModelo.Pronto -> {
                    CabecalhoDeFolha("IA avançada", aoFechar, subtitulo = "Roda no seu celular")
                    LinhaDeModo(ModosNaLista.first { it.modo == atual.modo }, selecionado = true, etiqueta = Etiqueta.PRONTA, radio = false)
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BotaoSecundario("Trocar de modo", { alvo = atual.modo; vista = Vista.TROCAR }, Modifier.weight(1f))
                        BotaoSecundario("Desativar", { vista = Vista.DESATIVAR }, Modifier.weight(1f), cor = MaterialTheme.cores.textoGasto)
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

private fun tamanhoDe(modo: ModoIa) = ModosNaLista.first { it.modo == modo }.tamanho

@Composable
private fun ListaDeModos(suportados: List<ModoIa>, selecionado: ModoIa, emUso: ModoIa?, aoEscolher: (ModoIa) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 14.dp)) {
        ModosNaLista.filter { it.modo in suportados || it.modo == ModoIa.INTELIGENTE }.forEach { m ->
            val embreve = m.modo == ModoIa.INTELIGENTE // mesmo modelo da Média por enquanto: prometer mais seria enganoso
            LinhaDeModo(
                m,
                selecionado = !embreve && selecionado == m.modo,
                etiqueta = when {
                    embreve -> Etiqueta.EM_BREVE
                    m.modo == emUso -> Etiqueta.EM_USO
                    else -> null
                },
                radio = !embreve,
                habilitado = !embreve,
                onClick = { aoEscolher(m.modo) },
            )
        }
    }
}

@Composable
private fun LinhaDeModo(
    m: ModoNaLista,
    selecionado: Boolean,
    etiqueta: Etiqueta?,
    radio: Boolean,
    habilitado: Boolean = false,
    onClick: () -> Unit = {},
) {
    val forma = RoundedCornerShape(16.dp)
    val opacidade = if (!habilitado && etiqueta == Etiqueta.EM_BREVE) 0.55f else 1f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(forma)
            .background(if (selecionado) RoxoHero.copy(alpha = 0.10f) else Color.Transparent)
            .then(
                if (habilitado) Modifier.clickable(role = Role.RadioButton, onClick = onClick) else Modifier
            )
            .border(1.dp, if (selecionado) RoxoHero else MaterialTheme.colorScheme.outline, forma)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                m.modo.titulo,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = opacidade)
            )
            Text(
                "${m.tamanho} · ${m.tempo} por pergunta",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = opacidade)
            )
        }
        if (etiqueta != null) {
            val corTexto = when (etiqueta) {
                Etiqueta.PRONTA -> MaterialTheme.cores.textoGanho
                Etiqueta.EM_USO -> MaterialTheme.colorScheme.onSurface
                Etiqueta.EM_BREVE -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val fundo = when (etiqueta) {
                Etiqueta.PRONTA -> MaterialTheme.cores.textoGanho.copy(alpha = 0.14f)
                Etiqueta.EM_USO -> RoxoHero.copy(alpha = 0.18f)
                Etiqueta.EM_BREVE -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            }
            Text(
                etiqueta.texto,
                color = corTexto,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.background(fundo, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        if (radio) {
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(if (selecionado) RoxoHero else Color.Transparent, CircleShape)
                    .border(1.5.dp, if (selecionado) RoxoHero else MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selecionado) Icon(Icons.Outlined.Check, contentDescription = null, tint = CorSobreVioleta, modifier = Modifier.size(12.dp))
            }
        }
    }
}

private val CorSobreVioleta = Color.White

@Composable
private fun AvisoDeWifi() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 2.dp, top = 14.dp)
    ) {
        Icon(Icons.Outlined.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(
            "Baixe no Wi-Fi: o arquivo é grande.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BarraDeProgresso(progresso: Float, descricao: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(3.dp))
            .semantics { contentDescription = "Download $descricao" }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progresso.coerceIn(0f, 1f))
                .height(6.dp)
                .background(RoxoHero, RoundedCornerShape(3.dp))
        )
    }
}
