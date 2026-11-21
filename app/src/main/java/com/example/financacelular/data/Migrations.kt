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

/** 12 -> 13: categoria lembrada por estabelecimento detectado (tabela nova, nada existente muda). */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `estabelecimento_categoria` (`chave` TEXT NOT NULL, " +
                "`tipo` TEXT NOT NULL, `categoriaId` INTEGER NOT NULL, PRIMARY KEY(`chave`, `tipo`))"
        )
    }
}
