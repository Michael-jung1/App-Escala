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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.aistudio.escala.data.DataDisponivel
import com.aistudio.escala.data.EscalacaoDia
import com.aistudio.escala.data.EscalaRepository
import com.aistudio.escala.ui.components.CalendarioMensal
import com.aistudio.escala.ui.components.PastBadge
import com.aistudio.escala.ui.components.SearchBarWithAutocomplete
import com.aistudio.escala.util.DateUtils
import com.aistudio.escala.util.PreferencesManager
import com.aistudio.escala.util.SearchUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EscalaDoDiaScreen(
    repository: EscalaRepository,
    preferencesManager: PreferencesManager,
    modifier: Modifier = Modifier,
    userFilter: String = "",
    onUserFilterChanged: (String) -> Unit = {},
    onClearUserFilter: () -> Unit = {},
    dataVersion: Int = 0
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val today = remember { LocalDate.now() }

    val activeUser = userFilter.ifBlank { preferencesManager.nomeUsuario ?: "" }.trim()
    var searchInput by remember { mutableStateOf(activeUser) }
    var allPeople by remember { mutableStateOf<List<String>>(emptyList()) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSuggestionsOpen by remember { mutableStateOf(false) }

    var availableDates by remember { mutableStateOf<List<DataDisponivel>>(emptyList()) }
    var serviceDatesSet by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    var userSpecificDates by remember { mutableStateOf<Set<LocalDate>?>(null) }
    var selectedLocalDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedDateServico by remember { mutableStateOf<String>("") }

    var churches by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedChurchFilter by remember { mutableStateOf("Todas as igrejas") }
    var isFilterMenuExpanded by remember { mutableStateOf(false) }

    var dayDuties by remember { mutableStateOf<List<EscalacaoDia>?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var collapsedChurches by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Synchronize local search input when activeUser changes externally
    LaunchedEffect(activeUser) {
        if (searchInput != activeUser) {
            searchInput = activeUser
        }
    }

    // Load registered names for autocomplete
    LaunchedEffect(Unit) {
        allPeople = repository.getPessoas()
    }

    // Debounce search input: automatically filters calendar & duty list without lagging typing
    LaunchedEffect(searchInput) {
        val query = searchInput.trim()
        if (query.length >= 2) {
            delay(300L)
            if (query != activeUser) {
                val resolved = SearchUtils.encontrarMelhorPessoa(allPeople, query) ?: query
                onUserFilterChanged(resolved)
            }
        } else if (query.isEmpty() && activeUser.isNotEmpty()) {
            onClearUserFilter()
        }
    }

    fun loadScheduleForDate(dateServico: String) {
        isLoading = true
        coroutineScope.launch {
            val duties = repository.getEscalaDoDia(dateServico)
            dayDuties = duties
            isLoading = false
        }
    }

    fun applyUserFilter(name: String) {
        val clean = name.trim()
        val resolved = SearchUtils.encontrarMelhorPessoa(allPeople, clean) ?: clean
        searchInput = resolved
        isSuggestionsOpen = false
        focusManager.clearFocus()
        onUserFilterChanged(resolved)
    }

    fun clearFilter() {
        searchInput = ""
        isSuggestionsOpen = false
        focusManager.clearFocus()
        onClearUserFilter()
    }

    // React whenever activeUser or dataVersion updates
    LaunchedEffect(activeUser, dataVersion) {
        val igrejas = repository.getIgrejas().map { it.nome }
        churches = listOf("Todas as igrejas") + igrejas

        val dates = repository.getDatasDisponiveis()
        availableDates = dates
        val allLocalDates = dates.mapNotNull { it.localDate }.toSet()

        if (activeUser.isNotBlank()) {
            val userDuties = repository.getEscalaPessoa(activeUser, apenasFuturas = false)
            val userDates = userDuties.mapNotNull { it.localDate }.toSet()
            userSpecificDates = userDates
            // Rule 1: Calendar highlights ONLY the days this specific person is scheduled!
            serviceDatesSet = userDates

            // Auto-select their earliest upcoming date, or first date overall if not already on one of their days
            val currentSelected = selectedLocalDate
            if (userDates.isNotEmpty() && (currentSelected == null || !userDates.contains(currentSelected))) {
                val nextDate = userDates.filter { it >= today }.minOrNull() ?: userDates.minOrNull()
                if (nextDate != null) {
                    selectedLocalDate = nextDate
                    val match = dates.firstOrNull { it.localDate == nextDate }
                    val ds = match?.dataServico ?: ""
                    selectedDateServico = ds
                    loadScheduleForDate(ds)
                    return@LaunchedEffect
                }
            }
        } else {
            userSpecificDates = null
            // All dates with scale
            serviceDatesSet = allLocalDates
        }

        val initialDateObj = selectedLocalDate ?: dates.firstOrNull { it.localDate != null }?.localDate ?: LocalDate.of(2026, 9, 6)
        selectedLocalDate = initialDateObj
        val match = dates.firstOrNull { it.localDate == initialDateObj }
        val dateServico = match?.dataServico ?: "Domingo 06"
        selectedDateServico = dateServico
        loadScheduleForDate(dateServico)
    }

    val isSelectedDatePast = selectedLocalDate?.let { DateUtils.isDataPassada(it, today) } ?: false
    val isSelectedDateToday = selectedLocalDate == today

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Escala do dia",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Quem serve nesse dia",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            )
        }

        // Search Bar by Name with Autocomplete
        item {
            SearchBarWithAutocomplete(
                value = searchInput,
                onValueChange = { searchInput = it },
                onSearch = { applyUserFilter(it) },
                onClear = { clearFilter() },
                allPeople = allPeople,
                placeholderText = "Filtrar calendário por nome (ex: Michael)",
                showSearchButton = false,
                testTagInput = "input_filtro_calendario_nome"
            )
        }

        // Active user filter banner (Rule 1 indicator)
        if (activeUser.isNotBlank()) {
            item {
                val dateCount = userSpecificDates?.size ?: 0
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("filter_user_active_banner"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Filtro ativo: $activeUser",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = if (dateCount > 0) {
                                        "Calendário destacando apenas os $dateCount dia(s) de $activeUser"
                                    } else {
                                        "Nenhuma escala encontrada para $activeUser"
                                    },
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                        IconButton(
                            onClick = { clearFilter() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remover filtro",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Monthly Interactive Calendar (Rule 1 & Rule 2 applied)
        item {
            CalendarioMensal(
                serviceDates = serviceDatesSet,
                selectedDate = selectedLocalDate,
                onSelectDate = { pickedDate ->
                    selectedLocalDate = pickedDate
                    val match = availableDates.firstOrNull { it.localDate == pickedDate }
                    if (match != null) {
                        selectedDateServico = match.dataServico
                        loadScheduleForDate(match.dataServico)
                    } else {
                        selectedDateServico = ""
                        dayDuties = emptyList()
                    }
                }
            )
        }

        // Date Status Indicator Banner (Rule 2: visual distinction between Past, Today, and Future)
        item {
            val dataFormatada = selectedLocalDate?.let { DateUtils.formatarDataExtenso(it) } ?: selectedDateServico
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dataFormatada,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelectedDatePast) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )

                when {
                    isSelectedDateToday -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Hoje",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    isSelectedDatePast -> {
                        // Rule 2: Past scales lose visual highlight and get neutral/muted styling
                        PastBadge(showIcon = true, text = "Escala passada")
                    }
                }
            }
        }

        // Church Filter Dropdown (only when viewing all persons, not when filtered strictly by person)
        if (activeUser.isBlank()) {
            item {
                ExposedDropdownMenuBox(
                    expanded = isFilterMenuExpanded,
                    onExpandedChange = { isFilterMenuExpanded = !isFilterMenuExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedChurchFilter,
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
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = isFilterMenuExpanded)
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
                            .testTag("church_filter_dropdown")
                    )

                    ExposedDropdownMenu(
                        expanded = isFilterMenuExpanded,
                        onDismissRequest = { isFilterMenuExpanded = false }
                    ) {
                        churches.forEach { church ->
                            DropdownMenuItem(
                                text = { Text(church) },
                                onClick = {
                                    selectedChurchFilter = church
                                    isFilterMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Schedule by Church Listing (Strict Rule 1 & Rule 2 Enforcement)
        if (isLoading) {
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
        } else {
            val duties = dayDuties ?: emptyList()

            // Strict Rule 1: When user search is active, ONLY duties with searched name are shown.
            // If the searched user is not present on this day, ALL other scales on this day MUST be hidden completely.
            if (activeUser.isNotBlank()) {
                val matchingUserDuties = duties.filter {
                    SearchUtils.correspondeBusca(it.pessoa, activeUser)
                }

                if (matchingUserDuties.isEmpty()) {
                    // Searched person is NOT present on this day: hide ALL other duties, show notice
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("empty_user_schedule_day_card"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EventBusy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "$activeUser não está escalado(a) nesta data",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Nenhuma função atribuída a este nome em ${selectedDateServico}. As demais escalas do dia estão ocultas pelo filtro ativo.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                )

                                val userDates = userSpecificDates ?: emptySet()
                                val nextUserDate = userDates.filter { it >= today }.minOrNull() ?: userDates.minOrNull()
                                if (nextUserDate != null) {
                                    Button(
                                        onClick = {
                                            selectedLocalDate = nextUserDate
                                            val match = availableDates.firstOrNull { it.localDate == nextUserDate }
                                            if (match != null) {
                                                selectedDateServico = match.dataServico
                                                loadScheduleForDate(match.dataServico)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Ir para escala de $activeUser (${DateUtils.formatarDataExtenso(nextUserDate)})",
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Searched person IS present on this day:
                    // Group ONLY the churches containing their duties
                    val churchesWithUser = matchingUserDuties.groupBy { it.igreja }

                    items(churchesWithUser.keys.toList()) { churchName ->
                        val userPostsInChurch = churchesWithUser[churchName] ?: emptyList()
                        val isCollapsed = collapsedChurches.contains(churchName)

                        // Rule 2: Past scales lose visual emphasis (opacity 0.65f, neutral border)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isSelectedDatePast) Modifier.alpha(0.65f) else Modifier)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    1.dp,
                                    if (isSelectedDatePast) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Column {
                                // Accordion Header
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isSelectedDatePast) {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                            } else {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            }
                                        )
                                        .clickable {
                                            collapsedChurches = if (isCollapsed) {
                                                collapsedChurches - churchName
                                            } else {
                                                collapsedChurches + churchName
                                            }
                                        }
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Place,
                                            contentDescription = null,
                                            tint = if (isSelectedDatePast) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = churchName,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "${userPostsInChurch.size} ${if (userPostsInChurch.size == 1) "posto" else "postos"}",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Icon(
                                            imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                            contentDescription = if (isCollapsed) "Expandir" else "Recolher",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // Accordion Body: Exclusively show posts of the searched user
                                AnimatedVisibility(visible = !isCollapsed) {
                                    Column {
                                        userPostsInChurch.forEachIndexed { index, duty ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                                    .background(
                                                        if (isSelectedDatePast) {
                                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                                        } else {
                                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                                        },
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = DateUtils.normalizarFuncao(duty.funcao),
                                                        fontSize = 14.sp,
                                                        color = if (isSelectedDatePast) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "Escala de serviço",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                                    )
                                                }

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = duty.pessoa,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(
                                                                if (isSelectedDatePast) {
                                                                    MaterialTheme.colorScheme.outline
                                                                } else {
                                                                    MaterialTheme.colorScheme.primary
                                                                }
                                                            )
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "Você",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White
                                                        )
                                                    }
                                                }
                                            }

                                            if (index < userPostsInChurch.size - 1) {
                                                HorizontalDivider(
                                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                                    modifier = Modifier.padding(horizontal = 16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // No name filter active: show all duties of the day (respecting church dropdown)
                if (duties.isEmpty()) {
                    item {
                        Text(
                            text = "Nenhuma escalação encontrada para essa data.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                } else {
                    val groupedByChurch = duties.groupBy { it.igreja }
                    val filteredChurches = groupedByChurch.keys.filter { churchName ->
                        selectedChurchFilter == "Todas as igrejas" || churchName == selectedChurchFilter
                    }

                    if (filteredChurches.isEmpty()) {
                        item {
                            Text(
                                text = "Essa igreja não tem escalação nessa data.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    } else {
                        items(filteredChurches.toList()) { churchName ->
                            val churchDuties = groupedByChurch[churchName] ?: emptyList()
                            val isCollapsed = collapsedChurches.contains(churchName)

                            // Rule 2: Past scales lose visual emphasis
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (isSelectedDatePast) Modifier.alpha(0.65f) else Modifier)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(
                                        1.dp,
                                        if (isSelectedDatePast) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Column {
                                    // Accordion Header
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isSelectedDatePast) {
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                                } else {
                                                    Color.Transparent
                                                }
                                            )
                                            .clickable {
                                                collapsedChurches = if (isCollapsed) {
                                                    collapsedChurches - churchName
                                                } else {
                                                    collapsedChurches + churchName
                                                }
                                            }
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Place,
                                                contentDescription = null,
                                                tint = if (isSelectedDatePast) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = churchName,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "${churchDuties.size} ${if (churchDuties.size == 1) "posto" else "postos"}",
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Icon(
                                                imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                                contentDescription = if (isCollapsed) "Expandir" else "Recolher",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    // Accordion Body
                                    AnimatedVisibility(visible = !isCollapsed) {
                                        Column {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                            churchDuties.forEachIndexed { index, duty ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = DateUtils.normalizarFuncao(duty.funcao),
                                                        fontSize = 14.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontWeight = FontWeight.Normal
                                                    )

                                                    Text(
                                                        text = duty.pessoa,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                if (index < churchDuties.size - 1) {
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
