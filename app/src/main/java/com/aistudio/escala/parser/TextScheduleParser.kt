package com.aistudio.escala.parser

import android.content.Context
import android.net.Uri
import android.util.Log
import com.aistudio.escala.data.ParsedEscala
import com.aistudio.escala.data.ParsedEscalacao
import com.aistudio.escala.data.ParsedIgreja
import com.aistudio.escala.data.ParsedPosto
import java.io.BufferedReader
import java.io.InputStreamReader

object TextScheduleParser {
    private const val TAG = "TextScheduleParser"

    fun parse(context: Context, uri: Uri, fileName: String): ParsedEscala? {
        return try {
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
            } ?: return null

            val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) return null

            val delimiter = when {
                lines.any { it.contains(";") } -> ";"
                lines.any { it.contains("\t") } -> "\t"
                lines.any { it.contains(",") } -> ","
                else -> null
            }

            if (delimiter != null) {
                parseDelimited(lines, delimiter, fileName)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha no parse textual local de $fileName: ${e.message}")
            null
        }
    }

    private fun parseDelimited(lines: List<String>, delimiter: String, fileName: String): ParsedEscala? {
        val headerRow = lines.firstOrNull() ?: return null
        val headers = headerRow.split(delimiter).map { it.trim().trim('"', '\'') }
        if (headers.size < 2) return null

        // Detect church / period / date column
        var currentChurchName = "Paróquia / Matriz"
        var periodo = "Escala Mensal"
        val postosMap = mutableMapOf<String, MutableList<ParsedEscalacao>>()
        val datasList = mutableListOf<String>()

        val rowStartIdx = if (headers.any { it.equals("igreja", ignoreCase = true) || it.equals("posto", ignoreCase = true) || it.equals("funcao", ignoreCase = true) || it.equals("data", ignoreCase = true) }) 1 else 0

        // Format A: Posto, Data 1, Data 2, Data 3...
        if (headers.first().contains("posto", ignoreCase = true) || headers.first().contains("função", ignoreCase = true) || headers.first().contains("funcao", ignoreCase = true)) {
            val dateHeaders = headers.drop(1)
            datasList.addAll(dateHeaders)

            for (i in 1 until lines.size) {
                val row = lines[i].split(delimiter).map { it.trim().trim('"', '\'') }
                if (row.isEmpty() || row.first().isBlank()) continue
                val postoNome = row.first()
                val escalacoes = mutableListOf<ParsedEscalacao>()

                for (colIdx in 1 until row.size) {
                    val dateHeader = dateHeaders.getOrNull(colIdx - 1) ?: continue
                    val pessoaNome = row[colIdx]
                    if (pessoaNome.isNotBlank() && pessoaNome != "-" && !pessoaNome.equals("vago", ignoreCase = true)) {
                        escalacoes.add(ParsedEscalacao(data = dateHeader, pessoa = pessoaNome))
                    }
                }
                if (escalacoes.isNotEmpty()) {
                    postosMap[postoNome] = escalacoes
                }
            }
        } else {
            // Format B: Igreja, Posto/Função, Data, Pessoa
            val colIgreja = headers.indexOfFirst { it.contains("igreja", ignoreCase = true) || it.contains("comunidade", ignoreCase = true) }
            val colPosto = headers.indexOfFirst { it.contains("posto", ignoreCase = true) || it.contains("funcao", ignoreCase = true) || it.contains("função", ignoreCase = true) }
            val colData = headers.indexOfFirst { it.contains("data", ignoreCase = true) || it.contains("dia", ignoreCase = true) }
            val colPessoa = headers.indexOfFirst { it.contains("pessoa", ignoreCase = true) || it.contains("nome", ignoreCase = true) || it.contains("servidor", ignoreCase = true) || it.contains("acólito", ignoreCase = true) || it.contains("coroinha", ignoreCase = true) }

            if (colPosto != -1 && colData != -1 && colPessoa != -1) {
                for (i in 1 until lines.size) {
                    val row = lines[i].split(delimiter).map { it.trim().trim('"', '\'') }
                    if (row.size <= maxOf(colPosto, colData, colPessoa)) continue

                    if (colIgreja != -1 && row.size > colIgreja && row[colIgreja].isNotBlank()) {
                        currentChurchName = row[colIgreja]
                    }
                    val postoNome = row[colPosto]
                    val dataVal = row[colData]
                    val pessoaNome = row[colPessoa]

                    if (postoNome.isNotBlank() && dataVal.isNotBlank() && pessoaNome.isNotBlank()) {
                        if (!datasList.contains(dataVal)) datasList.add(dataVal)
                        val list = postosMap.getOrPut(postoNome) { mutableListOf() }
                        list.add(ParsedEscalacao(data = dataVal, pessoa = pessoaNome))
                    }
                }
            }
        }

        if (postosMap.isEmpty()) return null

        val parsedPostos = postosMap.map { (nome, escalacoes) ->
            ParsedPosto(funcao = nome, escalacoes = escalacoes)
        }

        return ParsedEscala(
            periodo = periodo,
            arquivoOrigem = fileName,
            igrejas = listOf(
                ParsedIgreja(
                    nome = currentChurchName,
                    titulo = "Escala Importada",
                    coordenadores = "",
                    datas = datasList,
                    postos = parsedPostos
                )
            )
        )
    }
}
