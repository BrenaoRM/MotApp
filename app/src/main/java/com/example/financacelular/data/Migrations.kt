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