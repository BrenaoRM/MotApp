package com.example.financacelular.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.ia.AnaliseDoAssistente
import com.example.financacelular.ia.AtipicoParaTela
import com.example.financacelular.ia.CodigoRecomendacao
import com.example.financacelular.ia.EstadoAssistente
import com.example.financacelular.ia.Mensagem
import com.example.financacelular.ia.Perguntas
import com.example.financacelular.ia.Recomendacao
import com.example.financacelular.ui.theme.RoxoHero
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.cores
import com.example.financacelular.ui.theme.dimens
import java.text.NumberFormat
import java.time.format.DateTimeFormatter

private sealed interface FolhaDoAssistente {
    data class DaRecomendacao(val recomendacao: Recomendacao) : FolhaDoAssistente
    data class DoAtipico(val atipico: AtipicoParaTela) : FolhaDoAssistente
    data class Editar(val transacaoId: Long) : FolhaDoAssistente
    data object Perguntas : FolhaDoAssistente
}

/** Conteúdo sobre o violet, escolhido pela luminância como o `BotaoPrincipal` faz. */
private val CorSobreRoxo = if (RoxoHero.luminance() > 0.5f) Color.Black else Color.White

private const val MASCARA = "R$ ••••••"
private val TEXTO_DE_VALOR = Regex("-?R\\$ [\\d.]+")

/** Textos montados pela IA trazem valores no meio da frase: com "ocultar valores" ligado eles somem também. */
private fun protegido(texto: String): String =
    if (ValoresVisiveis.visivel) texto else TEXTO_DE_VALOR.replace(texto, MASCARA)

private fun iconeDe(r: Recomendacao): ImageVector = when (r.codigo) {
    CodigoRecomendacao.POUPANCA_BAIXA -> Icons.Outlined.Savings
    CodigoRecomendacao.EXCESSO_CATEGORIA -> Icons.Outlined.Restaurant
    CodigoRecomendacao.TENDENCIA_ALTA -> Icons.AutoMirrored.Outlined.TrendingUp
    CodigoRecomendacao.ASSINATURAS -> Icons.Outlined.Repeat
    CodigoRecomendacao.ORCAMENTO_ESTOURADO -> Icons.Outlined.Paid
    CodigoRecomendacao.GASTOS_ATIPICOS -> Icons.Outlined.Warning
}

/** Aba do assistente com IA: economia estimada, recomendações, gastos fora do padrão e perguntas. */
@Composable
fun AssistenteScreen(viewModel: AssistenteViewModel = viewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val categorias by viewModel.categorias.collectAsStateWithLifecycle()
    val cartoes by viewModel.cartoes.collectAsStateWithLifecycle()
    var folha by remember { mutableStateOf<FolhaDoAssistente?>(null) }
    val formato = ValoresVisiveis.formato(remember { NumberFormat.getCurrencyInstance(LocalePtBr) })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MaterialTheme.dimens.paddingScreen)
            .padding(bottom = espacoParaBarraFlutuante())
    ) {
        Spacer(modifier = Modifier.height(LocalEspacoCabecalho.current + 4.dp))
        when (val e = estado) {
            EstadoAssistente.Carregando -> Unit
            EstadoAssistente.Indisponivel -> Box(Modifier.padding(top = 72.dp)) {
                EstadoVazio(Icons.Outlined.AutoAwesome, "Análise indisponível neste aparelho")
            }
            is EstadoAssistente.PoucosDados -> PoucosDados(e)
            is EstadoAssistente.Pronto -> Pronto(e, formato, aoAbrir = { folha = it })
        }
        if (estado !is EstadoAssistente.Carregando) {
            SecaoPerguntas(aoAbrir = { pergunta ->
                pergunta?.let(viewModel::perguntar)
                folha = FolhaDoAssistente.Perguntas
            })
        }
    }

    when (val f = folha) {
        null -> Unit
        is FolhaDoAssistente.DaRecomendacao -> FolhaDaRecomendacao(f.recomendacao, formato) { folha = null }
        is FolhaDoAssistente.DoAtipico -> FolhaDoAtipico(
            item = f.atipico,
            formato = formato,
            aoFechar = { folha = null },
            aoVerLancamento = { folha = FolhaDoAssistente.Editar(f.atipico.atipico.transacaoId) },
            aoEstaCerto = {
                viewModel.dispensar(f.atipico.atipico.transacaoId)
                folha = null
            },
        )
        is FolhaDoAssistente.Editar -> {
            val transacao = viewModel.transacao(f.transacaoId)
            if (transacao == null) LaunchedEffect(f) { folha = null } else BottomSheetEditarTransacao(
                transacao = transacao,
                categorias = categorias,
                cartoes = cartoes,
                onDismiss = { folha = null },
                onSalvar = {
                    viewModel.atualizar(it)
                    folha = null
                },
                onExcluir = {
                    viewModel.excluir(transacao)
                    folha = null
                },
            )
        }
        FolhaDoAssistente.Perguntas -> FolhaDePerguntas(viewModel) { folha = null }
    }
}

