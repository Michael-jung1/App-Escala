package com.aistudio.escala.util

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Utilitários para busca resiliente, normalizada e tolerante a erros (fuzzy matching).
 */
object SearchUtils {

    /**
     * Remove acentos e caracteres diacríticos, além de converter para minúsculas.
     * Equivalente funcional de: .normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase()
     */
    fun normalizarTexto(texto: String?): String {
        if (texto.isNullOrBlank()) return ""
        val normalized = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
        return normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase(Locale.ROOT)
            .trim()
    }

    /**
     * Calcula a distância de Levenshtein entre duas strings normalizadas.
     */
    fun calcularDistanciaLevenshtein(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        var prev = IntArray(len2 + 1) { it }
        var curr = IntArray(len2 + 1)

        for (i in 1..len1) {
            curr[0] = i
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                curr[j] = min(
                    min(curr[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + cost
                )
            }
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[len2]
    }

    /**
     * Verifica se o candidato atende a busca considerando:
     * 1. Substring direta (ignora acentos e maiúsculas/minúsculas)
     * 2. Correspondência por palavras (ex: "michael" corresponde a "Michael Jung" ou "Jung Michael")
     * 3. Tolerância a erros (fuzzy): perdoa typos e letras omitidas/trocadas.
     *    - Para termos curtos (3 a 5 chars): tolera até 1 caractere de diferença.
     *    - Para termos mais longos (>= 6 chars): tolera até 2 caracteres de diferença.
     */
    fun correspondeBusca(candidato: String?, termoBusca: String?): Boolean {
        val normCandidato = normalizarTexto(candidato)
        val normTermo = normalizarTexto(termoBusca)

        if (normTermo.isEmpty()) return true
        if (normCandidato.isEmpty()) return false

        // 1. Substring rápida (exato após normalização NFD)
        if (normCandidato.contains(normTermo)) return true

        // 2. Substrings reversas ou palavras isoladas
        val palavrasCandidato = normCandidato.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val palavrasTermo = normTermo.split("\\s+".toRegex()).filter { it.isNotBlank() }

        // Se todas as palavras do termo encontram match (substring ou fuzzy) em alguma palavra do candidato
        val todasPalavrasCasam = palavrasTermo.all { termoPart ->
            palavrasCandidato.any { candPart ->
                if (candPart.contains(termoPart) || termoPart.contains(candPart)) {
                    true
                } else {
                    val maxErros = when {
                        termoPart.length >= 6 -> 2
                        termoPart.length >= 3 -> 1
                        else -> 0
                    }
                    if (maxErros > 0 && kotlin.math.abs(candPart.length - termoPart.length) <= maxErros) {
                        calcularDistanciaLevenshtein(candPart, termoPart) <= maxErros
                    } else {
                        false
                    }
                }
            }
        }
        if (todasPalavrasCasam) return true

        // 3. Fuzzy na string completa se o tamanho for aproximado
        val maxErrosGerais = when {
            normTermo.length >= 6 -> 2
            normTermo.length >= 3 -> 1
            else -> 0
        }
        if (maxErrosGerais > 0) {
            // Verifica contra cada palavra do candidato ou início
            for (palavra in palavrasCandidato) {
                if (kotlin.math.abs(palavra.length - normTermo.length) <= maxErrosGerais) {
                    if (calcularDistanciaLevenshtein(palavra, normTermo) <= maxErrosGerais) {
                        return true
                    }
                }
            }
        }

        return false
    }

    /**
     * Encontra a melhor correspondência exata ou fuzzy em uma lista de pessoas.
     * Retorna o nome cadastrado real se houver correspondência com pontuação alta.
     */
    fun encontrarMelhorPessoa(listaPessoas: List<String>, termoBusca: String): String? {
        val normTermo = normalizarTexto(termoBusca)
        if (normTermo.isEmpty()) return null

        // Primeiro tenta match exato normalizado
        val exato = listaPessoas.firstOrNull { normalizarTexto(it) == normTermo }
        if (exato != null) return exato

        // Segundo tenta match por substring completa
        val porSubstring = listaPessoas.firstOrNull { normalizarTexto(it).contains(normTermo) }
        if (porSubstring != null) return porSubstring

        // Terceiro tenta por fuzzy
        val matches = listaPessoas.filter { correspondeBusca(it, termoBusca) }
        if (matches.isNotEmpty()) {
            // Ordena pela menor distância de Levenshtein
            return matches.minByOrNull {
                calcularDistanciaLevenshtein(normalizarTexto(it), normTermo)
            }
        }

        return null
    }

    /**
     * Filtra e ranqueia sugestões para o autocomplete com ordenação por relevância.
     */
    fun filtrarSugestoes(listaPessoas: List<String>, termoBusca: String, limite: Int = 8): List<String> {
        val normTermo = normalizarTexto(termoBusca)
        if (normTermo.length < 2) return emptyList()

        return listaPessoas
            .filter { correspondeBusca(it, normTermo) }
            .sortedWith(
                compareBy<String> { nome ->
                    val normNome = normalizarTexto(nome)
                    // Prioridade 1: Inicia com o termo
                    if (normNome.startsWith(normTermo)) 0
                    // Prioridade 2: Contém o termo
                    else if (normNome.contains(normTermo)) 1
                    // Prioridade 3: Palavra interna inicia com o termo
                    else if (normNome.split(" ").any { it.startsWith(normTermo) }) 2
                    // Prioridade 4: Fuzzy
                    else 3
                }.thenBy { nome ->
                    calcularDistanciaLevenshtein(normalizarTexto(nome), normTermo)
                }
            )
            .take(limite)
    }
}
