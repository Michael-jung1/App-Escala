package com.aistudio.escala.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
    val coroutineScope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val successColors = getSuccessColor(isDark)
    val errorColors = getErrorColor(isDark)

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

    // Auto login if key was stored
    LaunchedEffect(Unit) {
        val savedKey = preferencesManager.coordenadorChave
        if (!savedKey.isNullOrBlank()) {
            val coord = repository.loginCoordenador(savedKey)
            if (coord != null) {
                coordinator = coord
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

    fun executeLogin(key: String) {
        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) return
        isLoggingIn = true
        loginError = null

        coroutineScope.launch {
            val result = repository.loginCoordenador(cleanKey)
            if (result != null) {
                coordinator = result
                selectedChurch = result.igrejas.firstOrNull()
                preferencesManager.coordenadorChave = cleanKey
            } else {
                loginError = "Chave de acesso inválida. Confira com quem te passou o código (ex: JFYLQP ou DEMO01)."
            }
            isLoggingIn = false
        }
    }

    fun logout() {
        coordinator = null
        selectedChurch = null
        scheduleDays = emptyList()
        preferencesManager.limparSessaoCoordenador()
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

    // Main Content
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
                placeholder = { Text("Código de acesso (ex: JFYLQP)") },
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
                enabled = accessKeyInput.isNotBlank() && !isLoggingIn,
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
                    Text("Entrar", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "A chave de acesso é gerada e entregue pelo administrador do sistema. (Dica: utilize JFYLQP ou DEMO01)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                        border = androidx.compose.foundation.BorderStroke(
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
