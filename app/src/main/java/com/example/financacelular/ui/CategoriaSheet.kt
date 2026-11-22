package com.example.financacelular.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.example.financacelular.data.Categoria
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.ui.theme.Espaco
import com.example.financacelular.ui.theme.RoxoHero
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.cores

private const val ICONES_POR_LINHA = 6

/**
 * Folha para criar ou editar uma categoria (nome e ícone). Com [item] nulo cria uma nova do tipo [tipoNova].
 * Excluir apaga também os lançamentos e as recorrências da categoria, por isso a confirmação (no mesmo sheet)
 * avisa quantos são antes de apagar. A categoria "Fatura" é do sistema: só o ícone muda.
 * [nomesDoTipo] são os nomes já usados no mesmo tipo, para não repetir.
 */
@Composable
fun FolhaEditarCategoria(
    item: ItemCategoria?,
    tipoNova: TipoTransacao,
    nomesDoTipo: List<String>,
    aoSalvar: (nome: String, icone: String?) -> Unit,
    aoExcluir: () -> Unit,
    aoFechar: () -> Unit,
    cor: Color = RoxoHero,
    /** Outras categorias do mesmo tipo; com [item] em uso e ao menos uma, a exclusão oferece "Mover para". */
    destinos: List<Categoria> = emptyList(),
    aoMoverEExcluir: (Categoria) -> Unit = {}
) {
    val categoria = item?.categoria
    val tipo = categoria?.tipo ?: tipoNova
    val sistema = item?.sistema == true
    var nome by rememberSaveable(categoria?.id) { mutableStateOf(categoria?.nome ?: "") }
    var icone by rememberSaveable(categoria?.id) { mutableStateOf(categoria?.icone) }
    var confirmando by rememberSaveable(categoria?.id) { mutableStateOf(false) }
    var destinoId by rememberSaveable(categoria?.id) { mutableStateOf<Long?>(null) }

    val nomeTratado = nome.trim()
    val repetido = nomeTratado.isNotEmpty() && nomesDoTipo.any {
        it.equals(nomeTratado, ignoreCase = true) && !it.equals(categoria?.nome, ignoreCase = true)
    }

    FolhaApp(onDismiss = aoFechar) {
        AnimatedContent(
            targetState = confirmando,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) using SizeTransform() },
            label = "confirmarExclusaoCategoria"
        ) { pedindoConfirmacao ->
            if (pedindoConfirmacao && item != null && item.emUso && destinos.isNotEmpty()) {
                ConfirmarMoverCategoria(
                    item = item,
                    destinos = destinos,
                    destinoId = destinoId,
                    aoEscolher = { destinoId = it },
                    aoMover = { destino -> aoMoverEExcluir(destino) },
                    aoExcluirTudo = aoExcluir
                )
            } else if (pedindoConfirmacao && item != null) {
                val partes = buildList {
                    if (item.lancamentos > 0) add(if (item.lancamentos == 1) "1 lançamento" else "${item.lancamentos} lançamentos")
                    if (item.recorrencias > 0) add(if (item.recorrencias == 1) "1 recorrência" else "${item.recorrencias} recorrências")
                }
                ConfirmarAcaoConteudo(
                    titulo = "Excluir categoria?",
                    texto = if (item.emUso) {
                        "Ao excluir \"${item.categoria.nome}\" você perde também tudo o que usa essa categoria, " +
                            "inclusive os limites de orçamento dela. Seus saldos e relatórios mudam. Essa ação não pode ser desfeita."
                    } else {
                        "A categoria \"${item.categoria.nome}\" será removida. Essa ação não pode ser desfeita."
                    },
                    textoConfirmar = if (item.emUso) "Excluir tudo" else "Excluir",
                    onConfirmar = aoExcluir,
                    onCancelar = { confirmando = false },
                    resumo = if (item.emUso) {
                        {
                            Text(
                                "Serão apagados: " + partes.joinToString(" e "),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.cores.textoGasto
                            )
                        }
                    } else null
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CabecalhoDeFolha(
                        titulo = if (categoria == null) "Nova categoria" else "Editar categoria",
                        aoFechar = aoFechar,
                        subtitulo = if (tipo == TipoTransacao.DESPESA) "Gasto" else "Ganho"
                    )
                    Spacer(modifier = Modifier.height(Espaco.lg))

                    CampoApp(
                        valor = nome,
                        aoMudar = { nome = it },
                        rotulo = "Nome",
                        habilitado = !sistema,
                        isError = repetido,
                        textoApoio = if (repetido) "Já existe uma categoria com esse nome" else null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )

                    Spacer(modifier = Modifier.height(Espaco.md))
                    Text(
                        "Ícone",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Espaco.xs))
                    GradeDeIcones(selecionado = icone, aoSelecionar = { icone = it }, cor = cor)

                    if (icone == null) {
                        Spacer(modifier = Modifier.height(Espaco.sm))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                iconeParaCategoria(nomeTratado),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(Tamanho.iconePequeno)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Automático: o ícone sugerido pelo nome",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (sistema) {
                        Spacer(modifier = Modifier.height(Espaco.sm))
                        Text(
                            "Categoria do sistema: o pagamento de cartão usa o nome, então só o ícone pode mudar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (item != null && item.emUso) {
                        Spacer(modifier = Modifier.height(Espaco.sm))
                        Text(
                            "Em uso por " + listOfNotNull(
                                item.lancamentos.takeIf { it > 0 }?.let { if (it == 1) "1 lançamento" else "$it lançamentos" },
                                item.recorrencias.takeIf { it > 0 }?.let { if (it == 1) "1 recorrência" else "$it recorrências" }
                            ).joinToString(" e ") + ".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(Espaco.xl))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Espaco.md)
                    ) {
                        if (item != null && !sistema) {
                            BotaoSecundario(
                                texto = "Excluir",
                                onClick = { confirmando = true },
                                modifier = Modifier.weight(1f),
                                cor = MaterialTheme.cores.textoGasto
                            )
                        }
                        BotaoPrincipal(
                            texto = "Salvar",
                            onClick = { aoSalvar(nomeTratado, icone) },
                            modifier = Modifier.weight(1f),
                            cor = cor,
                            corConteudo = Color.White,
                            enabled = nomeTratado.isNotEmpty() && !repetido
                        )
                    }
                    Spacer(modifier = Modifier.height(Espaco.sm))
                }
            }
        }
    }
}

