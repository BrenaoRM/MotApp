package com.example.financacelular.ia.llm

import com.example.financacelular.data.Categoria
import com.example.financacelular.data.DespesaRecorrente
import com.example.financacelular.data.Orcamento
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import com.example.financacelular.ia.Atipico
import com.example.financacelular.ia.CodigoRecomendacao
import com.example.financacelular.ia.Intencao
import com.example.financacelular.ia.MotivoAtipico
import com.example.financacelular.ia.Pergunta
import com.example.financacelular.ia.Recomendacao
import com.example.financacelular.ia.RespondedorDePerguntas
import com.example.financacelular.ia.Severidade
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

/**
 * Gera os fatos que treinam o LLM (ia/llm). Só roda com `-Dexportar.fatos=<saida.jsonl>` (caminho relativo a `app/`);
 * sem a propriedade fica de fora (Assume), para a suíte normal não pagar o custo.
 */
class ExportarFatosParaTreino {
    private val padrao = listOf("Alimentação", "Transporte", "Lazer", "Contas", "Outros")
    private val inventadas = listOf(
        "Mercado", "Saúde", "Pet \"Rex\"", "🍔 Lanche", "Educação", "Academia", "Viagem", "Casa", "Farmácia", "Café & Cia",
        "Streaming", "Presentes", "Carro 2", "Roupas",
    )
    private val lugares = listOf("Padaria", "Posto Shell", "Cinema", "Uber", "Farmácia Pague Menos", "Amazon", "iFood", "Show 2026")
    private val assinaturasNomes = listOf("Netflix", "Spotify", "Academia", "Google One", "Disney+", "Internet")
    private val titulos = listOf(
        "Você está guardando %d%% da renda", "Você gasta %d%% a mais do que ganha", "Reduza gastos com delivery",
        "Revise suas assinaturas",
    )

    @Test
    fun exportar() {
        val saida = System.getProperty("exportar.fatos").orEmpty()
        assumeTrue("sem -Dexportar.fatos", saida.isNotBlank())
        val rnd = Random(300)
        val hoje = LocalDate.of(2026, 11, 26)
        val atual = YearMonth.from(hoje)
        val arquivo = File(saida).also { it.absoluteFile.parentFile.mkdirs() }
        arquivo.bufferedWriter(Charsets.UTF_8).use { w ->
            repeat(300) { _ ->
                var proximoId = 1L
                val nomes = (padrao + inventadas.shuffled(rnd).take(4)).shuffled(rnd).take(rnd.nextInt(3, 9))
                val despesas = nomes.mapIndexed { i, n -> Categoria(i + 1L, n, TipoTransacao.DESPESA) }
                val categorias = despesas + Categoria(100, "Salário", TipoTransacao.RECEITA)
                val pesos = despesas.associate { it.id to rnd.nextInt(20, 1500) }
                val transacoes = (0..5).flatMap { atras ->
                    val mes = atual.minusMonths(atras.toLong())
                    val dias = if (mes == atual) 25 else mes.lengthOfMonth()
                    val gastos = List(rnd.nextInt(4, 25)) {
                        val c = despesas.random(rnd)
                        Transacao(
                            id = proximoId++, valorCentavos = rnd.nextLong(500, pesos.getValue(c.id) * 100L), data = mes.atDay(rnd.nextInt(1, dias + 1)),
                            categoriaId = c.id, tipo = TipoTransacao.DESPESA,
                            descricao = if (rnd.nextInt(3) == 0) lugares.random(rnd) else null,
                        )
                    }
                    val entradas = List(rnd.nextInt(0, 3)) {
                        Transacao(
                            id = proximoId++, valorCentavos = rnd.nextLong(100_000, 800_000), data = mes.atDay(5),
                            categoriaId = 100, tipo = TipoTransacao.RECEITA, descricao = if (rnd.nextBoolean()) "Salário" else null,
                        )
                    }
                    gastos + entradas
                }
                val orcamentos = despesas.filter { rnd.nextInt(3) == 0 }.map {
                    Orcamento(it.id, it.id, atual.minusMonths(5).toString(), rnd.nextLong(20_000, 150_000))
                }
                val recorrentes = List(rnd.nextInt(0, 6)) {
                    DespesaRecorrente(
                        nome = assinaturasNomes.random(rnd), valorCentavos = rnd.nextLong(1_500, 15_000),
                        categoriaId = despesas.random(rnd).id, diaDoMes = rnd.nextInt(1, 29),
                    )
                }
                val motivos = MotivoAtipico.entries
                val atipicos = transacoes.filter { it.tipo == TipoTransacao.DESPESA }.shuffled(rnd).take(rnd.nextInt(0, 5)).map {
                    Atipico(it.id, rnd.nextDouble(1.0, 5.0), motivos.random(rnd), it.valorCentavos, if (rnd.nextBoolean()) it.valorCentavos / 3 else null)
                }
                val recomendacoes = titulos.shuffled(rnd).take(rnd.nextInt(0, 4)).map {
                    Recomendacao(
                        CodigoRecomendacao.POUPANCA_BAIXA, Severidade.MEDIA, if ('%' in it) it.format(rnd.nextInt(1, 60)) else it, "", "", 0, null,
                    )
                }
                Intencao.entries.filter { it != Intencao.DESCONHECIDA }.forEach { intencao ->
                    for (atras in 0..5) {
                        if (rnd.nextInt(2) == 0) continue
                        val cat = if (rnd.nextBoolean()) despesas.random(rnd).id else null
                        val p = Pergunta(intencao, 0.9f, atual.minusMonths(atras.toLong()), cat)
                        val fato = RespondedorDePerguntas.fato(p, transacoes, categorias, recorrentes, orcamentos, recomendacoes, atipicos, hoje)
                        w.write(fato.paraJson().toString())
                        w.newLine()
                    }
                }
            }
        }
    }
}
