package com.aistudio.escala.parser

import android.util.Log
import com.aistudio.escala.data.ParsedEscala
import com.aistudio.escala.data.ParsedEscalacao
import com.aistudio.escala.data.ParsedIgreja
import com.aistudio.escala.data.ParsedPosto
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

object XlsxScheduleParser {
    private const val TAG = "XlsxScheduleParser"

    fun parse(inputStream: InputStream, fileName: String): ParsedEscala {
        val zip = ZipInputStream(inputStream)
        val files = mutableMapOf<String, ByteArray>()

        var entry = zip.nextEntry
        while (entry != null) {
            val name = entry.name
            if (name.equals("xl/sharedStrings.xml", ignoreCase = true) ||
                name.equals("xl/workbook.xml", ignoreCase = true) ||
                name.startsWith("xl/worksheets/sheet", ignoreCase = true)
            ) {
                files[name.lowercase()] = zip.readBytes()
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }

        // 1. Shared Strings
        val sharedStrings = parseSharedStrings(files["xl/sharedstrings.xml"])

        // 2. Sheet names from workbook
        val sheetNames = parseWorkbook(files["xl/workbook.xml"])

        // 3. Parse each sheet
        val igrejas = mutableListOf<ParsedIgreja>()
        var detectedPeriod: String? = null

        val availableSheets = files.keys.filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
            .sortedBy { key ->
                val numStr = key.substringAfter("sheet").substringBefore(".xml")
                numStr.toIntOrNull() ?: 0
            }

        availableSheets.forEachIndexed { index, sheetPath ->
            val sheetName = sheetNames.getOrNull(index) ?: "Igreja ${index + 1}"
            val sheetBytes = files[sheetPath] ?: return@forEachIndexed

            try {
                val parsedIgreja = parseSheet(sheetBytes, sheetName, sharedStrings)
                if (parsedIgreja != null && (parsedIgreja.postos.isNotEmpty() || parsedIgreja.datas.isNotEmpty())) {
                    igrejas.add(parsedIgreja)

                    if (detectedPeriod == null) {
                        detectedPeriod = extrairPeriodoDoTitulo(parsedIgreja.titulo ?: "")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao analisar aba $sheetName", e)
            }
        }

        val periodoFinal = detectedPeriod
            ?: extrairPeriodoDoTitulo(fileName)
            ?: "Escala ${java.time.LocalDate.now().monthValue}/${java.time.LocalDate.now().year}"

        return ParsedEscala(
            periodo = periodoFinal,
            arquivoOrigem = fileName,
            igrejas = igrejas
        )
    }

    private fun parseSharedStrings(bytes: ByteArray?): List<String> {
        if (bytes == null) return emptyList()
        val list = mutableListOf<String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

            var eventType = parser.eventType
            var inSi = false
            var inText = false
            val currentSiText = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (name.equals("si", ignoreCase = true)) {
                            inSi = true
                            currentSiText.clear()
                        } else if (name.equals("t", ignoreCase = true)) {
                            inText = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inSi && inText) {
                            currentSiText.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name.equals("t", ignoreCase = true)) {
                            inText = false
                        } else if (name.equals("si", ignoreCase = true)) {
                            inSi = false
                            list.add(currentSiText.toString())
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao ler sharedStrings.xml", e)
        }
        return list
    }

    private fun parseWorkbook(bytes: ByteArray?): List<String> {
        if (bytes == null) return emptyList()
        val list = mutableListOf<String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name.equals("sheet", ignoreCase = true)) {
                    val nameAttr = parser.getAttributeValue(null, "name")
                    if (!nameAttr.isNullOrBlank()) {
                        list.add(nameAttr)
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao ler workbook.xml", e)
        }
        return list
    }

    private fun parseSheet(
        bytes: ByteArray,
        sheetName: String,
        sharedStrings: List<String>
    ): ParsedIgreja? {
        val rowsMap = mutableMapOf<Int, MutableMap<String, String>>()

        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

        var eventType = parser.eventType
        var currentRowNum = 0
        var currentCellRef = ""
        var currentCellType = ""
        var currentCellValue = StringBuilder()
        var inValue = false
        var inInlineText = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name.equals("row", ignoreCase = true)) {
                        val rStr = parser.getAttributeValue(null, "r")
                        currentRowNum = rStr?.toIntOrNull() ?: (currentRowNum + 1)
                    } else if (name.equals("c", ignoreCase = true)) {
                        currentCellRef = parser.getAttributeValue(null, "r") ?: ""
                        currentCellType = parser.getAttributeValue(null, "t") ?: ""
                        currentCellValue.clear()
                    } else if (name.equals("v", ignoreCase = true)) {
                        inValue = true
                    } else if (name.equals("t", ignoreCase = true)) {
                        inInlineText = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inValue || inInlineText) {
                        currentCellValue.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name.equals("v", ignoreCase = true)) {
                        inValue = false
                    } else if (name.equals("t", ignoreCase = true)) {
                        inInlineText = false
                    } else if (name.equals("c", ignoreCase = true)) {
                        var textVal = currentCellValue.toString().trim()
                        if (currentCellType.equals("s", ignoreCase = true)) {
                            val idx = textVal.toIntOrNull()
                            if (idx != null && idx in sharedStrings.indices) {
                                textVal = sharedStrings[idx]
                            }
                        }

                        val colLetters = currentCellRef.filter { it.isLetter() }.uppercase()
                        if (colLetters.isNotEmpty() && textVal.isNotEmpty()) {
                            val row = rowsMap.getOrPut(currentRowNum) { mutableMapOf() }
                            row[colLetters] = textVal.trim()
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        if (rowsMap.isEmpty()) return null

        var titulo: String? = null
        var coordenadores: String? = null
        var headerRowNum: Int? = null
        var serviceCol: String = "B"

        // Search in top 8 rows
        val sortedRowNums = rowsMap.keys.sorted()
        for (rNum in sortedRowNums.filter { it <= 8 }) {
            val row = rowsMap[rNum] ?: continue
            for ((col, valText) in row) {
                val lower = valText.lowercase()
                if (lower.contains("escala") && titulo == null) {
                    titulo = valText.replace("\n", " ").trim()
                }
                if (lower.contains("coordenador") && coordenadores == null) {
                    coordenadores = valText.replace("\n", " ").trim()
                }
                if (lower in listOf("serviço", "servico", "função", "funcao", "posto")) {
                    headerRowNum = rNum
                    serviceCol = col
                }
            }
        }

        // Fallback for header row if "serviço" was not explicit
        if (headerRowNum == null) {
            for (rNum in sortedRowNums.filter { it <= 8 }) {
                val row = rowsMap[rNum] ?: continue
                val dayKeywords = listOf("domingo", "sábado", "sabado", "quarta", "sexta", "quinta", "segunda", "terça", "terca")
                val matchingDays = row.values.count { v -> dayKeywords.any { v.lowercase().contains(it) } }
                if (matchingDays >= 2) {
                    headerRowNum = rNum
                    serviceCol = if (row.containsKey("B")) "B" else "A"
                    break
                }
            }
        }

        // Date columns
        val dateCols = mutableMapOf<String, String>()
        if (headerRowNum != null && rowsMap.containsKey(headerRowNum)) {
            val headerRow = rowsMap[headerRowNum] ?: emptyMap()
            for ((col, valText) in headerRow) {
                if (col == serviceCol) continue

                val lower = valText.lowercase().trim()
                val isNumericOnly = valText.matches(Regex("^[0-9.]+$"))
                val isNo = lower == "nº" || lower == "no" || lower == "n"
                if (isNumericOnly || isNo) continue

                val hasDigit = valText.any { it.isDigit() }
                val hasDayWord = listOf("dom", "seg", "ter", "qua", "qui", "sex", "sáb", "sab").any { lower.contains(it) }

                if (hasDigit || hasDayWord) {
                    dateCols[col] = valText.replace("\n", " ").trim()
                }
            }
        }

        val sortedDateCols = dateCols.keys.sortedWith(Comparator { a, b ->
            colToIdx(a).compareTo(colToIdx(b))
        })
        val dateList = sortedDateCols.mapNotNull { dateCols[it] }

        val postos = mutableListOf<ParsedPosto>()
        if (headerRowNum != null) {
            for (rNum in sortedRowNums.filter { it > headerRowNum }) {
                val row = rowsMap[rNum] ?: continue
                val funcao = row[serviceCol]?.trim() ?: continue
                val lowerFuncao = funcao.lowercase()
                if (lowerFuncao.isEmpty() || lowerFuncao == "nº" || lowerFuncao == "no") continue

                val escalacoes = mutableListOf<ParsedEscalacao>()
                for (col in sortedDateCols) {
                    val pessoa = row[col]?.trim()
                    val dataServico = dateCols[col]
                    if (!pessoa.isNullOrBlank() && !dataServico.isNullOrBlank()) {
                        escalacoes.add(ParsedEscalacao(data = dataServico, pessoa = pessoa))
                    }
                }

                if (escalacoes.isNotEmpty()) {
                    postos.add(ParsedPosto(funcao = funcao, escalacoes = escalacoes))
                }
            }
        }

        return ParsedIgreja(
            nome = sheetName,
            titulo = titulo ?: "Escala de $sheetName",
            coordenadores = coordenadores,
            datas = dateList,
            postos = postos
        )
    }

    private fun colToIdx(colStr: String): Int {
        var idx = 0
        for (ch in colStr.uppercase()) {
            idx = idx * 26 + (ch - 'A' + 1)
        }
        return idx
    }

    private fun extrairPeriodoDoTitulo(texto: String): String? {
        val lower = texto.lowercase()
        val meses = listOf(
            "janeiro", "fevereiro", "março", "marco", "abril", "maio", "junho",
            "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"
        )
        val mesEncontrado = meses.firstOrNull { lower.contains(it) } ?: return null
        val mesFormatado = when (mesEncontrado) {
            "marco" -> "Março"
            else -> mesEncontrado.replaceFirstChar { it.uppercase() }
        }

        val anoMatch = Regex("20[2-3][0-9]").find(texto)
        val ano = anoMatch?.value ?: java.time.LocalDate.now().year.toString()

        return "$mesFormatado de $ano"
    }
}