@Composable
private fun PoucosDados(e: EstadoAssistente.PoucosDados) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 72.dp)) {
        EstadoVazio(
            icone = Icons.Outlined.AutoAwesome,
            mensagem = "Continue lançando\nA análise começa com ${e.minimo} gastos.\nVocê tem ${e.gastos}.",
        )
        Box(
            modifier = Modifier
                .size(width = 180.dp, height = 6.dp)
                .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(3.dp))
                .semantics { contentDescription = "${e.gastos} de ${e.minimo} gastos" }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((e.gastos.toFloat() / e.minimo).coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(RoxoHero, RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun Pronto(e: EstadoAssistente.Pronto, formato: NumberFormat, aoAbrir: (FolhaDoAssistente) -> Unit) {
    if (e.economiaTotalCentavos > 0) {
        CartaoHeroDePagina(cor = RoxoHero) {
            Text(
                "Você pode economizar por mês",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            ValorDoHero("~" + formato.format(e.economiaTotalCentavos / 100.0))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Somando ${e.recomendacoes.size} ${if (e.recomendacoes.size == 1) "recomendação" else "recomendações"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Titulo("Recomendações")
    if (e.recomendacoes.isEmpty()) {
        CartaoUnicoDeLinhas { EstadoVazio(Icons.Outlined.Lightbulb, "Nenhuma recomendação por enquanto") }
    } else {
        CartaoUnicoDeLinhas {
            e.recomendacoes.forEachIndexed { i, r ->
                if (i > 0) DivisorDeLinha()
                LinhaDePagina(
                    inicio = { IconeEmQuadrado(iconeDe(r), MaterialTheme.colorScheme.onSurfaceVariant) },
                    titulo = r.titulo,
                    valor = "",
                    subtitulo = protegido(r.detalhe),
                    onClick = { aoAbrir(FolhaDoAssistente.DaRecomendacao(r)) },
                )
            }
        }
    }

    Titulo("Fora do seu padrão")
    if (e.atipicosDoMes.isEmpty()) {
        CartaoUnicoDeLinhas { EstadoVazio(Icons.Outlined.Check, "Nada fora do seu padrão este mês") }
    } else {
        val formatoData = remember { DateTimeFormatter.ofPattern("d MMM", LocalePtBr) }
        CartaoUnicoDeLinhas {
            e.atipicosDoMes.forEachIndexed { i, a ->
                if (i > 0) DivisorDeLinha()
                LinhaDePagina(
                    inicio = { IconeEmQuadrado(Icons.Outlined.Warning, MaterialTheme.cores.alerta) },
                    titulo = a.descricao,
                    valor = formato.format(a.atipico.valorCentavos / 100.0),
                    subtitulo = AnaliseDoAssistente.resumoDoMotivo(a.atipico) + " · " + formatoData.format(a.data),
                    onClick = { aoAbrir(FolhaDoAssistente.DoAtipico(a)) },
                )
            }
        }
    }
}

@Composable
private fun Titulo(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 2.dp, top = 22.dp, bottom = 10.dp).semantics { heading() }
    )
}

@Composable
private fun IconeEmQuadrado(icone: ImageVector, cor: Color) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(Tamanho.iconeGrande))
    }
}

// ---------------------------------------------------------------- perguntas

/** Campo e chips da aba: tocar abre a folha "Perguntas" (com a pergunta já enviada, no caso do chip). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SecaoPerguntas(aoAbrir: (String?) -> Unit) {
    Titulo("Perguntas")
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(role = Role.Button, onClickLabel = "Abrir perguntas") { aoAbrir(null) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 6.dp)) {
            Text(
                "Pergunte sobre seus gastos",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier.size(40.dp).background(RoxoHero, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, tint = CorSobreRoxo, modifier = Modifier.size(18.dp))
            }
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Perguntas.SUGESTOES_INICIAIS.forEach { ChipDePergunta(it) { aoAbrir(it) } }
    }
}

@Composable
private fun ChipDePergunta(texto: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .height(36.dp)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(texto, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolhaDePerguntas(viewModel: AssistenteViewModel, aoFechar: () -> Unit) {
    val conversa by viewModel.conversa.collectAsStateWithLifecycle()
    val sugestoes by viewModel.sugestoes.collectAsStateWithLifecycle()
    val pensando by viewModel.pensando.collectAsStateWithLifecycle()
    var texto by remember { mutableStateOf("") }
    val lista = rememberLazyListState()
    LaunchedEffect(conversa.size) {
        if (conversa.isNotEmpty()) lista.animateScrollToItem(conversa.lastIndex)
    }
    fun enviar(pergunta: String) {
        viewModel.perguntar(pergunta)
        texto = ""
    }

    FolhaApp(onDismiss = aoFechar) {
        CabecalhoDeFolha("Perguntas", aoFechar)
        Spacer(modifier = Modifier.height(10.dp))
        if (conversa.isNotEmpty()) {
            LazyColumn(
                state = lista,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)
            ) {
                items(conversa) { Balao(it) }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            sugestoes.forEach { ChipDePergunta(it) { if (!pensando) enviar(it) } }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val podeEnviar = texto.isNotBlank() && !pensando
        CampoApp(
            valor = texto,
            aoMudar = { texto = it },
            rotulo = "Pergunte sobre seus gastos",
            trailingIcon = {
                IconButton(onClick = { enviar(texto) }, enabled = podeEnviar, modifier = Modifier.size(Tamanho.toque)) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Enviar",
                        tint = if (podeEnviar) RoxoHero else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun Balao(m: Mensagem) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (m.deUsuario) Alignment.CenterEnd else Alignment.CenterStart) {
        val forma = if (m.deUsuario) RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
        Surface(
            shape = forma,
            color = if (m.deUsuario) RoxoHero else MaterialTheme.colorScheme.background,
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

// ---------------------------------------------------------------- detalhes

@Composable
private fun FolhaDaRecomendacao(r: Recomendacao, formato: NumberFormat, aoFechar: () -> Unit) {
    FolhaApp(onDismiss = aoFechar) {
        CabecalhoDeFolha(r.titulo, aoFechar)
        Spacer(modifier = Modifier.height(8.dp))
        if (r.economiaMensalCentavos > 0) {
            Row(verticalAlignment = Alignment.Bottom) {
                ValorDoHero("~" + formato.format(r.economiaMensalCentavos / 100.0), Modifier.weight(1f, fill = false))
                Text(
                    " /mês (estimativa)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            protegido(r.detalhe),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Por que estou vendo isso?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(protegido(r.porQue), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(20.dp))
        BotaoSecundario("Entendi", aoFechar, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun FolhaDoAtipico(
    item: AtipicoParaTela,
    formato: NumberFormat,
    aoFechar: () -> Unit,
    aoVerLancamento: () -> Unit,
    aoEstaCerto: () -> Unit,
) {
    val a = item.atipico
    val formatoData = remember { DateTimeFormatter.ofPattern("d MMM yyyy", LocalePtBr) }
    FolhaApp(onDismiss = aoFechar) {
        CabecalhoDeFolha(item.descricao, aoFechar)
        Spacer(modifier = Modifier.height(8.dp))
        ValorDoHero(formato.format(a.valorCentavos / 100.0))
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.cores.alerta,
                modifier = Modifier.padding(top = 2.dp).size(Tamanho.iconePequeno + 2.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                AnaliseDoAssistente.textoDoMotivo(a),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.cores.alerta
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinhaDeDetalhe("Data", formatoData.format(item.data))
        LinhaDeDetalhe("Categoria", item.nomeCategoria)
        a.valorReferenciaCentavos?.let { LinhaDeDetalhe("Valor típico", formato.format(it / 100.0)) }
        Spacer(modifier = Modifier.height(20.dp))
        BotaoPrincipal("Ver lançamento", aoVerLancamento, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        BotaoSecundario("Está certo", aoEstaCerto, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(10.dp))
    }
}
