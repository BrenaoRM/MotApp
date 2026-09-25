package com.example.financacelular.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Transacao::class,
        Categoria::class,
        Orcamento::class,
        Meta::class,
        DespesaRecorrente::class,
        InvestimentoEntity::class,
        AfazerEntity::class,
        CartaoEntity::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transacaoDao(): TransacaoDao
    abstract fun categoriaDao(): CategoriaDao
    abstract fun orcamentoDao(): OrcamentoDao
    abstract fun metaDao(): MetaDao
    abstract fun despesaRecorrenteDao(): DespesaRecorrenteDao
    abstract fun investimentoDao(): InvestimentoDao
    abstract fun afazerDao(): AfazerDao
    abstract fun cartaoDao(): CartaoDao

    companion object {
        const val NOME_ARQUIVO_BANCO = "financa_celular.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    NOME_ARQUIVO_BANCO
                )
                    .fallbackToDestructiveMigration(true)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                val categoriaDao = getInstance(context).categoriaDao()
                                listOf(
                                    Categoria(nome = "Alimentação", tipo = TipoTransacao.DESPESA),
                                    Categoria(nome = "Transporte", tipo = TipoTransacao.DESPESA),
                                    Categoria(nome = "Lazer", tipo = TipoTransacao.DESPESA),
                                    Categoria(nome = "Contas", tipo = TipoTransacao.DESPESA),
                                    Categoria(nome = "Salário", tipo = TipoTransacao.RECEITA),
                                    Categoria(nome = "Outros", tipo = TipoTransacao.DESPESA)
                                ).forEach { categoriaDao.inserir(it) }
                            }
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
        }

        fun destruirInstancia() {
            if (INSTANCE?.isOpen == true) {
                INSTANCE?.close()
            }
            INSTANCE = null
        }
    }
}