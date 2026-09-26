package com.aistudio.escala.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.aistudio.escala.util.CoordenadorLockoutManager
import com.aistudio.escala.util.SecurityUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.escala.data.Coordenador
import com.aistudio.escala.data.DiaComPostos
import com.aistudio.escala.data.EscalaRepository
import com.aistudio.escala.data.Igreja
import com.aistudio.escala.data.PostoItem
import com.aistudio.escala.ui.components.ImportarEscalaCard
import com.aistudio.escala.ui.theme.getErrorColor
import com.aistudio.escala.ui.theme.getSuccessColor
import com.aistudio.escala.util.DateUtils
import com.aistudio.escala.util.PreferencesManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoordenadorScreen(
    repository: EscalaRepository,
    preferencesManager: PreferencesManager,
    modifier: Modifier = Modifier,
    onDataUpdated: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val successColors = getSuccessColor(isDark)
    val errorColors = getErrorColor(isDark)

    val prefs = remember(context) { context.getSharedPreferences("escala_prefs", Context.MODE_PRIVATE) }
    val defaultPrefs = remember(context) { context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE) }

    // Regra 1: Variável de estado que inicia como falsa
    var isAuthorized by remember { mutableStateOf(false) }
    var isAdmin by remember { mutableStateOf(preferencesManager.isAdminCache) }

    // Estados da barreira de acesso / tela de bloqueio
    var lockPasswordInput by remember { mutableStateOf("") }
    var lockErrorMessage by remember { mutableStateOf<String?>(null) }
    var isOnline by remember { mutableStateOf(false) }

    // Rate limit e bloqueio contra força bruta persistido em memória
    var remainingSeconds by remember { mutableStateOf(CoordenadorLockoutManager.getRemainingSeconds()) }

    LaunchedEffect(remainingSeconds) {
        if (remainingSeconds > 0) {
            kotlinx.coroutines.delay(1000L)
            remainingSeconds = CoordenadorLockoutManager.getRemainingSeconds()
            if (remainingSeconds == 0 && lockErrorMessage?.contains("Bloqueado temporariamente") == true) {
                lockErrorMessage = null
            }
        }
    }

    // Estados do layout original do coordenador
    var coordinator by remember { mutableStateOf<Coordenador?>(null) }
    var accessKeyInput by remember { mutableStateOf(preferencesManager.coordenadorChave ?: "") }
    var loginError by remember { mutableStateOf<String?>(null) }
    var isLoggingIn by remember { mutableStateOf(false) }

    var selectedChurch by remember { mutableStateOf<Igreja?>(null) }
    var isChurchMenuExpanded by remember { mutableStateOf(false) }
    var showPastDays by remember { mutableStateOf(false) }

    var scheduleDays by remember { mutableStateOf<List<DiaComPostos>>(emptyList()) }
    var isLoadingSchedule by remember { mutableStateOf(false) }

    var editingPosto by remember { mutableStateOf<PostoItem?>(null) }
    var editedName by remember { mutableStateOf("") }
    var saveSuccessMessage by remember { mutableStateOf<String?>(null) }

    // Estados do Gerenciamento de Acessos (Apenas Administrador)
    var listaAcessos by remember { mutableStateOf<List<Coordenador>>(emptyList()) }
    var showAddAccessDialog by remember { mutableStateOf(false) }
    var editingAccessCoord by remember { mutableStateOf<Coordenador?>(null) }
    var accessNomeInput by remember { mutableStateOf("") }
    var accessChaveInput by remember { mutableStateOf("") }
    var accessIsAdminInput by remember { mutableStateOf(false) }
    var accessDialogError by remember { mutableStateOf<String?>(null) }

    fun carregarAcessos() {
        coroutineScope.launch {
            listaAcessos = repository.getCoordenadoresAcessos()
        }
    }

    // Função utilitária para verificar conexão com a internet
    fun checkInternetConnection(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    // Regra 2: Ao carregar a tela, verifique a conexão com a internet.
    // Se houver rede, busca o hash da senha atualizada no banco de dados e salva no EncryptedSharedPreferences (PreferencesManager).
    LaunchedEffect(Unit) {
        val hasNet = checkInternetConnection()
        isOnline = hasNet
        if (hasNet) {
            try {
                delay(300) // Simula latência de rede na busca
                val hashAtualizado = repository.getSenhaCoordenador()
                if (hashAtualizado.isNotBlank()) {
                    preferencesManager.senhaCoordCache = hashAtualizado
                }
            } catch (_: Exception) {
            }
        } else {
            // Em modo offline no primeiro uso, se não houver senha no cache ainda, garante o hash
            if (preferencesManager.senhaCoordCache == null) {
                val hashPadrao = repository.getSenhaCoordenador()
                if (hashPadrao.isNotBlank()) {
                    preferencesManager.senhaCoordCache = hashPadrao
                }
            }
        }

        // Carrega sessão salva caso exista
        val savedKey = preferencesManager.coordenadorChave
        if (!savedKey.isNullOrBlank()) {
            val coord = repository.loginCoordenador(savedKey)
            if (coord != null) {
                coordinator = coord
                isAdmin = coord.isAdmin
                preferencesManager.isAdminCache = coord.isAdmin
                selectedChurch = coord.igrejas.firstOrNull()
            }
        }
    }

    fun loadChurchSchedule(churchId: Long, past: Boolean) {
        isLoadingSchedule = true
        coroutineScope.launch {
            val days = repository.getPostosDaIgreja(churchId, apenasFuturas = !past)
            scheduleDays = days
            isLoadingSchedule = false
        }
    }

    LaunchedEffect(selectedChurch, showPastDays) {
        selectedChurch?.let { church ->
            loadChurchSchedule(church.id, showPastDays)
        }
    }

    LaunchedEffect(isAdmin, isAuthorized) {
        if (isAdmin && isAuthorized) {
            carregarAcessos()
        }
    }

    fun executeLogin(key: String) {
        if (CoordenadorLockoutManager.isLockedOut()) {
            val secs = CoordenadorLockoutManager.getRemainingSeconds()
            remainingSeconds = secs
            loginError = "Muitas tentativas inválidas. Aguarde $secs segundos para tentar novamente."
            return
        }

        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) return
        isLoggingIn = true
        loginError = null

        coroutineScope.launch {
            val result = repository.loginCoordenador(cleanKey)
            if (result != null) {
                CoordenadorLockoutManager.reset()
                remainingSeconds = 0
                coordinator = result
                isAdmin = result.isAdmin
                preferencesManager.isAdminCache = result.isAdmin
                prefs.edit().putBoolean("isAdmin_cache", result.isAdmin).apply()
                defaultPrefs.edit().putBoolean("isAdmin_cache", result.isAdmin).apply()
                selectedChurch = result.igrejas.firstOrNull()
                preferencesManager.coordenadorChave = cleanKey
                if (result.isAdmin) carregarAcessos()
            } else {
                CoordenadorLockoutManager.recordFailedAttempt()
                if (CoordenadorLockoutManager.isLockedOut()) {
                    val secs = CoordenadorLockoutManager.getRemainingSeconds()
                    remainingSeconds = secs
                    loginError = "Limite de tentativas excedido. Bloqueado temporariamente por $secs segundos."
                } else {
                    val restantes = CoordenadorLockoutManager.MAX_ATTEMPTS - CoordenadorLockoutManager.failedAttempts
                    loginError = "Chave de acesso inválida. Restam $restantes tentativa(s) antes do bloqueio temporário."
                }
            }
            isLoggingIn = false
        }
    }

    fun logout() {
        coordinator = null
        selectedChurch = null
        scheduleDays = emptyList()
        preferencesManager.limparSessaoCoordenador()
        // Volta a bloquear o acesso ao deslogar
        isAuthorized = false
        isAdmin = false
        preferencesManager.isAdminCache = false
        prefs.edit().putBoolean("isAdmin_cache", false).apply()
        defaultPrefs.edit().putBoolean("isAdmin_cache", false).apply()
        lockPasswordInput = ""
        lockErrorMessage = null
    }

    // Regra 4 & 5: Validação offline ao clicar em 'Entrar'
    // Compara a senha digitada com o cache seguro e aplica rate limit contra força bruta persistido em memória.
    fun validarSenhaOffline() {
        if (CoordenadorLockoutManager.isLockedOut()) {
            val secs = CoordenadorLockoutManager.getRemainingSeconds()
            remainingSeconds = secs
            lockErrorMessage = "Muitas tentativas inválidas. Aguarde $secs segundos para tentar novamente."
            return
        }

        val digitada = lockPasswordInput.trim()
        if (digitada.isEmpty()) {
            lockErrorMessage = "Por favor, digite a senha."
            return
        }

        // Lê o hash exclusivamente do EncryptedSharedPreferences (PreferencesManager)
        val hashSalvo = preferencesManager.senhaCoordCache ?: ""

        val coincides = SecurityUtils.verifyAccessCode(digitada, hashSalvo)

        if (coincides) {
            CoordenadorLockoutManager.reset()
            remainingSeconds = 0
            lockErrorMessage = null
            isAuthorized = true

            // Obtém isAdmin do SharedPreferences (offline) como fallback inicial
            val cachedIsAdmin = prefs.getBoolean("isAdmin_cache", false)
                ?: defaultPrefs.getBoolean("isAdmin_cache", false)
                ?: preferencesManager.isAdminCache
            isAdmin = cachedIsAdmin

            // Se a chave coincidir com um coordenador no banco de dados, efetua login imediato e atualiza isAdmin
            coroutineScope.launch {
                val validation = repository.validarSenha(digitada)
                if (validation.first) {
                    isAdmin = validation.second
                    preferencesManager.isAdminCache = validation.second
                    prefs.edit().putBoolean("isAdmin_cache", validation.second).apply()
                    defaultPrefs.edit().putBoolean("isAdmin_cache", validation.second).apply()
                }

                val coord = repository.loginCoordenador(digitada)
                if (coord != null) {
                    coordinator = coord
                    isAdmin = coord.isAdmin
                    preferencesManager.isAdminCache = coord.isAdmin
                    prefs.edit().putBoolean("isAdmin_cache", coord.isAdmin).apply()
                    defaultPrefs.edit().putBoolean("isAdmin_cache", coord.isAdmin).apply()
                    selectedChurch = coord.igrejas.firstOrNull()
                    preferencesManager.coordenadorChave = digitada
                    if (coord.isAdmin) carregarAcessos()
                }
            }
        } else {
            CoordenadorLockoutManager.recordFailedAttempt()
            if (CoordenadorLockoutManager.isLockedOut()) {
                val secs = CoordenadorLockoutManager.getRemainingSeconds()
                remainingSeconds = secs
                lockErrorMessage = "Limite de tentativas excedido. Bloqueado temporariamente por $secs segundos."
            } else {
                val restantes = CoordenadorLockoutManager.MAX_ATTEMPTS - CoordenadorLockoutManager.failedAttempts
                lockErrorMessage = "Senha incorreta. Restam $restantes tentativa(s) antes do bloqueio temporário."
            }
        }
    }

    fun saveEditedName() {
        val target = editingPosto ?: return
        val newName = editedName.trim()
        if (newName.isEmpty()) return

        coroutineScope.launch {
            val success = repository.atualizarEscalacao(target.escalacaoId, newName)
            if (success) {
                saveSuccessMessage = "Alteração salva com sucesso!"
                editingPosto = null
                // Refresh list
                selectedChurch?.let { loadChurchSchedule(it.id, showPastDays) }
                delay(3000)
                saveSuccessMessage = null
            }
        }
    }

    // Dialog for editing assignment
    if (editingPosto != null) {
        AlertDialog(
            onDismissRequest = { editingPosto = null },
            title = {
                Text(
                    text = "Editar escalação",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Medium
                )
            },
            text = {
                Column {
                    Text(
                        text = "Função: ${DateUtils.normalizarFuncao(editingPosto?.funcao ?: "")}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("Nome da pessoa") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_person_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { saveEditedName() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("save_person_name_button")
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingPosto = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo de Criar / Editar Acesso (Apenas Administrador)
    if (showAddAccessDialog || editingAccessCoord != null) {
        val isEditing = editingAccessCoord != null
        AlertDialog(
            onDismissRequest = {
                showAddAccessDialog = false
                editingAccessCoord = null
                accessDialogError = null
            },
            title = {
                Text(
                    text = if (isEditing) "Editar Acesso" else "Novo Acesso de Coordenador",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = accessNomeInput,
                        onValueChange = {
                            accessNomeInput = it
                            accessDialogError = null
                        },
                        label = { Text("Nome do Coordenador") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = accessChaveInput,
                        onValueChange = {
                            accessChaveInput = it.uppercase()
                            accessDialogError = null
                        },
                        label = { Text("Senha / Chave de Acesso") },
                        placeholder = { Text(if (isEditing) "Deixe em branco para manter a atual" else "Ex: COORD123") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Nível Administrador",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Pode gerenciar senhas e acessos",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = accessIsAdminInput,
                            onCheckedChange = { accessIsAdminInput = it }
                        )
                    }

                    if (accessDialogError != null) {
                        Text(
                            text = accessDialogError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val nome = accessNomeInput.trim()
                        val chave = accessChaveInput.trim()
                        if (nome.isEmpty() || (!isEditing && chave.isEmpty())) {
                            accessDialogError = if (isEditing) "Preencha o nome do coordenador." else "Preencha o nome e a chave de acesso."
                            return@Button
                        }
                        coroutineScope.launch {
                            val target = editingAccessCoord
                            if (target != null) {
                                val ok = repository.atualizarCoordenadorAcesso(target.id, nome, chave, accessIsAdminInput)
                                if (ok) {
                                    carregarAcessos()
                                    showAddAccessDialog = false
                                    editingAccessCoord = null
                                    accessDialogError = null
                                    saveSuccessMessage = "Acesso de \"$nome\" atualizado com sucesso!"
                                } else {
                                    accessDialogError = "Erro ao atualizar. Tente outra chave."
                                }
                            } else {
                                val newId = repository.salvarCoordenadorAcesso(nome, chave, accessIsAdminInput)
                                if (newId != null) {
                                    carregarAcessos()
                                    showAddAccessDialog = false
                                    editingAccessCoord = null
                                    accessDialogError = null
                                    saveSuccessMessage = "Coordenador \"$nome\" cadastrado com sucesso!"
                                } else {
                                    accessDialogError = "Chave de acesso já existente ou erro ao salvar."
                                }
                            }
                        }
                    }
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddAccessDialog = false
                        editingAccessCoord = null
                        accessDialogError = null
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Regra 3: Controle de interface: Se isAuthorized for falso, oculte o layout original da aba.
    // Mostre apenas uma tela de bloqueio contendo um campo de texto para digitar a senha e um botão 'Entrar'.
    if (!isAuthorized) {
        CoordenadorLockScreen(
            passwordInput = lockPasswordInput,
            onPasswordChange = {
                lockPasswordInput = it
                if (lockErrorMessage != null) lockErrorMessage = null
            },
            onLoginClick = { validarSenhaOffline() },
            errorMessage = lockErrorMessage,
            isOnline = isOnline,
            isDark = isDark,
            isLockedOut = remainingSeconds > 0,
            remainingSeconds = remainingSeconds,
            modifier = modifier
        )
    } else {
        // Regra 5: Layout original da aba Coordenador
        if (coordinator == null) {
            // --- LOGIN VIEW ---
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Painel do coordenador",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Entrar com a chave de acesso",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                OutlinedTextField(
                    value = accessKeyInput,
                    onValueChange = { accessKeyInput = it.uppercase() },
                    enabled = remainingSeconds <= 0,
                    placeholder = { Text(if (remainingSeconds > 0) "Aguarde o desbloqueio..." else "Código de acesso") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { executeLogin(accessKeyInput) }),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("coordinator_access_key_input")
                )

                if (loginError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(errorColors.background)
                            .border(1.dp, errorColors.border, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = loginError ?: "",
                            color = errorColors.text,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { executeLogin(accessKeyInput) },
                    enabled = accessKeyInput.isNotBlank() && !isLoggingIn && remainingSeconds <= 0,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("coordinator_login_button")
                ) {
                    if (isLoggingIn) {
                        CircularProgressIndicator(
                             modifier = Modifier.size(20.dp),
                             color = Color.White,
                             strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (remainingSeconds > 0) "Aguarde (${remainingSeconds}s)" else "Entrar",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "A chave de acesso é gerada e entregue pelo administrador do sistema.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        } else {
            // --- COORDINATOR DASHBOARD ---
            val coord = coordinator!!
            val firstName = coord.nome.split(" ").firstOrNull() ?: coord.nome

            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                text = "Painel do coordenador",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Olá, $firstName",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        OutlinedButton(
                            onClick = { logout() },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.testTag("coordinator_logout_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sair",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Church Selector & History Toggle
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (coord.igrejas.size > 1) {
                            ExposedDropdownMenuBox(
                                expanded = isChurchMenuExpanded,
                                onExpandedChange = { isChurchMenuExpanded = !isChurchMenuExpanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = selectedChurch?.nome ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Place,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isChurchMenuExpanded)
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                )

                                ExposedDropdownMenu(
                                    expanded = isChurchMenuExpanded,
                                    onDismissRequest = { isChurchMenuExpanded = false }
                                ) {
                                    coord.igrejas.forEach { church ->
                                        DropdownMenuItem(
                                            text = { Text(church.nome) },
                                            onClick = {
                                                selectedChurch = church
                                                isChurchMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = selectedChurch?.nome ?: "",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // History Toggle Button
                        OutlinedButton(
                            onClick = { showPastDays = !showPastDays },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (showPastDays) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (showPastDays) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            ),
                            modifier = Modifier.testTag("toggle_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = if (showPastDays) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showPastDays) "Ocultar passados" else "Ver passados",
                                fontSize = 12.sp,
                                color = if (showPastDays) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Seção 'Gerenciamento de Acessos' - Exclusiva para Administradores (if (isAdmin))
                if (isAdmin) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AdminPanelSettings,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "Gerenciamento de Acessos",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Apenas administradores podem ver e gerenciar senhas",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            accessNomeInput = ""
                                            accessChaveInput = ""
                                            accessIsAdminInput = false
                                            editingAccessCoord = null
                                            accessDialogError = null
                                            showAddAccessDialog = true
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Novo Coordenador",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                if (listaAcessos.isEmpty()) {
                                    Text(
                                        text = "Nenhum acesso cadastrado além do padrão.",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listaAcessos.forEach { itemCoord ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.surface)
                                                    .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(
                                                        imageVector = if (itemCoord.isAdmin) Icons.Default.Security else Icons.Default.Person,
                                                        contentDescription = null,
                                                        tint = if (itemCoord.isAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column {
                                                        Text(
                                                            text = itemCoord.nome,
                                                            fontWeight = FontWeight.Medium,
                                                            fontSize = 14.sp
                                                        )
                                                        Text(
                                                            text = "Chave: ${itemCoord.chaveAcesso} ${if (itemCoord.isAdmin) "• Administrador" else "• Coordenador"}",
                                                            fontSize = 12.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }

                                                Row {
                                                    IconButton(
                                                        onClick = {
                                                            editingAccessCoord = itemCoord
                                                            accessNomeInput = itemCoord.nome
                                                            accessChaveInput = ""
                                                            accessIsAdminInput = itemCoord.isAdmin
                                                            accessDialogError = null
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Edit,
                                                            contentDescription = "Editar",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }

                                                    // Impede excluir a si mesmo
                                                    if (itemCoord.id != coord.id) {
                                                        IconButton(
                                                            onClick = {
                                                                coroutineScope.launch {
                                                                    repository.excluirCoordenadorAcesso(itemCoord.id)
                                                                    carregarAcessos()
                                                                    saveSuccessMessage = "Acesso removido com sucesso!"
                                                                }
                                                            },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Delete,
                                                                contentDescription = "Excluir",
                                                                tint = MaterialTheme.colorScheme.error,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Import Escala Section
                item {
                    ImportarEscalaCard(
                        repository = repository,
                        onImportSuccess = { periodo ->
                            coroutineScope.launch {
                                saveSuccessMessage = "Escala de \"$periodo\" importada com sucesso!"
                                val updatedCoord = repository.loginCoordenador(coord.chaveAcesso)
                                if (updatedCoord != null) {
                                    coordinator = updatedCoord
                                    selectedChurch = updatedCoord.igrejas.firstOrNull { it.id == selectedChurch?.id }
                                        ?: updatedCoord.igrejas.firstOrNull()
                                }
                                selectedChurch?.let { loadChurchSchedule(it.id, showPastDays) }
                                onDataUpdated()
                            }
                        }
                    )
                }

                // Save Success Banner
                if (saveSuccessMessage != null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(successColors.background)
                                .border(1.dp, successColors.border, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = saveSuccessMessage ?: "",
                                color = successColors.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Schedule Cards by Day
                if (isLoadingSchedule) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else if (scheduleDays.isEmpty()) {
                    item {
                        Text(
                            text = if (showPastDays) "Nenhuma escalação encontrada." else "Nenhum dia futuro. Tente \"Ver passados\".",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                } else {
                    items(scheduleDays) { dia ->
                        val partes = dia.dataServico.split(" ")
                        val diaSemana = partes.getOrNull(0) ?: dia.dataServico
                        val numero = partes.getOrNull(1) ?: ""
                        val isPast = dia.dataReal?.let { DateUtils.isDataPassada(it) } ?: false

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isPast) Modifier.alpha(0.65f) else Modifier)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    1.dp,
                                    if (isPast) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Column {
                                // Header: Weekday + Day Number
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isPast) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
                                        )
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Text(
                                            text = diaSemana,
                                            fontFamily = FontFamily.Serif,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isPast) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = numero,
                                            fontSize = 14.sp,
                                            color = if (isPast) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }

                                    if (isPast) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.surface)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Passado",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                // Postos List with inline click to edit
                                dia.postos.forEachIndexed { index, posto ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                editingPosto = posto
                                                editedName = posto.pessoaNome
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = DateUtils.normalizarFuncao(posto.funcao),
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = posto.pessoaNome,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Editar ${posto.pessoaNome}",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    if (index < dia.postos.size - 1) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

/**
 * Tela de Bloqueio para a aba Coordenador.
 * Exibe apenas um campo de texto para a senha e um botão 'Entrar',
 * com suporte à visualização de status de conexão e mensagens de validação.
 */
@Composable
private fun CoordenadorLockScreen(
    passwordInput: String,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    errorMessage: String?,
    isOnline: Boolean,
    isDark: Boolean,
    isLockedOut: Boolean = false,
    remainingSeconds: Int = 0,
    modifier: Modifier = Modifier
) {
    var isPasswordVisible by remember { mutableStateOf(false) }
    val errorColors = getErrorColor(isDark)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .testTag("lock_screen_container"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Ícone de Cadeado / Segurança
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Bloqueio de segurança",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Área do Coordenador",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Acesso Restrito",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Digite a senha para desbloquear o painel de coordenação.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Indicador de Conexão / Cache
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isOnline) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                contentDescription = null,
                tint = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (isOnline) "Online • Senha sincronizada" else "Offline • Validação local ativa",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Campo de texto para digitar a senha
        OutlinedTextField(
            value = passwordInput,
            onValueChange = onPasswordChange,
            enabled = !isLockedOut,
            placeholder = { Text(if (isLockedOut) "Aguarde o desbloqueio..." else "Digite a senha") },
            label = { Text("Senha") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }, enabled = !isLockedOut) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isPasswordVisible) "Ocultar senha" else "Exibir senha",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { if (!isLockedOut) onLoginClick() }),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("lock_password_input")
        )

        // Banner de erro caso a validação falhe
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(errorColors.background)
                    .border(1.dp, errorColors.border, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = errorMessage,
                    color = errorColors.text,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Botão 'Entrar'
        Button(
            onClick = onLoginClick,
            enabled = !isLockedOut,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("lock_login_button")
        ) {
            Text(
                text = if (isLockedOut) "Aguarde (${remainingSeconds}s)" else "Entrar",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Validação segura offline via cache local.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )
    }
}
