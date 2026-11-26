package com.example.financacelular.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 8 -> 9: adiciona transacoes.recorrenteId e vincula as transações antigas
 * geradas por assinaturas/recorrentes (descrição exatamente "<nome> (Assinatura)"
 * ou "<nome> (Recorrente)").
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transacoes ADD COLUMN recorrenteId INTEGER")

        db.execSQL(
            """
            UPDATE transacoes
            SET recorrenteId = (
                SELECT r.id
                FROM despesas_recorrentes r
                WHERE transacoes.descricao = r.nome || ' (Assinatura)'
                   OR transacoes.descricao = r.nome || ' (Recorrente)'
                ORDER BY r.id
                LIMIT 1
            )
            WHERE descricao LIKE '% (Assinatura)'
               OR descricao LIKE '% (Recorrente)'
            """.trimIndent()
        )
    }
}

/**
 * 9 -> 10: adiciona despesas_recorrentes.cartaoId e preenche, para cada assinatura,
 * com o cartão da cobrança mais recente já gerada.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE despesas_recorrentes ADD COLUMN cartaoId INTEGER")

        db.execSQL(
            """
            UPDATE despesas_recorrentes
            SET cartaoId = (
                SELECT t.cartaoId
                FROM transacoes t
                WHERE t.recorrenteId = despesas_recorrentes.id
                  AND t.cartaoId IS NOT NULL
                ORDER BY t.anoMes DESC, t.data DESC
                LIMIT 1
            )
            """.trimIndent()
        )
    }
}

/** 11 -> 12: forma de pagamento da recorrência (débito ou crédito); as existentes continuam no crédito. */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE despesas_recorrentes ADD COLUMN formaPagamento TEXT NOT NULL DEFAULT 'CARTAO_CREDITO'")
    }
}

/** 10 -> 11: ícone escolhido para a categoria (null = ícone sugerido pelo nome). */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE categorias ADD COLUMN icone TEXT")
    }
}

