package com.aistudio.escala.parser

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.aistudio.escala.BuildConfig
import com.aistudio.escala.data.ParsedEscala
import com.aistudio.escala.data.ParsedEscalacao
import com.aistudio.escala.data.ParsedIgreja
import com.aistudio.escala.data.ParsedPosto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.InputStream
import java.util.concurrent.TimeUnit
import org.json.JSONObject

// --- Common Data Classes for Gemini API ---

@Serializable
data class GenerateContentRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
    val systemInstruction: GeminiContent? = null
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

@Serializable
data class GeminiInlineData(
    val mimeType: String,
    val data: String
)

@Serializable
data class GeminiGenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<GeminiCandidate>? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

object GeminiScheduleParser {
    private const val TAG = "GeminiScheduleParser"

    suspend fun parseDocumentWithGemini(
        context: Context,
        uri: Uri,
        fileName: String,
        mimeType: String
    ): ParsedEscala = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            throw IllegalStateException(
                "Chave da API Gemini não configurada. Configure o segredo GEMINI_API_KEY no painel de Secrets do AI Studio para processamento multimodal (PDF/Imagens)."
            )
        }

        // Read file bytes
        val fileBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Não foi possível ler o arquivo selecionado.")

        val base64Data = Base64.encodeToString(fileBytes, Base64.NO_WRAP)

        val resolvedMimeType = when {
            mimeType.contains("pdf", ignoreCase = true) || fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            mimeType.contains("png", ignoreCase = true) || fileName.endsWith(".png", ignoreCase = true) -> "image/png"
            mimeType.contains("jpeg", ignoreCase = true) || mimeType.contains("jpg", ignoreCase = true) ||
                    fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            mimeType.contains("webp", ignoreCase = true) -> "image/webp"
            mimeType.contains("csv", ignoreCase = true) || fileName.endsWith(".csv", ignoreCase = true) -> "text/csv"
            mimeType.contains("text", ignoreCase = true) || fileName.endsWith(".txt", ignoreCase = true) -> "text/plain"
            else -> mimeType.ifBlank { "application/pdf" }
        }

        val prompt = """
            Você é um assistente católico especializado em transcrever e estruturar escalas de coroinhas, acólitos e cerimoniários.
            Analise cuidadosamente o documento anexado (escala litúrgica).
            Extraia todas as informações organizadas por paróquia/igreja/comunidade, postos (funções como Missal, Cruz, Sineta, Credência, Liturgia, Acendimento Velas, etc.), as datas de serviço e os nomes de cada servidor escalado.
            
            Retorne ESTRITAMENTE em formato JSON com a seguinte estrutura:
            {
              "periodo": "Mês e Ano, ex: Outubro de 2026",
              "igrejas": [
                {
                  "igreja": "Nome da Igreja ou Comunidade (ex: São José, Perpétuo Socorro, Sagrado Coração)",
                  "titulo": "Título da escala encontrado no documento",
                  "coordenadores": "Nomes dos coordenadores se houver",
                  "datas": ["Domingo 04", "Domingo 11", ...],
                  "postos": [
                    {
                      "funcao": "Nome da Função/Posto (ex: Missal, Cruz, Sineta 1, Credência 1, etc.)",
                      "escalacoes": [
                        {
                          "data": "Data do serviço correspondente (ex: Domingo 04)",
                          "pessoa": "Nome da pessoa escalada"
                        }
                      ]
                    }
                  ]
                }
              ]
            }
            Não inclua marcações markdown ou texto fora do JSON.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(
                            inlineData = GeminiInlineData(
                                mimeType = resolvedMimeType,
                                data = base64Data
                            )
                        ),
                        GeminiPart(text = prompt)
                    )
                )
            ),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.1f
            )
        )

        val response = GeminiClient.service.generateContent(apiKey, request)
        val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("O modelo Gemini não retornou dados para este documento.")

        parseJsonToEscala(textResponse, fileName)
    }

    fun parseJsonToEscala(jsonText: String, fileName: String): ParsedEscala {
        // Clean possible markdown code fences if present
        val cleanJson = jsonText
            .replace(Regex("^```json\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("^```\\s*", RegexOption.MULTILINE), "")
            .trim()

        val root = JSONObject(cleanJson)
        val periodo = root.optString("periodo", "Escala do Mês")
        val igrejasJson = root.optJSONArray("igrejas") ?: org.json.JSONArray()

        val parsedIgrejas = mutableListOf<ParsedIgreja>()

        for (i in 0 until igrejasJson.length()) {
            val igObj = igrejasJson.getJSONObject(i)
            val nomeIgreja = igObj.optString("igreja", "Igreja ${i + 1}")
            val titulo = igObj.optString("titulo", null)
            val coordenadores = igObj.optString("coordenadores", null)

            val datasJson = igObj.optJSONArray("datas")
            val datasList = mutableListOf<String>()
            if (datasJson != null) {
                for (d in 0 until datasJson.length()) {
                    datasList.add(datasJson.getString(d))
                }
            }

            val postosJson = igObj.optJSONArray("postos")
            val postosList = mutableListOf<ParsedPosto>()
            if (postosJson != null) {
                for (p in 0 until postosJson.length()) {
                    val pObj = postosJson.getJSONObject(p)
                    val funcao = pObj.optString("funcao", "Serviço")
                    val escJson = pObj.optJSONArray("escalacoes")
                    val escList = mutableListOf<ParsedEscalacao>()
                    if (escJson != null) {
                        for (e in 0 until escJson.length()) {
                            val eObj = escJson.getJSONObject(e)
                            val dt = eObj.optString("data", "")
                            val ps = eObj.optString("pessoa", "")
                            if (dt.isNotBlank() && ps.isNotBlank()) {
                                escList.add(ParsedEscalacao(data = dt, pessoa = ps))
                            }
                        }
                    }
                    if (escList.isNotEmpty()) {
                        postosList.add(ParsedPosto(funcao = funcao, escalacoes = escList))
                    }
                }
            }

            parsedIgrejas.add(
                ParsedIgreja(
                    nome = nomeIgreja,
                    titulo = titulo,
                    coordenadores = coordenadores,
                    datas = datasList,
                    postos = postosList
                )
            )
        }

        return ParsedEscala(
            periodo = periodo,
            arquivoOrigem = fileName,
            igrejas = parsedIgrejas
        )
    }
}
