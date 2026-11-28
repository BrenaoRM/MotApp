package com.example.financacelular.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.financacelular.data.Categoria
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import com.example.financacelular.ia.Mensagem
import com.example.financacelular.ia.llm.GerenciadorDeModelo
import com.example.financacelular.ui.theme.RoxoHero
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.cores
import com.example.financacelular.ui.theme.dimens
import java.text.NumberFormat
import java.time.LocalDate

/** Gasto do chat aberto na folha de edição; [indice] é a posição da mensagem na conversa. */
private class Rascunho(val indice: Int, val transacao: Transacao)

/** Conversa em tela inteira, sem a barra de navegação: só sai pela seta ou pelo voltar do sistema. */
@Composable
fun ConversaScreen(viewModel: AssistenteViewModel, aoVoltar: () -> Unit) {
    // Coletar o estado mantém os dados que o chat lê atualizados enquanto a tela está aberta
    viewModel.estado.collectAsStateWithLifecycle()
    val conversa by viewModel.conversa.collectAsStateWithLifecycle()
    val pensando by viewModel.pensando.collectAsStateWithLifecycle()
    val categorias by viewModel.categorias.collectAsStateWithLifecycle()
    val cartoes by viewModel.cartoes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val iaSuportada = remember { iaAvancadaSuportada(context) }
    val estadoIa by GerenciadorDeModelo.estado.collectAsStateWithLifecycle()
    val formato = ValoresVisiveis.formato(remember { NumberFormat.getCurrencyInstance(LocalePtBr) })
    var mostrarIa by rememberSaveable { mutableStateOf(false) }
    var rascunho by remember { mutableStateOf<Rascunho?>(null) }
    var texto by rememberSaveable { mutableStateOf("") }
    val lista = rememberLazyListState()
    // Entre a pergunta e a primeira frase (ou a resposta do sistema atual) aparece o "pensando…"
    val aPensar = pensando && conversa.lastOrNull()?.deUsuario == true
    val itensDaLista = conversa.size + if (aPensar) 1 else 0
    LaunchedEffect(itensDaLista, conversa.lastOrNull()?.texto) {
        if (itensDaLista > 0) lista.animateScrollToItem(itensDaLista - 1)
    }
    val podeEnviar = texto.isNotBlank() && !pensando
    fun enviar() {
        if (!podeEnviar) return
        viewModel.perguntar(texto)
        texto = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = MaterialTheme.dimens.paddingScreen)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            IconButton(onClick = aoVoltar, modifier = Modifier.size(Tamanho.toque).offset(x = (-12).dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar")
            }
            Text(
                "Conversar",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            if (iaSuportada) PilulaIaAvancada(estadoIa) { mostrarIa = true }
        }

        if (conversa.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = RoxoHero, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Pergunte sobre seus gastos,\nanote um gasto ou peça ajuda\npara economizar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                state = lista,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                itemsIndexed(conversa) { i, m ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Balao(m)
                        if (m.gasto?.valorCentavos != null) {
                            CartaoDoGasto(
                                mensagem = m,
                                categorias = categorias,
                                formato = formato,
                                sugerir = viewModel::categoriaDoGasto,
                                aoRegistrar = { viewModel.registrarGasto(i, it) },
                                aoEditar = { rascunho = Rascunho(i, it) },
                            )
                        }
                    }
                }
                if (aPensar) item { BalaoPensando() }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.weight(1f).heightIn(min = 52.dp)
            ) {
                BasicTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(RoxoHero),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { enviar() }),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Mensagem" },
                    decorationBox = { campo ->
                        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            if (texto.isEmpty()) {
                                Text(
                                    "Mensagem",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            campo()
                        }
                    }
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { enviar() },
                enabled = podeEnviar,
                modifier = Modifier
                    .size(52.dp)
                    .background(if (podeEnviar) RoxoHero else RoxoHero.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            ) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Enviar", tint = CorSobreRoxo, modifier = Modifier.size(20.dp))
            }
        }
    }

    rascunho?.let { r ->
        BottomSheetEditarTransacao(
            transacao = r.transacao,
            categorias = categorias,
            cartoes = cartoes,
            nova = true,
            onDismiss = { rascunho = null },
            onSalvar = {
                viewModel.registrarGasto(r.indice, it)
                rascunho = null
            },
            onExcluir = { rascunho = null },
        )
    }
    if (mostrarIa) FolhaIaAvancada { mostrarIa = false }
}

internal fun rotuloDaData(data: LocalDate, hoje: LocalDate): String = when {
    data == hoje -> "hoje"
    data == hoje.minusDays(1) -> "ontem"
    else -> "%02d/%02d".format(data.dayOfMonth, data.monthValue) + if (data.year == hoje.year) "" else "/${data.year}"
}

/** Gasto anotado pelo chat: nada é gravado antes de "Registrar" (ou de salvar na folha). */
@Composable
private fun CartaoDoGasto(
    mensagem: Mensagem,
    categorias: List<Categoria>,
    formato: NumberFormat,
    sugerir: suspend (String) -> Categoria?,
    aoRegistrar: (Transacao) -> Unit,
    aoEditar: (Transacao) -> Unit,
) {
    val gasto = mensagem.gasto ?: return
    val centavos = gasto.valorCentavos ?: return
    // A categoria dita no pedido vence a sugestão
    val categoria by produceState<Categoria?>(null, gasto, categorias) {
        value = categorias.firstOrNull { it.id == gasto.categoriaId } ?: sugerir(gasto.descricao)
    }
    val data = gasto.data ?: LocalDate.now()
    fun transacao() = Transacao(
        valorCentavos = centavos,
        data = data,
        categoriaId = categoria?.id ?: 0L,
        tipo = TipoTransacao.DESPESA,
        descricao = gasto.descricao.ifEmpty { null },
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (!mensagem.registrado) {
                Text(
                    (categoria?.nome ?: "Sem categoria") + " · " + rotuloDaData(data, LocalDate.now()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(formato.format(centavos / 100.0), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(gasto.descricao.ifEmpty { "Gasto" }, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            if (mensagem.registrado) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.cores.textoGanho,
                        modifier = Modifier.size(Tamanho.iconePequeno + 2.dp)
                    )
                    Text("Gasto registrado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.cores.textoGanho)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Sem categoria sugerida ou sem descrição, "Registrar" abre a folha para completar
                    BotaoPrincipal(
                        "Registrar",
                        { if (categoria == null || gasto.descricao.isBlank()) aoEditar(transacao()) else aoRegistrar(transacao()) },
                        Modifier.weight(1f),
                    )
                    BotaoSecundario("Editar", { aoEditar(transacao()) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Balao(m: Mensagem) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (m.deUsuario) Alignment.CenterEnd else Alignment.CenterStart) {
        val forma = if (m.deUsuario) RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
        Surface(
            shape = forma,
            color = if (m.deUsuario) RoxoHero else MaterialTheme.colorScheme.surface,
            border = if (m.deUsuario) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                if (m.deUsuario) m.texto else protegido(m.texto),
                style = MaterialTheme.typography.bodyMedium,
                color = if (m.deUsuario) CorSobreRoxo else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

/** "pensando…" no lugar da resposta, até a primeira frase chegar (ou a resposta do sistema atual). */
@Composable
private fun BalaoPensando() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                "pensando…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}