/** Exclusão de categoria em uso: mover os lançamentos para outra do mesmo tipo (mantém o histórico) ou apagar tudo. */
@Composable
private fun ConfirmarMoverCategoria(
    item: ItemCategoria,
    destinos: List<Categoria>,
    destinoId: Long?,
    aoEscolher: (Long) -> Unit,
    aoMover: (Categoria) -> Unit,
    aoExcluirTudo: () -> Unit
) {
    val partes = buildList {
        if (item.lancamentos > 0) add(if (item.lancamentos == 1) "1 lançamento" else "${item.lancamentos} lançamentos")
        if (item.recorrencias > 0) add(if (item.recorrencias == 1) "1 recorrência" else "${item.recorrencias} recorrências")
    }.joinToString(" e ")
    val escolhido = destinos.firstOrNull { it.id == destinoId }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Excluir \"${item.categoria.nome}\"?", style = MaterialTheme.typography.titleLarge, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "$partes usam esta categoria. Mova para outra para manter o histórico. " +
                "O limite de orçamento dela será descartado.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Espaco.lg))
        Text(
            "Mover para",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Espaco.sm))
        Column(
            modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Espaco.sm)
        ) {
            destinos.chunked(3).forEach { linha ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Espaco.sm)) {
                    linha.forEach { destino ->
                        BlocoDeEscolha(
                            selecionado = destino.id == destinoId,
                            onClick = { aoEscolher(destino.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    iconeParaCategoria(destino.nome, destino.icone),
                                    contentDescription = null,
                                    modifier = Modifier.size(Tamanho.icone)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    destino.nome,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    repeat(3 - linha.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
        Spacer(modifier = Modifier.height(Espaco.xl))
        BotaoPrincipal(
            texto = "Mover e excluir",
            onClick = { escolhido?.let(aoMover) },
            modifier = Modifier.fillMaxWidth(),
            cor = RoxoHero,
            corConteudo = Color.White,
            enabled = escolhido != null
        )
        Spacer(modifier = Modifier.height(Espaco.md))
        BotaoSecundario(
            texto = "Excluir tudo",
            onClick = aoExcluirTudo,
            modifier = Modifier.fillMaxWidth(),
            cor = MaterialTheme.cores.textoGasto
        )
        Spacer(modifier = Modifier.height(Espaco.sm))
    }
}

/** Grade de ícones: "Automático" primeiro, depois os de [IconesDeCategoria], [ICONES_POR_LINHA] por linha. */
@Composable
private fun GradeDeIcones(selecionado: String?, aoSelecionar: (String?) -> Unit, cor: Color) {
    val opcoes: List<Triple<String?, ImageVector, String>> =
        listOf(Triple(null, Icons.Outlined.AutoAwesome, "Automático")) +
            IconesDeCategoria.map { Triple<String?, ImageVector, String>(it.chave, it.icone, it.rotulo) }
    val forma = RoundedCornerShape(12.dp)

    Column(modifier = Modifier.fillMaxWidth()) {
        opcoes.chunked(ICONES_POR_LINHA).forEach { linha ->
            Row(modifier = Modifier.fillMaxWidth()) {
                linha.forEach { (chave, icone, rotulo) ->
                    val ativo = chave == selecionado
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(Tamanho.toque)
                            .clip(forma)
                            .then(if (ativo) Modifier.border(1.dp, cor, forma) else Modifier)
                            .selectable(selected = ativo, role = Role.RadioButton, onClick = { aoSelecionar(chave) }),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icone,
                            contentDescription = rotulo,
                            tint = if (ativo) cor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(Tamanho.iconeGrande)
                        )
                    }
                }
                // completa a última linha para as células manterem a mesma largura
                repeat(ICONES_POR_LINHA - linha.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}
