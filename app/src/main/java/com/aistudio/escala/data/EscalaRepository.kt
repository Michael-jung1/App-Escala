package com.aistudio.escala.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EscalaRepository(context: Context) {
    private val dbHelper = DatabaseHelper.getInstance(context)

    suspend fun getIgrejas(): List<Igreja> = withContext(Dispatchers.IO) {
        dbHelper.listarIgrejas()
    }

    suspend fun getPessoas(): List<String> = withContext(Dispatchers.IO) {
        dbHelper.listarPessoas()
    }

    suspend fun getEscalaPessoa(nome: String, apenasFuturas: Boolean = false): List<EscalacaoPessoa> = withContext(Dispatchers.IO) {
        dbHelper.buscarEscalaPessoa(nome, apenasFuturas)
    }

    suspend fun getEscalaDoDia(dataServico: String): List<EscalacaoDia> = withContext(Dispatchers.IO) {
        dbHelper.buscarEscalaDoDia(dataServico)
    }

    suspend fun getDatasDisponiveis(): List<DataDisponivel> = withContext(Dispatchers.IO) {
        dbHelper.listarDatasDisponiveis()
    }

    suspend fun loginCoordenador(chaveAcesso: String): Coordenador? = withContext(Dispatchers.IO) {
        dbHelper.autenticarCoordenador(chaveAcesso)
    }

    suspend fun getPostosDaIgreja(igrejaId: Long, apenasFuturas: Boolean): List<DiaComPostos> = withContext(Dispatchers.IO) {
        dbHelper.listarPostosDaIgreja(igrejaId, apenasFuturas)
    }

    suspend fun atualizarEscalacao(escalacaoId: Long, novoNome: String): Boolean = withContext(Dispatchers.IO) {
        dbHelper.atualizarEscalacao(escalacaoId, novoNome)
    }

    suspend fun getPeriodos(): List<String> = withContext(Dispatchers.IO) {
        dbHelper.listarPeriodos()
    }

    suspend fun importarEscala(resultado: ParsedEscala): ImportResult = withContext(Dispatchers.IO) {
        dbHelper.importarEscalaCompleta(resultado)
    }
}