/** 13 -> 14: índices nas colunas filtradas de transacoes e nas chaves estrangeiras de categoria (nada de dados muda). */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transacoes_anoMes` ON `transacoes` (`anoMes`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transacoes_data` ON `transacoes` (`data`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transacoes_categoriaId` ON `transacoes` (`categoriaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transacoes_cartaoId` ON `transacoes` (`cartaoId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transacoes_recorrenteId` ON `transacoes` (`recorrenteId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_despesas_recorrentes_categoriaId` ON `despesas_recorrentes` (`categoriaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_orcamentos_categoriaId` ON `orcamentos` (`categoriaId`)")
    }
}

/** 12 -> 13: categoria lembrada por estabelecimento detectado (tabela nova, nada existente muda). */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `estabelecimento_categoria` (`chave` TEXT NOT NULL, " +
                "`tipo` TEXT NOT NULL, `categoriaId` INTEGER NOT NULL, PRIMARY KEY(`chave`, `tipo`))"
        )
    }
}

/** 14 -> 15: transacoes.dataCompra, o dia real da compra parcelada (as antigas ficam sem). */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transacoes ADD COLUMN dataCompra TEXT")
    }
}

/**
 * Recria [tabela] com o SQL novo ([criar], com o nome `<tabela>_nova`), copiando as colunas: [colunas] são
 * as que vão iguais e [centavos] mapeia coluna nova -> coluna antiga em reais (REAL), gravada em centavos.
 */
private fun SupportSQLiteDatabase.recriarEmCentavos(
    tabela: String,
    criar: String,
    colunas: List<String>,
    centavos: Map<String, String>,
    indices: List<String> = emptyList()
) {
    execSQL(criar)
    val destino = (colunas + centavos.keys).joinToString(", ") { "`$it`" }
    val origem = (colunas.map { "`$it`" } + centavos.values.map { "CAST(ROUND(`$it` * 100) AS INTEGER)" }).joinToString(", ")
    execSQL("INSERT INTO `${tabela}_nova` ($destino) SELECT $origem FROM `$tabela`")
    execSQL("DROP TABLE `$tabela`")
    execSQL("ALTER TABLE `${tabela}_nova` RENAME TO `$tabela`")
    indices.forEach { execSQL(it) }
}

/**
 * 15 -> 16: dinheiro em centavos (INTEGER) nas cinco tabelas com valor, e transacoes.pagaFaturaDoCartaoId,
 * que passa a identificar o pagamento de fatura. Os pagamentos antigos são reconhecidos pela descrição
 * ("Pagamento de Fatura - yyyy-MM" = cartão padrão; "... - yyyy-MM - id" = outro cartão), como antes.
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.recriarEmCentavos(
            tabela = "transacoes",
            criar = "CREATE TABLE IF NOT EXISTS `transacoes_nova` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`valorCentavos` INTEGER NOT NULL, `data` TEXT NOT NULL, `categoriaId` INTEGER NOT NULL, `tipo` TEXT NOT NULL, " +
                "`descricao` TEXT, `formaPagamento` TEXT NOT NULL, `cartaoId` INTEGER, `anoMes` TEXT, " +
                "`numeroParcela` INTEGER NOT NULL, `totalParcelas` INTEGER NOT NULL, `grupoParcelamentoId` TEXT, " +
                "`recorrenteId` INTEGER, `dataCompra` TEXT, `pagaFaturaDoCartaoId` INTEGER, " +
                "FOREIGN KEY(`categoriaId`) REFERENCES `categorias`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            colunas = listOf(
                "id", "data", "categoriaId", "tipo", "descricao", "formaPagamento", "cartaoId", "anoMes",
                "numeroParcela", "totalParcelas", "grupoParcelamentoId", "recorrenteId", "dataCompra"
            ),
            centavos = mapOf("valorCentavos" to "valor"),
            indices = listOf(
                "CREATE INDEX IF NOT EXISTS `index_transacoes_anoMes` ON `transacoes` (`anoMes`)",
                "CREATE INDEX IF NOT EXISTS `index_transacoes_data` ON `transacoes` (`data`)",
                "CREATE INDEX IF NOT EXISTS `index_transacoes_categoriaId` ON `transacoes` (`categoriaId`)",
                "CREATE INDEX IF NOT EXISTS `index_transacoes_cartaoId` ON `transacoes` (`cartaoId`)",
                "CREATE INDEX IF NOT EXISTS `index_transacoes_recorrenteId` ON `transacoes` (`recorrenteId`)"
            )
        )
        // "Pagamento de Fatura - " tem 22 letras: o mês vai da 23ª à 29ª, e o id do cartão (se houver) começa na 33ª
        val mes = "'Pagamento de Fatura - [0-9][0-9][0-9][0-9]-[0-9][0-9]'"
        db.execSQL(
            "UPDATE transacoes SET pagaFaturaDoCartaoId = $ID_CARTAO_PADRAO, anoMes = substr(descricao, 23, 7) " +
                "WHERE cartaoId IS NULL AND descricao GLOB $mes"
        )
        db.execSQL(
            "UPDATE transacoes SET pagaFaturaDoCartaoId = CAST(substr(descricao, 33) AS INTEGER), " +
                "anoMes = substr(descricao, 23, 7) " +
                "WHERE cartaoId IS NULL AND descricao GLOB ${mes.dropLast(1)} - [0-9]*' " +
                "AND substr(descricao, 33) NOT GLOB '*[^0-9]*'"
        )
        db.recriarEmCentavos(
            tabela = "despesas_recorrentes",
            criar = "CREATE TABLE IF NOT EXISTS `despesas_recorrentes_nova` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`nome` TEXT NOT NULL, `valorCentavos` INTEGER NOT NULL, `categoriaId` INTEGER NOT NULL, " +
                "`diaDoMes` INTEGER NOT NULL, `tipo` TEXT NOT NULL, `dataCriacao` TEXT NOT NULL, `cartaoId` INTEGER, " +
                "`formaPagamento` TEXT NOT NULL DEFAULT 'CARTAO_CREDITO', " +
                "FOREIGN KEY(`categoriaId`) REFERENCES `categorias`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            colunas = listOf("id", "nome", "categoriaId", "diaDoMes", "tipo", "dataCriacao", "cartaoId", "formaPagamento"),
            centavos = mapOf("valorCentavos" to "valor"),
            indices = listOf(
                "CREATE INDEX IF NOT EXISTS `index_despesas_recorrentes_categoriaId` ON `despesas_recorrentes` (`categoriaId`)"
            )
        )
        db.recriarEmCentavos(
            tabela = "investimentos",
            criar = "CREATE TABLE IF NOT EXISTS `investimentos_nova` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`nome` TEXT NOT NULL, `categoria` TEXT NOT NULL, `valorInvestidoCentavos` INTEGER NOT NULL, " +
                "`data` TEXT NOT NULL, `anoMes` TEXT NOT NULL)",
            colunas = listOf("id", "nome", "categoria", "data", "anoMes"),
            centavos = mapOf("valorInvestidoCentavos" to "valorInvestido")
        )
        db.recriarEmCentavos(
            tabela = "metas",
            criar = "CREATE TABLE IF NOT EXISTS `metas_nova` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`nome` TEXT NOT NULL, `valorAlvoCentavos` INTEGER NOT NULL, `valorAtualCentavos` INTEGER NOT NULL)",
            colunas = listOf("id", "nome"),
            centavos = mapOf("valorAlvoCentavos" to "valorAlvo", "valorAtualCentavos" to "valorAtual")
        )
        db.recriarEmCentavos(
            tabela = "orcamentos",
            criar = "CREATE TABLE IF NOT EXISTS `orcamentos_nova` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`categoriaId` INTEGER NOT NULL, `anoMes` TEXT NOT NULL, `valorLimiteCentavos` INTEGER NOT NULL, " +
                "FOREIGN KEY(`categoriaId`) REFERENCES `categorias`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            colunas = listOf("id", "categoriaId", "anoMes"),
            centavos = mapOf("valorLimiteCentavos" to "valorLimite"),
            indices = listOf("CREATE INDEX IF NOT EXISTS `index_orcamentos_categoriaId` ON `orcamentos` (`categoriaId`)")
        )
    }
}

/** 16 -> 17: cache de vetores da IA (`ia_vetor`) e alertas de atípicos dispensados (`ia_alerta_dispensado`). */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `ia_vetor` (`chave` TEXT NOT NULL, `vetor` BLOB NOT NULL, `versaoModelo` INTEGER NOT NULL, PRIMARY KEY(`chave`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `ia_alerta_dispensado` (`transacaoId` INTEGER NOT NULL, `dispensadoEm` INTEGER NOT NULL, PRIMARY KEY(`transacaoId`))")
    }
}
