package com.example.financacelular.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.financacelular.ia.AlertaDispensado
import com.example.financacelular.ia.AlertaDispensadoDao
import com.example.financacelular.ia.IaFinanceira
import com.example.financacelular.ia.VetorIa
import com.example.financacelular.ia.VetorIaDao
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
        CartaoEntity::class,
        EstabelecimentoCategoria::class,
        VetorIa::class,
        AlertaDispensado::class
    ],
    version = AppDatabase.VERSAO,
    exportSchema = true
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
    abstract fun estabelecimentoCategoriaDao(): EstabelecimentoCategoriaDao
    abstract fun vetorIaDao(): VetorIaDao
    abstract fun alertaDispensadoDao(): AlertaDispensadoDao

    companion object {
        const val NOME_ARQUIVO_BANCO = "financa_celular.db"
        const val VERSAO = 17

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    NOME_ARQUIVO_BANCO
                )
                    .addMigrations(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                val categoriaDao = INSTANCE?.categoriaDao() ?: return@launch
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
            synchronized(this) {
                // Sem checar isOpen: uma abertura que falhou deixa isOpen falso mas o Room ainda precisa fechar
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        /**
         * Fecha o banco e roda [bloco] segurando a mesma trava de [getInstance]: ninguém recria o banco
         * (nem um repositório sobre ele) enquanto o arquivo é trocado; quem chamar espera e recebe o novo.
         */
        fun <T> comBancoFechado(bloco: () -> T): T = synchronized(this) {
            FinancaRepository.destruirInstancia()
            destruirInstancia()
            try {
                bloco()
            } finally {
                // Restauro (ou rollback): o modelo e a memória da IA vieram do banco anterior
                IaFinanceira.invalidar()
            }
        }
    }
}