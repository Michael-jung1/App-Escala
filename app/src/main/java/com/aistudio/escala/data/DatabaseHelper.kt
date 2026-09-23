package com.aistudio.escala.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.aistudio.escala.util.DateUtils
import com.aistudio.escala.util.SearchUtils
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

class DatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val TAG = "DatabaseHelper"
        const val DB_NAME = "escala.db"
        const val DB_VERSION = 1

        @Volatile
        private var INSTANCE: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DatabaseHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        copyDatabaseIfNeeded()
    }

    private fun copyDatabaseIfNeeded() {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) {
            try {
                dbFile.parentFile?.mkdirs()
                context.assets.open(DB_NAME).use { input ->
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Successfully copied escala.db from assets to ${dbFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Error copying database from assets", e)
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Database is copied from pre-populated asset
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Upgrade logic if schema changes
    }

    fun listarIgrejas(): List<Igreja> {
        val list = mutableListOf<Igreja>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, nome, titulo_escala, coordenadores FROM igreja ORDER BY nome",
            null
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    Igreja(
                        id = c.getLong(0),
                        nome = c.getString(1),
                        tituloEscala = if (c.isNull(2)) null else c.getString(2),
                        coordenadores = if (c.isNull(3)) null else c.getString(3)
                    )
                )
            }
        }
        return list
    }

    fun listarPessoas(): List<String> {
        val list = mutableListOf<String>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT DISTINCT TRIM(pessoa_nome) AS pessoa_nome
            FROM escalacao
            WHERE pessoa_nome IS NOT NULL
              AND TRIM(pessoa_nome) != ''
            ORDER BY pessoa_nome COLLATE NOCASE
            """.trimIndent(),
            null
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val nome = c.getString(0)
                if (!nome.isNullOrBlank()) {
                    list.add(nome.trim())
                }
            }
        }
        return list
    }

    fun buscarEscalaPessoa(nomeBusca: String, apenasFuturas: Boolean = true): List<EscalacaoPessoa> {
        val trimmed = nomeBusca.trim()
        if (trimmed.isEmpty()) return emptyList()

        val list = mutableListOf<EscalacaoPessoa>()
        val db = readableDatabase
        val hoje = LocalDate.now()

        // 1. Tenta consulta direta com LIKE e o termo fornecido
        val cursor = db.rawQuery(
            """
            SELECT
                igreja.nome AS igreja,
                posto.funcao AS funcao,
                escalacao.data_servico AS data,
                periodo_escala.referencia AS periodo,
                escalacao.pessoa_nome AS pessoa
            FROM escalacao
            JOIN posto ON posto.id = escalacao.posto_id
            JOIN periodo_escala ON periodo_escala.id = posto.periodo_escala_id
            JOIN igreja ON igreja.id = periodo_escala.igreja_id
            WHERE escalacao.pessoa_nome LIKE ? COLLATE NOCASE
            """.trimIndent(),
            arrayOf("%$trimmed%")
        )

        cursor.use { c ->
            while (c.moveToNext()) {
                val igreja = c.getString(0)
                val funcao = c.getString(1)
                val data = c.getString(2)
                val periodo = c.getString(3)

                val localDate = DateUtils.converterDataServico(data, periodo)
                val dataReal = localDate?.let { DateUtils.formatarISO(it.year, it.monthValue, it.dayOfMonth) }

                if (apenasFuturas && localDate != null && localDate.isBefore(hoje)) {
                    continue
                }

                list.add(
                    EscalacaoPessoa(
                        igreja = igreja,
                        funcao = funcao,
                        data = data,
                        periodo = periodo,
                        dataReal = dataReal,
                        localDate = localDate
                    )
                )
            }
        }

        // 2. Se a busca direta não retornar nada (ou para termos com acentos/typos como "joao" vs "João"),
        // busca todas as escalações e aplica normalização NFD e fuzzy search do SearchUtils
        if (list.isEmpty()) {
            val cursorAll = db.rawQuery(
                """
                SELECT
                    igreja.nome AS igreja,
                    posto.funcao AS funcao,
                    escalacao.data_servico AS data,
                    periodo_escala.referencia AS periodo,
                    escalacao.pessoa_nome AS pessoa
                FROM escalacao
                JOIN posto ON posto.id = escalacao.posto_id
                JOIN periodo_escala ON periodo_escala.id = posto.periodo_escala_id
                JOIN igreja ON igreja.id = periodo_escala.igreja_id
                """.trimIndent(),
                null
            )

            cursorAll.use { c ->
                while (c.moveToNext()) {
                    val pessoaNome = c.getString(4)
                    if (SearchUtils.correspondeBusca(pessoaNome, trimmed)) {
                        val igreja = c.getString(0)
                        val funcao = c.getString(1)
                        val data = c.getString(2)
                        val periodo = c.getString(3)

                        val localDate = DateUtils.converterDataServico(data, periodo)
                        val dataReal = localDate?.let { DateUtils.formatarISO(it.year, it.monthValue, it.dayOfMonth) }

                        if (apenasFuturas && localDate != null && localDate.isBefore(hoje)) {
                            continue
                        }

                        list.add(
                            EscalacaoPessoa(
                                igreja = igreja,
                                funcao = funcao,
                                data = data,
                                periodo = periodo,
                                dataReal = dataReal,
                                localDate = localDate
                            )
                        )
                    }
                }
            }
        }

        list.sortWith(compareBy({ it.dataReal == null }, { it.dataReal ?: "" }))
        return list
    }

    fun buscarEscalaDoDia(dataServico: String): List<EscalacaoDia> {
        val list = mutableListOf<EscalacaoDia>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT
                escalacao.id AS escalacao_id,
                igreja.nome AS igreja,
                posto.funcao AS funcao,
                escalacao.pessoa_nome AS pessoa
            FROM escalacao
            JOIN posto ON posto.id = escalacao.posto_id
            JOIN periodo_escala ON periodo_escala.id = posto.periodo_escala_id
            JOIN igreja ON igreja.id = periodo_escala.igreja_id
            WHERE escalacao.data_servico = ? COLLATE NOCASE
            ORDER BY igreja.nome, posto.ordem_na_planilha
            """.trimIndent(),
            arrayOf(dataServico)
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    EscalacaoDia(
                        escalacaoId = c.getLong(0),
                        igreja = c.getString(1),
                        funcao = c.getString(2),
                        pessoa = c.getString(3)
                    )
                )
            }
        }
        return list
    }

    fun listarDatasDisponiveis(): List<DataDisponivel> {
        val list = mutableListOf<DataDisponivel>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT DISTINCT escalacao.data_servico, periodo_escala.referencia
            FROM escalacao
            JOIN posto ON posto.id = escalacao.posto_id
            JOIN periodo_escala ON periodo_escala.id = posto.periodo_escala_id
            ORDER BY escalacao.data_servico
            """.trimIndent(),
            null
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val dataServico = c.getString(0)
                val periodo = c.getString(1)
                val localDate = DateUtils.converterDataServico(dataServico, periodo)
                val dataReal = localDate?.let { DateUtils.formatarISO(it.year, it.monthValue, it.dayOfMonth) }
                list.add(
                    DataDisponivel(
                        dataServico = dataServico,
                        periodo = periodo,
                        dataReal = dataReal,
                        localDate = localDate
                    )
                )
            }
        }
        list.sortWith(compareBy({ it.localDate == null }, { it.localDate }))
        return list
    }

    fun autenticarCoordenador(chaveAcesso: String): Coordenador? {
        val chaveLimpa = chaveAcesso.trim()
        if (chaveLimpa.isEmpty()) return null

        val db = readableDatabase

        // Fallback for DEMO01
        if (chaveLimpa.equals("DEMO01", ignoreCase = true)) {
            val todasIgrejas = listarIgrejas()
            return Coordenador(
                id = 999L,
                nome = "Coordenador Demonstração",
                chaveAcesso = "DEMO01",
                igrejas = todasIgrejas
            )
        }

        var coordenador: Coordenador? = null
        val cursor = db.rawQuery(
            "SELECT id, nome, chave_acesso FROM coordenador WHERE chave_acesso = ? COLLATE NOCASE",
            arrayOf(chaveLimpa)
        )
        cursor.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(0)
                val nome = c.getString(1)
                val chave = c.getString(2)

                // Obter igrejas
                val igrejas = mutableListOf<Igreja>()
                val cursorIgrejas = db.rawQuery(
                    """
                    SELECT igreja.id, igreja.nome, igreja.titulo_escala, igreja.coordenadores
                    FROM coordenador_igreja
                    JOIN igreja ON igreja.id = coordenador_igreja.igreja_id
                    WHERE coordenador_igreja.coordenador_id = ?
                    ORDER BY igreja.nome
                    """.trimIndent(),
                    arrayOf(id.toString())
                )
                cursorIgrejas.use { ci ->
                    while (ci.moveToNext()) {
                        igrejas.add(
                            Igreja(
                                id = ci.getLong(0),
                                nome = ci.getString(1),
                                tituloEscala = if (ci.isNull(2)) null else ci.getString(2),
                                coordenadores = if (ci.isNull(3)) null else ci.getString(3)
                            )
                        )
                    }
                }

                coordenador = Coordenador(
                    id = id,
                    nome = nome,
                    chaveAcesso = chave,
                    igrejas = if (igrejas.isNotEmpty()) igrejas else listarIgrejas()
                )
            }
        }
        return coordenador
    }

    fun listarPostosDaIgreja(igrejaId: Long, apenasFuturas: Boolean = false): List<DiaComPostos> {
        val db = readableDatabase
        var periodoReferencia = "Setembro de 2026"
        val cursorPeriodo = db.rawQuery(
            """
            SELECT id, referencia FROM periodo_escala
            WHERE igreja_id = ?
            ORDER BY importado_em DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(igrejaId.toString())
        )
        var periodoId: Long = -1
        cursorPeriodo.use { cp ->
            if (cp.moveToFirst()) {
                periodoId = cp.getLong(0)
                periodoReferencia = cp.getString(1)
            }
        }

        if (periodoId == -1L) return emptyList()

        val cursor = db.rawQuery(
            """
            SELECT escalacao.id, escalacao.data_servico, escalacao.pessoa_nome, posto.funcao
            FROM escalacao
            JOIN posto ON posto.id = escalacao.posto_id
            WHERE posto.periodo_escala_id = ?
            ORDER BY posto.ordem_na_planilha
            """.trimIndent(),
            arrayOf(periodoId.toString())
        )

        val hoje = LocalDate.now()
        val mapaDias = linkedMapOf<String, MutableList<PostoItem>>()
        val mapaDatasReais = mutableMapOf<String, Pair<String?, LocalDate?>>()

        cursor.use { c ->
            while (c.moveToNext()) {
                val escalacaoId = c.getLong(0)
                val dataTexto = c.getString(1)
                val pessoaNome = c.getString(2)
                val funcao = c.getString(3)

                if (!mapaDatasReais.containsKey(dataTexto)) {
                    val localDate = DateUtils.converterDataServico(dataTexto, periodoReferencia)
                    val dataReal = localDate?.let { DateUtils.formatarISO(it.year, it.monthValue, it.dayOfMonth) }
                    mapaDatasReais[dataTexto] = Pair(dataReal, localDate)
                }

                val (_, localDate) = mapaDatasReais[dataTexto] ?: Pair(null, null)
                if (apenasFuturas && localDate != null && localDate.isBefore(hoje)) {
                    continue
                }

                val list = mapaDias.getOrPut(dataTexto) { mutableListOf() }
                list.add(PostoItem(escalacaoId, funcao, pessoaNome))
            }
        }

        val resultado = mapaDias.map { (dataTexto, postos) ->
            val (dataReal, localDate) = mapaDatasReais[dataTexto] ?: Pair(null, null)
            DiaComPostos(
                dataServico = dataTexto,
                dataReal = dataReal,
                localDate = localDate,
                postos = postos
            )
        }.toMutableList()

        resultado.sortWith(compareBy({ it.localDate == null }, { it.localDate }))
        return resultado
    }

    fun atualizarEscalacao(escalacaoId: Long, novoNome: String): Boolean {
        val trimmed = novoNome.trim()
        if (trimmed.isEmpty()) return false

        val db = writableDatabase
        val cv = ContentValues().apply {
            put("pessoa_nome", trimmed)
        }
        val rows = db.update("escalacao", cv, "id = ?", arrayOf(escalacaoId.toString()))
        return rows > 0
    }

    fun listarPeriodos(): List<String> {
        val list = mutableListOf<String>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT DISTINCT referencia FROM periodo_escala ORDER BY importado_em DESC",
            null
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(c.getString(0))
            }
        }
        return list
    }

    fun importarEscalaCompleta(resultado: ParsedEscala): ImportResult {
        val db = writableDatabase
        db.beginTransaction()
        try {
            var totalEscalacoes = 0
            var totalIgrejas = 0
            var totalPostos = 0

            for (igrejaParsed in resultado.igrejas) {
                // 1. Obter ou criar igreja
                var igrejaId: Long = -1
                val cursorIgreja = db.rawQuery(
                    "SELECT id FROM igreja WHERE nome LIKE ? COLLATE NOCASE",
                    arrayOf("%${igrejaParsed.nome.trim()}%")
                )
                cursorIgreja.use { c ->
                    if (c.moveToFirst()) {
                        igrejaId = c.getLong(0)
                        val cvUpdate = ContentValues().apply {
                            igrejaParsed.titulo?.let { put("titulo_escala", it) }
                            igrejaParsed.coordenadores?.let { put("coordenadores", it) }
                        }
                        if (cvUpdate.size() > 0) {
                            db.update("igreja", cvUpdate, "id = ?", arrayOf(igrejaId.toString()))
                        }
                    }
                }

                if (igrejaId == -1L) {
                    val cvIgreja = ContentValues().apply {
                        put("nome", igrejaParsed.nome.trim())
                        put("titulo_escala", igrejaParsed.titulo)
                        put("coordenadores", igrejaParsed.coordenadores)
                    }
                    igrejaId = db.insert("igreja", null, cvIgreja)
                }

                if (igrejaId <= 0) continue
                totalIgrejas++

                // 2. Obter ou criar periodo_escala
                var periodoId: Long = -1
                val cursorPeriodo = db.rawQuery(
                    "SELECT id FROM periodo_escala WHERE igreja_id = ? AND referencia = ?",
                    arrayOf(igrejaId.toString(), resultado.periodo)
                )
                cursorPeriodo.use { cp ->
                    if (cp.moveToFirst()) {
                        periodoId = cp.getLong(0)
                        // Limpa postos anteriores deste período para re-importação limpa
                        db.delete("posto", "periodo_escala_id = ?", arrayOf(periodoId.toString()))
                    }
                }

                val nowIso = java.time.LocalDateTime.now().toString()
                if (periodoId == -1L) {
                    val cvPeriodo = ContentValues().apply {
                        put("igreja_id", igrejaId)
                        put("referencia", resultado.periodo)
                        put("arquivo_origem", resultado.arquivoOrigem)
                        put("importado_em", nowIso)
                    }
                    periodoId = db.insert("periodo_escala", null, cvPeriodo)
                } else {
                    val cvPeriodo = ContentValues().apply {
                        put("arquivo_origem", resultado.arquivoOrigem)
                        put("importado_em", nowIso)
                    }
                    db.update("periodo_escala", cvPeriodo, "id = ?", arrayOf(periodoId.toString()))
                }

                // 3. Inserir postos e escalações
                var ordem = 1
                for (postoParsed in igrejaParsed.postos) {
                    val cvPosto = ContentValues().apply {
                        put("periodo_escala_id", periodoId)
                        put("funcao", DateUtils.normalizarFuncao(postoParsed.funcao))
                        put("ordem_na_planilha", ordem++)
                    }
                    val postoId = db.insert("posto", null, cvPosto)
                    if (postoId <= 0) continue
                    totalPostos++

                    for (esc in postoParsed.escalacoes) {
                        val pessoa = esc.pessoa.trim()
                        if (pessoa.isEmpty()) continue
                        val cvEsc = ContentValues().apply {
                            put("posto_id", postoId)
                            put("data_servico", esc.data.trim())
                            put("pessoa_nome", pessoa)
                        }
                        db.insertWithOnConflict("escalacao", null, cvEsc, SQLiteDatabase.CONFLICT_REPLACE)
                        totalEscalacoes++
                    }
                }
            }

            db.setTransactionSuccessful()
            return ImportResult.Success(
                periodo = resultado.periodo,
                totalIgrejas = totalIgrejas,
                totalEscalacoes = totalEscalacoes,
                totalPostos = totalPostos
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao importar escala", e)
            return ImportResult.Error(e.message ?: "Erro desconhecido ao salvar escala no banco de dados.")
        } finally {
            db.endTransaction()
        }
    }
}
