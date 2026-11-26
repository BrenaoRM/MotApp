package com.example.financacelular.data

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.LocalDate
import java.util.Locale

/** Planilha CSV dos lançamentos de um período, enviada pela lista de compartilhar do Android. */
object ExportarLancamentos {

    enum class Periodo(val rotulo: String) {
        ESTE_MES("Este mês"),
        MES_PASSADO("Mês passado"),
        ESTE_ANO("Este ano"),
        TUDO("Tudo");

        /** Pela data do lançamento (a mesma que aparece no Extrato). */
        fun contem(data: LocalDate, hoje: LocalDate = LocalDate.now()): Boolean = when (this) {
            ESTE_MES -> data.year == hoje.year && data.month == hoje.month
            MES_PASSADO -> hoje.minusMonths(1).let { data.year == it.year && data.month == it.month }
            ESTE_ANO -> data.year == hoje.year
            TUDO -> true
        }
    }

    /**
     * Separador ";" e vírgula decimal, como o Excel em português espera; BOM no início para os acentos
     * abrirem certos. Valor sempre positivo, a coluna Tipo diz se é receita ou despesa.
     */
    fun gerarCsv(lancamentos: List<Transacao>, categorias: Map<Long, String>, cartoes: Map<Long, String>): String {
        val linhas = mutableListOf(
            listOf("Data", "Descrição", "Categoria", "Tipo", "Valor", "Forma de pagamento", "Cartão", "Parcela")
        )
        lancamentos.sortedWith(compareBy<Transacao> { it.data }.thenBy { it.id }).forEach { t ->
            linhas += listOf(
                t.data.format(FORMATO_DATA),
                t.descricao.orEmpty(),
                categorias[t.categoriaId].orEmpty(),
                if (t.tipo == TipoTransacao.RECEITA) "Receita" else "Despesa",
                String.format(Locale.ROOT, "%.2f", t.valor).replace('.', ','),
                when (t.formaPagamento) {
                    FormaPagamento.DINHEIRO -> "Dinheiro"
                    FormaPagamento.DEBITO -> "Débito"
                    FormaPagamento.CARTAO_CREDITO -> "Cartão de crédito"
                },
                t.cartaoId?.let { cartoes[it] }.orEmpty(),
                if (t.totalParcelas > 1) "${t.numeroParcela}/${t.totalParcelas}" else ""
            )
        }
        return "\uFEFF" + linhas.joinToString("\r\n") { linha -> linha.joinToString(";") { campo(it) } } + "\r\n"
    }

    private fun campo(texto: String): String =
        if (texto.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + texto.replace("\"", "\"\"") + "\""
        else texto

    /** Datas de todos os lançamentos, para a folha mostrar quantos entram em cada período. */
    suspend fun datas(context: Context): List<LocalDate> =
        AppDatabase.getInstance(context).transacaoDao().listarTransacoes().first().map { it.data }

    /** Gera o arquivo do [periodo] na pasta de cache (só o último fica); null se o período não tem lançamentos. */
    private suspend fun gerarArquivo(context: Context, periodo: Periodo): File? {
        val csv = gerarCsv(context, periodo) ?: return null
        val pasta = File(context.cacheDir, "exportacoes").apply { mkdirs() }
        pasta.listFiles()?.forEach { it.delete() }
        return File(pasta, nomeDoArquivo()).apply { writeText(csv, Charsets.UTF_8) }
    }

    private suspend fun gerarCsv(context: Context, periodo: Periodo): String? {
        val banco = AppDatabase.getInstance(context)
        val hoje = LocalDate.now()
        val lancamentos = banco.transacaoDao().listarTransacoes().first().filter { periodo.contem(it.data, hoje) }
        if (lancamentos.isEmpty()) return null
        val categorias = banco.categoriaDao().listarTodasSync().associate { it.id to it.nome }
        val cartoes = banco.cartaoDao().listarTodosSync().associate { it.id to it.nome }
        return gerarCsv(lancamentos, categorias, cartoes)
    }

    fun nomeDoArquivo(): String = "motapp-lancamentos-${LocalDate.now()}.csv"

    private fun uriDoArquivo(context: Context, arquivo: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", arquivo)

    /** Gera o arquivo do [periodo] e abre o compartilhar. Devolve se havia lançamentos. */
    suspend fun compartilhar(context: Context, periodo: Periodo): Boolean {
        val arquivo = gerarArquivo(context, periodo) ?: return false
        val envio = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uriDoArquivo(context, arquivo))
            putExtra(Intent.EXTRA_SUBJECT, "Lançamentos do MotApp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(envio, "Compartilhar planilha").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    /** Abre a planilha num app do celular (Excel, Planilhas, Meus Arquivos). False se nenhum app abre CSV. */
    suspend fun abrir(context: Context, periodo: Periodo): Boolean {
        val arquivo = gerarArquivo(context, periodo) ?: return false
        val ver = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriDoArquivo(context, arquivo), "text/csv")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(ver)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /** Android 10+: grava direto em Downloads, sem pedir permissão. */
    fun salvaDireto(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Grava em Downloads (Android 10+). */
    suspend fun salvarEmDownloads(context: Context, periodo: Periodo): Boolean {
        if (!salvaDireto()) return false
        val csv = gerarCsv(context, periodo) ?: return false
        val valores = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, nomeDoArquivo())
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores) ?: return false
        resolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) } ?: return false
        return true
    }

    /** Android 8 e 9: grava no lugar escolhido na janela "Salvar como" do sistema. */
    suspend fun salvarEm(context: Context, uri: Uri, periodo: Periodo): Boolean {
        val csv = gerarCsv(context, periodo) ?: return false
        context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) } ?: return false
        return true
    }
}
