package com.aistudio.escala.data

import java.time.LocalDate

data class Igreja(
    val id: Long,
    val nome: String,
    val tituloEscala: String?,
    val coordenadores: String?
)

data class EscalacaoPessoa(
    val igreja: String,
    val funcao: String,
    val data: String,
    val periodo: String,
    val dataReal: String?, // ISO YYYY-MM-DD
    val localDate: LocalDate?
)

data class EscalacaoDia(
    val escalacaoId: Long,
    val igreja: String,
    val funcao: String,
    val pessoa: String
)

data class DataDisponivel(
    val dataServico: String,
    val periodo: String,
    val dataReal: String?, // ISO YYYY-MM-DD
    val localDate: LocalDate?
)

data class PostoItem(
    val escalacaoId: Long,
    val funcao: String,
    val pessoaNome: String
)

data class DiaComPostos(
    val dataServico: String,
    val dataReal: String?,
    val localDate: LocalDate?,
    val postos: List<PostoItem>
)

data class Coordenador(
    val id: Long,
    val nome: String,
    val chaveAcesso: String,
    val igrejas: List<Igreja>
)

data class ParsedEscala(
    val periodo: String,
    val arquivoOrigem: String,
    val igrejas: List<ParsedIgreja>
)

data class ParsedIgreja(
    val nome: String,
    val titulo: String?,
    val coordenadores: String?,
    val datas: List<String>,
    val postos: List<ParsedPosto>
)

data class ParsedPosto(
    val funcao: String,
    val escalacoes: List<ParsedEscalacao>
)

data class ParsedEscalacao(
    val data: String,
    val pessoa: String
)

sealed class ImportResult {
    data class Success(
        val periodo: String,
        val totalIgrejas: Int,
        val totalEscalacoes: Int,
        val totalPostos: Int
    ) : ImportResult()

    data class Error(val message: String) : ImportResult()
}
