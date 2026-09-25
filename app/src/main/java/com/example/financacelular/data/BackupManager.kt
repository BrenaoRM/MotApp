package com.example.financacelular.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object BackupManager {

    fun exportarBaseDeDadosParaFicheiro(context: Context): File? {
        return try {
            val dbFile = context.getDatabasePath(AppDatabase.NOME_ARQUIVO_BANCO)
            val backupFile = File(context.cacheDir, "backup_financa.db")

            val arquivoReal = if (dbFile.exists()) {
                dbFile
            } else {
                val dbDir = File(context.applicationInfo.dataDir, "databases")
                dbDir.listFiles()?.find {
                    it.name.contains("financa") &&
                            !it.name.endsWith("-journal") &&
                            !it.name.endsWith("-wal") &&
                            !it.name.endsWith("-shm")
                }
            }

            if (arquivoReal != null && arquivoReal.exists()) {
                forcarCheckpointWal(arquivoReal.absolutePath)

                FileInputStream(arquivoReal).use { input ->
                    FileOutputStream(backupFile).use { output ->
                        input.copyTo(output)
                    }
                }
                backupFile
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun forcarCheckpointWal(caminhoDb: String) {
        try {
            val db = SQLiteDatabase.openDatabase(caminhoDb, null, SQLiteDatabase.OPEN_READWRITE)
            db.rawQuery("PRAGMA wal_checkpoint(FULL);", null).use { it.moveToFirst() }
            db.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}