package com.example

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.DatePicker
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.example.data.Drawing
import com.example.data.HomeNote
import com.example.data.HomeTask
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen()
            }
        }
    }
}

enum class HomeTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    TASKS("Tasks", Icons.Default.CheckCircle),
    DRAWING("Drawing Pad", Icons.Default.Brush),
    CALENDAR("Calendar", Icons.Default.DateRange),
    ARCHIVE("Archive", Icons.Default.Archive)
}

enum class NotificationType {
    OVERDUE, APPROACHING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: HomeViewModel = viewModel()) {
    var activeTab by remember { mutableStateOf(HomeTab.TASKS) }
    
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    var showNotificationTray by remember { mutableStateOf(false) }
    var dismissedNotificationIds by remember { mutableStateOf(setOf<Int>()) }

    val approachingTasks = remember(tasks) {
        val now = System.currentTimeMillis()
        tasks.filter { !it.isCompleted && it.dueDate != null }.mapNotNull { task ->
            val diff = task.dueDate!! - now
            val type = when {
                diff < 0 -> NotificationType.OVERDUE
                diff < 24 * 60 * 60 * 1000 -> NotificationType.APPROACHING
                else -> null
            }
            if (type != null) task to type else null
        }
    }

    // Identify current banner task (first one in the approachingTasks list that hasn't been dismissed)
    val currentBannerItem: Pair<HomeTask, NotificationType>? = remember(approachingTasks, dismissedNotificationIds) {
        approachingTasks.firstOrNull { pair -> !dismissedNotificationIds.contains(pair.first.id) }
    }

    var showGithubSyncDialog by remember { mutableStateOf(false) }
    var showExportJsonDialog by remember { mutableStateOf(false) }
    var currentExportJsonString by remember { mutableStateOf("") }

    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    java.io.OutputStreamWriter(outputStream).use { writer ->
                        writer.write(currentExportJsonString)
                    }
                }
                android.widget.Toast.makeText(context, "Backup JSON file created successfully!", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Failed to create file: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    java.io.BufferedReader(java.io.InputStreamReader(inputStream)).use { reader ->
                        val jsonContent = reader.readText()
                        coroutineScope.launch {
                            val success = viewModel.importDataFromJson(jsonContent)
                            if (success) {
                                android.widget.Toast.makeText(context, "Data imported successfully!", android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                android.widget.Toast.makeText(context, "Error parsing backup JSON. Please check formatting.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Failed to read file: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showGithubSyncDialog) {
        GithubSyncDialog(
            viewModel = viewModel,
            onDismiss = { showGithubSyncDialog = false }
        )
    }

    if (showExportJsonDialog) {
        ExportJsonDialog(
            jsonString = currentExportJsonString,
            onDismiss = { showExportJsonDialog = false }
        )
    }

    if (showNotificationTray) {
        BrowserNotificationTrayDialog(
            approachingTasks = approachingTasks,
            onDismiss = { showNotificationTray = false },
            onComplete = { task ->
                viewModel.toggleTask(task)
            },
            onSnooze = { task ->
                val newDueDate = (task.dueDate ?: System.currentTimeMillis()) + 3600000
                viewModel.updateTask(task.copy(dueDate = newDueDate))
            },
            onClearAll = {
                dismissedNotificationIds = dismissedNotificationIds.toMutableSet().apply {
                    addAll(approachingTasks.map { it.first.id })
                }
                showNotificationTray = false
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.HomeWork,
                        contentDescription = "App Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Friendly To Do",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Sync & Backup Control",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Sync, contentDescription = "GitHub Sync") },
                    label = { Text("GitHub Sync") },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showGithubSyncDialog = true
                    },
                    modifier = Modifier
                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                        .testTag("drawer_github_sync")
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.CloudUpload, contentDescription = "Export JSON") },
                    label = { Text("Export JSON") },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        currentExportJsonString = viewModel.exportDataToJson()
                        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                        exportLauncher.launch("backup-ToDo-$dateStr.JSON")
                    },
                    modifier = Modifier
                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                        .testTag("drawer_export_json")
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.CloudDownload, contentDescription = "Import JSON") },
                    label = { Text("Import JSON") },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        importLauncher.launch(arrayOf("application/json", "*/*"))
                    },
                    modifier = Modifier
                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                        .testTag("drawer_import_json")
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                coroutineScope.launch { drawerState.open() }
                            },
                            modifier = Modifier.testTag("hamburger_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Hamburger Menu"
                            )
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.HomeWork,
                                contentDescription = "App Icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Friendly To Do Reminder",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showNotificationTray = true },
                            modifier = Modifier.testTag("notification_bell_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (approachingTasks.isNotEmpty()) {
                                        Badge(
                                            modifier = Modifier.testTag("notification_badge")
                                        ) {
                                            Text(
                                                text = approachingTasks.size.toString(),
                                                modifier = Modifier.testTag("notification_badge_count")
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (approachingTasks.isNotEmpty()) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                    contentDescription = "Notifications",
                                    tint = if (approachingTasks.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    HomeTab.values().forEach { tab ->
                        NavigationBarItem(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag("${tab.name.lowercase()}_tab")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "Tab Transition",
                    modifier = Modifier.fillMaxSize()
                ) { targetTab ->
                    when (targetTab) {
                        HomeTab.TASKS -> TasksAndNotesTab(viewModel)
                        HomeTab.DRAWING -> DrawingPadTab(viewModel)
                        HomeTab.CALENDAR -> CalendarTab(viewModel)
                        HomeTab.ARCHIVE -> ArchiveTab(viewModel)
                    }
                }

                // Browser notification banner overlay floating on top
                AnimatedVisibility(
                    visible = currentBannerItem != null,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter).zIndex(99f)
                ) {
                    if (currentBannerItem != null) {
                        val (task, type) = currentBannerItem
                        BrowserNotificationBanner(
                            task = task,
                            type = type,
                            onDismiss = {
                                dismissedNotificationIds = dismissedNotificationIds.toMutableSet().apply { add(task.id) }
                            },
                            onComplete = {
                                viewModel.toggleTask(task)
                                dismissedNotificationIds = dismissedNotificationIds.toMutableSet().apply { add(task.id) }
                            },
                            onSnooze = {
                                val newDueDate = (task.dueDate ?: System.currentTimeMillis()) + 3600000
                                viewModel.updateTask(task.copy(dueDate = newDueDate))
                                dismissedNotificationIds = dismissedNotificationIds.toMutableSet().apply { add(task.id) }
                            }
                        )
                    }
                }
            }
        }
    }
}

// ==================== TAB 1: TASKS & NOTES ====================
@Composable
fun TasksAndNotesTab(viewModel: HomeViewModel) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val smartSuggestions by viewModel.smartSuggestions.collectAsStateWithLifecycle()
    val isSmartSuggesting by viewModel.isSmartSuggesting.collectAsStateWithLifecycle()

    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var selectedSort by remember { mutableStateOf("Newest") }

    val categories = listOf("All", "Cleaning", "Garden", "General", "Home", "Kitchen", "Maintenance")

    val activeTasks = tasks.filter { !it.isCompleted }
    val filteredTasks = if (selectedCategoryFilter == "All") {
        activeTasks
    } else {
        activeTasks.filter { it.category.equals(selectedCategoryFilter, ignoreCase = true) }
    }

    val sortedTasks = remember(filteredTasks, selectedSort) {
        when (selectedSort) {
            "A-Z" -> {
                filteredTasks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            }
            "Newest" -> {
                filteredTasks.sortedByDescending { it.id }
            }
            "Oldest" -> {
                filteredTasks.sortedBy { it.id }
            }
            else -> filteredTasks
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome Hero Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Hello there! 👋",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Keep your home clean, organized, and running smoothly. Let's tackle some chores today!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showAddTaskDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("add_task_fab")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Task")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Task")
                        }
                        Button(
                            onClick = { showAddNoteDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.NoteAdd, contentDescription = "Add Note")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Note")
                        }
                    }
                }
            }
        }

        // AI Smart suggestions section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Gemini AI",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Gemini Chore Suggestions",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        if (isSmartSuggesting) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            TextButton(onClick = { viewModel.generateSmartChoreSuggestions() }) {
                                Text("Generate")
                            }
                        }
                    }

                    if (smartSuggestions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Tap a suggested task to add it directly to your home list:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(smartSuggestions) { suggestion ->
                                Card(
                                    modifier = Modifier
                                        .width(220.dp)
                                        .clickable {
                                            viewModel.addTask(
                                                title = suggestion.title,
                                                category = suggestion.category,
                                                notes = suggestion.notes
                                            )
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = suggestion.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = suggestion.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (suggestion.notes.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = suggestion.notes,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.dismissSuggestions() }, modifier = Modifier.align(Alignment.End)) {
                            Text("Dismiss")
                        }
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Let Gemini analyze your home list and suggest useful chores like checks, yard water times, or cleaning reminders.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Category Filter Tabs
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(categories) { cat ->
                    val isSelected = selectedCategoryFilter == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategoryFilter = cat },
                        label = { Text(cat) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
        }

        // Tasks Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Your Home Chores",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${sortedTasks.size} active",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                var expanded by remember { mutableStateOf(false) }
                Box {
                    AssistChip(
                        onClick = { expanded = true },
                        label = { Text("Sort: $selectedSort") },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select sort order",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Newest") },
                            onClick = {
                                selectedSort = "Newest"
                                expanded = false
                            },
                            leadingIcon = {
                                if (selectedSort == "Newest") {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Oldest") },
                            onClick = {
                                selectedSort = "Oldest"
                                expanded = false
                            },
                            leadingIcon = {
                                if (selectedSort == "Oldest") {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("A-Z") },
                            onClick = {
                                selectedSort = "A-Z"
                                expanded = false
                            },
                            leadingIcon = {
                                if (selectedSort == "A-Z") {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        // Task Items List
        if (sortedTasks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = "No tasks",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "All clear! No tasks in this category.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(sortedTasks) { task ->
                TaskItemCard(task = task, viewModel = viewModel)
            }
        }

        // Notes Header
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Home Reference Notes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Notes Section
        if (notes.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No reference notes. Jot down faucet models, utility contacts, or paint codes!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            item {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .height(280.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notes) { note ->
                        NoteCardItem(note = note, viewModel = viewModel)
                    }
                }
            }
        }
    }

    // Task Dialog
    if (showAddTaskDialog) {
        AddTaskDialog(onDismiss = { showAddTaskDialog = false }, viewModel = viewModel)
    }

    // Note Dialog
    if (showAddNoteDialog) {
        AddNoteDialog(onDismiss = { showAddNoteDialog = false }, viewModel = viewModel)
    }
}

@Composable
fun PriorityBadge(priority: String) {
    val (bgColor, textColor) = when (priority.lowercase(Locale.getDefault())) {
        "high" -> Color(0xFFFCE8E6) to Color(0xFFC5221F) // Soft warning red
        "low" -> Color(0xFFE6F4EA) to Color(0xFF137333)  // Soft friendly green
        else -> Color(0xFFFEF7E0) to Color(0xFFB06000)  // Soft warning orange/yellow (Medium)
    }
    
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.height(22.dp).testTag("priority_badge_${priority.lowercase(Locale.getDefault())}")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = priority,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun FrequencyBadge(frequency: String) {
    if (frequency.equals("Once", ignoreCase = true)) return
    
    val (bgColor, textColor) = when (frequency.lowercase(Locale.getDefault())) {
        "daily" -> Color(0xFFE8F0FE) to Color(0xFF1967D2) // Soft blue
        "weekly" -> Color(0xFFF3E8FD) to Color(0xFF8611F3) // Soft purple
        "monthly" -> Color(0xFFE2F3F5) to Color(0xFF007A87) // Soft teal
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.height(22.dp).testTag("frequency_badge_${frequency.lowercase(Locale.getDefault())}")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = frequency,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TaskItemCard(task: HomeTask, viewModel: HomeViewModel) {
    var showSketchDialog by remember { mutableStateOf(false) }
    var attachedSketch by remember { mutableStateOf<Drawing?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    LaunchedEffect(task.attachedDrawingId) {
        if (task.hasDrawingAttachment && task.attachedDrawingId != null) {
            attachedSketch = viewModel.drawings.value.find { it.id == task.attachedDrawingId }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_item_card"),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { viewModel.toggleTask(task) },
                modifier = Modifier.testTag("task_checkbox_${task.id}")
            )
            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = { },
                        label = { Text(task.category, fontSize = 10.sp) },
                        modifier = Modifier.height(22.dp)
                    )
                    PriorityBadge(priority = task.priority)
                    FrequencyBadge(frequency = task.frequency)
                }

                if (task.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = task.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (task.dueDate != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Due Date",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val formatted = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(task.dueDate))
                        Text(
                            text = "Due: $formatted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (task.isCompleted && task.completedAt != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed At",
                            tint = Color(0xFF137333),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Finished: ${task.completedAt}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF137333),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("task_completed_at_text")
                        )
                    }
                }
            }

            // Attached drawing button
            if (task.hasDrawingAttachment && task.attachedDrawingId != null) {
                IconButton(onClick = { showSketchDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Gesture,
                        contentDescription = "View Drawing",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(
                onClick = { showEditDialog = true },
                modifier = Modifier.testTag("edit_task_button_${task.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Task",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = { showDeleteConfirmDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Task",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }
        }
    }

    if (showEditDialog) {
        EditTaskDialog(
            task = task,
            onDismiss = { showEditDialog = false },
            viewModel = viewModel
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(text = "Delete Task?")
            },
            text = {
                Text(text = "Are you sure you want to delete the task \"${task.title}\"? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTask(task)
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_task_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSketchDialog && attachedSketch != null) {
        Dialog(onDismissRequest = { showSketchDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Attached Sketch: ${attachedSketch!!.title}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
                    ) {
                        val paths = remember(attachedSketch) {
                            DrawingSerializer.deserializeDrawing(attachedSketch!!.drawingData)
                        }
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            paths.forEach { path ->
                                if (path.points.size > 1) {
                                    val composePath = Path().apply {
                                        moveTo(path.points[0].x, path.points[0].y)
                                        for (i in 1 until path.points.size) {
                                            lineTo(path.points[i].x, path.points[i].y)
                                        }
                                    }
                                    drawPath(
                                        path = composePath,
                                        color = Color(android.graphics.Color.parseColor(path.colorHex)),
                                        style = Stroke(
                                            width = path.strokeWidth,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(onClick = { showSketchDialog = false }) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun NoteCardItem(note: HomeNote, viewModel: HomeViewModel) {
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.size(24.dp).testTag("edit_note_button_${note.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Note",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Delete Note",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = note.content,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }

    if (showEditDialog) {
        EditNoteDialog(
            note = note,
            onDismiss = { showEditDialog = false },
            viewModel = viewModel
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(text = "Delete Note?")
            },
            text = {
                Text(text = "Are you sure you want to delete the reference note \"${note.title}\"? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteNote(note)
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_note_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun EditTaskDialog(task: HomeTask, onDismiss: () -> Unit, viewModel: HomeViewModel) {
    var title by remember { mutableStateOf(task.title) }
    var notes by remember { mutableStateOf(task.notes) }
    var category by remember { mutableStateOf(task.category) }
    var priority by remember { mutableStateOf(task.priority) }
    var frequency by remember { mutableStateOf(task.frequency) }
    var dueDate by remember { mutableStateOf(task.dueDate) }
    var selectedDrawingId by remember { mutableStateOf(task.attachedDrawingId) }

    val context = LocalContext.current
    val drawings by viewModel.drawings.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Household Task") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Chore Title (e.g. Clean gutters)") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_task_title_input")
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Details / Notes") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_task_notes_input")
                )

                // Category selection
                Text("Category:", fontWeight = FontWeight.SemiBold)
                val categoriesList = listOf("Cleaning", "Garden", "General", "Home", "Kitchen", "Maintenance")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categoriesList) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) },
                            modifier = Modifier.testTag("edit_category_chip_$cat")
                        )
                    }
                }

                // Priority selection
                Text("Priority Level:", fontWeight = FontWeight.SemiBold)
                val priorityLevels = listOf("Low", "Medium", "High")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    priorityLevels.forEach { p ->
                        val isSelected = priority == p
                        val selectColor = when (p) {
                            "High" -> Color(0xFFC5221F)
                            "Medium" -> Color(0xFFB06000)
                            else -> Color(0xFF137333)
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { priority = p },
                            label = { 
                                Text(
                                    text = p,
                                    color = if (isSelected) selectColor else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ) 
                            },
                            modifier = Modifier.testTag("edit_priority_chip_$p")
                        )
                    }
                }

                // Frequency selection
                Text("Recurrence Frequency:", fontWeight = FontWeight.SemiBold)
                val frequencies = listOf("Once", "Daily", "Weekly", "Monthly")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    frequencies.forEach { freq ->
                        val isSelected = frequency == freq
                        FilterChip(
                            selected = isSelected,
                            onClick = { frequency = freq },
                            label = { Text(freq) },
                            modifier = Modifier.testTag("edit_frequency_chip_$freq")
                        )
                    }
                }

                // Due Date selection
                Button(
                    onClick = {
                        val calendar = Calendar.getInstance()
                        if (dueDate != null) {
                            calendar.timeInMillis = dueDate!!
                        }
                        DatePickerDialog(
                            context,
                            { _: DatePicker, year: Int, month: Int, day: Int ->
                                calendar.set(year, month, day)
                                dueDate = calendar.timeInMillis
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("edit_task_due_date_button")
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = "Date")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (dueDate == null) "Set Due Date" else {
                            val fmt = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(dueDate!!))
                            "Due: $fmt"
                        }
                    )
                }

                // Drawing Pad attachment selection
                if (drawings.isNotEmpty()) {
                    Text("Attach a Sketch:", fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(drawings) { d ->
                            FilterChip(
                                selected = selectedDrawingId == d.id,
                                onClick = {
                                    selectedDrawingId = if (selectedDrawingId == d.id) null else d.id
                                },
                                label = { Text(d.title) },
                                modifier = Modifier.testTag("edit_drawing_chip_${d.id}")
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        viewModel.updateTask(
                            task.copy(
                                title = title,
                                notes = notes,
                                dueDate = dueDate,
                                category = category,
                                hasDrawingAttachment = selectedDrawingId != null,
                                attachedDrawingId = selectedDrawingId,
                                priority = priority,
                                frequency = frequency
                            )
                        )
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("edit_task_save_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("edit_task_cancel_button")) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddTaskDialog(onDismiss: () -> Unit, viewModel: HomeViewModel) {
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Home") }
    var priority by remember { mutableStateOf("Medium") }
    var frequency by remember { mutableStateOf("Once") }
    var dueDate by remember { mutableStateOf<Long?>(null) }
    var selectedDrawingId by remember { mutableStateOf<Int?>(null) }

    val context = LocalContext.current
    val drawings by viewModel.drawings.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Household Task") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Chore Title (e.g. Clean gutters)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Details / Notes") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Category selection
                Text("Category:", fontWeight = FontWeight.SemiBold)
                val categoriesList = listOf("Cleaning", "Garden", "General", "Home", "Kitchen", "Maintenance")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categoriesList) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }

                // Priority selection
                Text("Priority Level:", fontWeight = FontWeight.SemiBold)
                val priorityLevels = listOf("Low", "Medium", "High")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    priorityLevels.forEach { p ->
                        val isSelected = priority == p
                        val selectColor = when (p) {
                            "High" -> Color(0xFFC5221F)
                            "Medium" -> Color(0xFFB06000)
                            else -> Color(0xFF137333)
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { priority = p },
                            label = { 
                                Text(
                                    text = p,
                                    color = if (isSelected) selectColor else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ) 
                            },
                            modifier = Modifier.testTag("priority_chip_$p")
                        )
                    }
                }

                // Frequency selection
                Text("Recurrence Frequency:", fontWeight = FontWeight.SemiBold)
                val frequencies = listOf("Once", "Daily", "Weekly", "Monthly")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    frequencies.forEach { freq ->
                        val isSelected = frequency == freq
                        FilterChip(
                            selected = isSelected,
                            onClick = { frequency = freq },
                            label = { Text(freq) },
                            modifier = Modifier.testTag("frequency_chip_$freq")
                        )
                    }
                }

                // Due Date selection
                Button(
                    onClick = {
                        val calendar = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _: DatePicker, year: Int, month: Int, day: Int ->
                                calendar.set(year, month, day)
                                dueDate = calendar.timeInMillis
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = "Date")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (dueDate == null) "Set Due Date" else {
                            val fmt = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(dueDate!!))
                            "Due: $fmt"
                        }
                    )
                }

                // Drawing Pad attachment selection
                if (drawings.isNotEmpty()) {
                    Text("Attach a Sketch:", fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(drawings) { d ->
                            FilterChip(
                                selected = selectedDrawingId == d.id,
                                onClick = {
                                    selectedDrawingId = if (selectedDrawingId == d.id) null else d.id
                                },
                                label = { Text(d.title) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        viewModel.addTask(
                            title = title,
                            notes = notes,
                            dueDate = dueDate,
                            category = category,
                            hasDrawingAttachment = selectedDrawingId != null,
                            attachedDrawingId = selectedDrawingId,
                            priority = priority,
                            frequency = frequency
                        )
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("submit_button")
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddNoteDialog(onDismiss: () -> Unit, viewModel: HomeViewModel) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Home Note") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Note Title (e.g. Utility numbers)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Information / Content") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        viewModel.addNote(title, content)
                        onDismiss()
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditNoteDialog(note: HomeNote, onDismiss: () -> Unit, viewModel: HomeViewModel) {
    var title by remember { mutableStateOf(note.title) }
    var content by remember { mutableStateOf(note.content) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Home Note") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Note Title (e.g. Utility numbers)") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_note_title_input")
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Information / Content") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_note_content_input"),
                    minLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        viewModel.updateNote(note.copy(title = title, content = content))
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("edit_note_save_button")
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("edit_note_cancel_button")) {
                Text("Cancel")
            }
        }
    )
}


// ==================== TAB 2: DRAWING PAD ====================
@Composable
fun DrawingPadTab(viewModel: HomeViewModel) {
    var title by remember { mutableStateOf("My Sketch") }
    val drawings by viewModel.drawings.collectAsStateWithLifecycle()

    var selectedColorHex by remember { mutableStateOf("#000000") }
    var selectedStrokeWidth by remember { mutableStateOf(8f) }

    var activePaths by remember { mutableStateOf(listOf(DrawingPath(emptyList(), "#000000", 8f))) }
    var currentPathPoints by remember { mutableStateOf(emptyList<DrawingPoint>()) }

    val colors = listOf(
        "#000000", // Black
        "#E53935", // Red
        "#43A047", // Green
        "#1E88E5", // Blue
        "#FFB300", // Yellow/Orange
        "#8E24AA"  // Purple
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Sketch Title") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val serialized = DrawingSerializer.serializeDrawing(activePaths)
                    viewModel.saveDrawing(title, serialized) {
                        title = "My Sketch"
                        activePaths = listOf(DrawingPath(emptyList(), selectedColorHex, selectedStrokeWidth))
                    }
                }
            ) {
                Icon(Icons.Default.Save, contentDescription = "Save Drawing")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save")
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = {
                activePaths = listOf(DrawingPath(emptyList(), selectedColorHex, selectedStrokeWidth))
            }) {
                Icon(Icons.Default.DeleteForever, contentDescription = "Clear Canvas", tint = MaterialTheme.colorScheme.error)
            }
        }

        // Color and stroke palette selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                colors.forEach { hex ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(hex)))
                            .border(
                                width = if (selectedColorHex == hex) 3.dp else 1.dp,
                                color = if (selectedColorHex == hex) MaterialTheme.colorScheme.primary else Color.LightGray,
                                shape = CircleShape
                            )
                            .clickable {
                                selectedColorHex = hex
                                // update current active empty path properties
                                activePaths = activePaths.dropLast(1) + DrawingPath(emptyList(), hex, selectedStrokeWidth)
                            }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Size:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Slider(
                    value = selectedStrokeWidth,
                    onValueChange = {
                        selectedStrokeWidth = it
                        activePaths = activePaths.dropLast(1) + DrawingPath(emptyList(), selectedColorHex, it)
                    },
                    valueRange = 4f..32f,
                    modifier = Modifier.width(100.dp)
                )
            }
        }

        // Canvas Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(16.dp))
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .pointerInput(selectedColorHex, selectedStrokeWidth) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPathPoints = listOf(DrawingPoint(offset.x, offset.y))
                            activePaths = activePaths + DrawingPath(currentPathPoints, selectedColorHex, selectedStrokeWidth)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val newPoint = DrawingPoint(change.position.x, change.position.y)
                            currentPathPoints = currentPathPoints + newPoint
                            // Update the last path in the list
                            if (activePaths.isNotEmpty()) {
                                activePaths = activePaths.dropLast(1) + DrawingPath(currentPathPoints, selectedColorHex, selectedStrokeWidth)
                            }
                        },
                        onDragEnd = {
                            currentPathPoints = emptyList()
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                activePaths.forEach { path ->
                    if (path.points.size > 1) {
                        val composePath = Path().apply {
                            moveTo(path.points[0].x, path.points[0].y)
                            for (i in 1 until path.points.size) {
                                lineTo(path.points[i].x, path.points[i].y)
                            }
                        }
                        drawPath(
                            path = composePath,
                            color = Color(android.graphics.Color.parseColor(path.colorHex)),
                            style = Stroke(
                                width = path.strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }

        // Saved Drawings List
        Text("Saved Drawings & Diagrams:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        if (drawings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No saved drawings. Sketch plumbing pipes, yards, or project details!", fontSize = 12.sp, color = Color.Gray)
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(drawings) { d ->
                    Card(
                        modifier = Modifier
                            .width(140.dp)
                            .clickable {
                                title = d.title
                                activePaths = DrawingSerializer.deserializeDrawing(d.drawingData)
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = d.title,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { viewModel.deleteDrawing(d) }, modifier = Modifier.size(16.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete Drawing", modifier = Modifier.size(12.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            // Simple box thumbnail
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .background(Color.White, RoundedCornerShape(4.dp))
                            ) {
                                val miniPaths = DrawingSerializer.deserializeDrawing(d.drawingData)
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    miniPaths.forEach { path ->
                                        if (path.points.size > 1) {
                                            val composePath = Path().apply {
                                                // scale points slightly for thumbnail
                                                moveTo(path.points[0].x * 0.35f, path.points[0].y * 0.15f)
                                                for (i in 1 until path.points.size) {
                                                    lineTo(path.points[i].x * 0.35f, path.points[i].y * 0.15f)
                                                }
                                            }
                                            drawPath(
                                                path = composePath,
                                                color = Color(android.graphics.Color.parseColor(path.colorHex)),
                                                style = Stroke(width = 2f)
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


// ==================== TAB 3: CALENDAR VIEW ====================
enum class CalendarMode { DAY, WEEK, MONTH }

@Composable
fun SegmentedCalendarModeSelector(
    selectedMode: CalendarMode,
    onModeSelected: (CalendarMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = CalendarMode.values()
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            modes.forEach { mode ->
                val isSelected = selectedMode == mode
                val targetColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(2.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(targetColor)
                        .clickable { onModeSelected(mode) }
                        .testTag("calendar_mode_button_${mode.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (mode) {
                            CalendarMode.DAY -> "Day"
                            CalendarMode.WEEK -> "Week"
                            CalendarMode.MONTH -> "Month"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
fun CalendarHeader(
    selectedDate: Date,
    mode: CalendarMode,
    onDateChange: (Date) -> Unit,
    modifier: Modifier = Modifier
) {
    val sdfMonthHeader = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    val sdfDayHeader = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                val cal = Calendar.getInstance().apply { time = selectedDate }
                when (mode) {
                    CalendarMode.DAY -> cal.add(Calendar.DAY_OF_YEAR, -1)
                    CalendarMode.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, -1)
                    CalendarMode.MONTH -> cal.add(Calendar.MONTH, -1)
                }
                onDateChange(cal.time)
            },
            modifier = Modifier.testTag("calendar_prev_button")
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Previous",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = if (mode == CalendarMode.DAY) sdfDayHeader.format(selectedDate) else sdfMonthHeader.format(selectedDate),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = when (mode) {
                    CalendarMode.DAY -> "Single Day"
                    CalendarMode.WEEK -> "Weekly Strip"
                    CalendarMode.MONTH -> "Monthly Grid"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )
        }
        
        IconButton(
            onClick = {
                val cal = Calendar.getInstance().apply { time = selectedDate }
                when (mode) {
                    CalendarMode.DAY -> cal.add(Calendar.DAY_OF_YEAR, 1)
                    CalendarMode.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                    CalendarMode.MONTH -> cal.add(Calendar.MONTH, 1)
                }
                onDateChange(cal.time)
            },
            modifier = Modifier.testTag("calendar_next_button")
        ) {
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "Next",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun DayCalendarView(selectedDate: Date, tasks: List<HomeTask>) {
    val startOfDay = getStartOfDay(selectedDate).timeInMillis
    val endOfDay = getEndOfDay(selectedDate).timeInMillis
    val dayTasks = tasks.filter {
        it.dueDate != null && it.dueDate in startOfDay..endOfDay
    }
    val completedCount = dayTasks.count { it.isCompleted }
    val remainingCount = dayTasks.size - completedCount

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("day_calendar_hero_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = SimpleDateFormat("EEEE", Locale.getDefault()).format(selectedDate),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (dayTasks.isEmpty()) {
                        "No chores scheduled"
                    } else if (remainingCount == 0) {
                        "All chores completed! 🎉"
                    } else {
                        "$remainingCount chores remaining"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = SimpleDateFormat("d", Locale.getDefault()).format(selectedDate),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun WeekCalendarView(selectedDate: Date, tasks: List<HomeTask>, onDateChange: (Date) -> Unit) {
    val cal = Calendar.getInstance().apply { time = selectedDate }
    cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)

    val weekDays = remember(selectedDate) {
        List(7) {
            val d = cal.time
            cal.add(Calendar.DAY_OF_YEAR, 1)
            d
        }
    }

    val activeDaySdf = SimpleDateFormat("d", Locale.getDefault())
    val activeDayNameSdf = SimpleDateFormat("EEE", Locale.getDefault())

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        weekDays.forEach { date ->
            val isSelected = isSameDay(date, selectedDate)
            val isToday = isSameDay(date, Date())

            val border = when {
                isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                isToday -> BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onDateChange(date) }
                    .testTag("week_day_card_${activeDaySdf.format(date)}"),
                border = border,
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else if (isToday) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = activeDayNameSdf.format(date),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = activeDaySdf.format(date),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val hasTasks = tasks.any {
                        it.dueDate != null && isSameDay(Date(it.dueDate), date)
                    }
                    if (hasTasks) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MonthCalendarView(selectedDate: Date, tasks: List<HomeTask>, onDateChange: (Date) -> Unit) {
    val cal = Calendar.getInstance().apply {
        time = selectedDate
        set(Calendar.DAY_OF_MONTH, 1)
    }

    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

    cal.add(Calendar.DAY_OF_MONTH, -(firstDayOfWeek - 1))
    val monthDays = remember(selectedDate) {
        List(42) {
            val d = cal.time
            cal.add(Calendar.DAY_OF_MONTH, 1)
            d
        }
    }

    val daysHeader = listOf("S", "M", "T", "W", "T", "F", "S")

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            daysHeader.forEach { d ->
                Text(
                    text = d,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(monthDays) { date ->
                val isSelected = isSameDay(date, selectedDate)
                val c1 = Calendar.getInstance().apply { time = date }
                val c2 = Calendar.getInstance().apply { time = selectedDate }
                val isCurrentMonth = c1.get(Calendar.MONTH) == c2.get(Calendar.MONTH)
                val isToday = isSameDay(date, Date())

                val border = when {
                    isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    isToday -> BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                    else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }

                Card(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable { onDateChange(date) }
                        .testTag("month_day_card_${SimpleDateFormat("d", Locale.getDefault()).format(date)}"),
                    border = border,
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                            isToday -> MaterialTheme.colorScheme.secondaryContainer
                            isCurrentMonth -> MaterialTheme.colorScheme.surface
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        }
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Text(
                                text = SimpleDateFormat("d", Locale.getDefault()).format(date),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                    isCurrentMonth -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                }
                            )
                            val hasTasks = tasks.any {
                                it.dueDate != null && isSameDay(Date(it.dueDate), date)
                            }
                            if (hasTasks) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                                )
                            } else {
                                Spacer(modifier = Modifier.height(7.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarTab(viewModel: HomeViewModel) {
    var mode by remember { mutableStateOf(CalendarMode.WEEK) }
    var selectedDate by remember { mutableStateOf(Date()) }
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var icsContent by remember { mutableStateOf("") }
    val calendarExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    java.io.OutputStreamWriter(outputStream).use { writer ->
                        writer.write(icsContent)
                    }
                }
                android.widget.Toast.makeText(context, "Calendar exported successfully!", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Failed to export calendar: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SegmentedCalendarModeSelector(
                selectedMode = mode,
                onModeSelected = { mode = it },
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            IconButton(
                onClick = {
                    val scheduledTasks = tasks.filter { it.dueDate != null }
                    if (scheduledTasks.isEmpty()) {
                        android.widget.Toast.makeText(context, "No scheduled chores to export!", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        icsContent = generateIcsContent(scheduledTasks)
                        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                        calendarExportLauncher.launch("chores-schedule-$dateStr.ics")
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(24.dp))
                    .testTag("export_calendar_ics_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = "Export Calendar (.ics)",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        CalendarHeader(
            selectedDate = selectedDate,
            mode = mode,
            onDateChange = { selectedDate = it }
        )

        when (mode) {
            CalendarMode.DAY -> DayCalendarView(selectedDate, tasks)
            CalendarMode.WEEK -> WeekCalendarView(selectedDate, tasks) { selectedDate = it }
            CalendarMode.MONTH -> MonthCalendarView(selectedDate, tasks) { selectedDate = it }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            thickness = 1.dp
        )

        Text(
            text = "Due on: " + SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(selectedDate),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        val activeDayStart = getStartOfDay(selectedDate).timeInMillis
        val activeDayEnd = getEndOfDay(selectedDate).timeInMillis
        val selectedDayTasks = tasks.filter {
            it.dueDate != null && it.dueDate in activeDayStart..activeDayEnd
        }

        if (selectedDayTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No chores scheduled for this day.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(selectedDayTasks) { task ->
                    TaskItemCard(task = task, viewModel = viewModel)
                }
            }
        }
    }
}

fun generateIcsContent(tasks: List<HomeTask>): String {
    val builder = StringBuilder()
    builder.append("BEGIN:VCALENDAR\n")
    builder.append("VERSION:2.0\n")
    builder.append("PRODID:-//AI Studio Build//Chores App//EN\n")
    builder.append("CALSCALE:GREGORIAN\n")
    builder.append("METHOD:PUBLISH\n")

    val sdfIcs = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val dtStamp = sdfIcs.format(Date())

    tasks.filter { it.dueDate != null }.forEach { task ->
        val dueDateMs = task.dueDate!!
        val dtStart = sdfIcs.format(Date(dueDateMs))
        val dtEnd = sdfIcs.format(Date(dueDateMs + 3600000)) // 1 hour duration

        val cleanTitle = escapeIcsText(task.title)
        val cleanNotes = escapeIcsText(task.notes)
        val cleanCategory = escapeIcsText(task.category)

        builder.append("BEGIN:VEVENT\n")
        builder.append("UID:chore-${task.id}@aistudio.com\n")
        builder.append("DTSTAMP:$dtStamp\n")
        builder.append("DTSTART:$dtStart\n")
        builder.append("DTEND:$dtEnd\n")
        builder.append("SUMMARY:$cleanTitle\n")
        if (cleanNotes.isNotEmpty()) {
            builder.append("DESCRIPTION:$cleanNotes\n")
        }
        builder.append("CATEGORIES:$cleanCategory\n")
        builder.append("STATUS:${if (task.isCompleted) "COMPLETED" else "CONFIRMED"}\n")
        builder.append("END:VEVENT\n")
    }

    builder.append("END:VCALENDAR")
    return builder.toString()
}

fun escapeIcsText(text: String): String {
    return text.replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")
        .replace("\r", "")
}





// ==================== GENERAL DATE UTILS ====================
fun isSameDay(d1: Date, d2: Date): Boolean {
    val c1 = Calendar.getInstance().apply { time = d1 }
    val c2 = Calendar.getInstance().apply { time = d2 }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}

fun getStartOfDay(date: Date): Calendar {
    return Calendar.getInstance().apply {
        time = date
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}

fun getEndOfDay(date: Date): Calendar {
    return Calendar.getInstance().apply {
        time = date
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }
}

@Composable
fun GithubSyncDialog(
    viewModel: HomeViewModel,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var token by remember { mutableStateOf(viewModel.getGithubToken()) }
    var gistId by remember { mutableStateOf(viewModel.getGistId()) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("GitHub Gist Backup & Sync") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Backup and sync your chores & notes securely using a personal GitHub Gist. Your settings are stored locally on this device.",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("GitHub Personal Access Token (PAT)") },
                    placeholder = { Text("ghp_...") },
                    modifier = Modifier.fillMaxWidth().testTag("gist_token_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = gistId,
                    onValueChange = { gistId = it },
                    label = { Text("Gist ID (optional)") },
                    placeholder = { Text("Leave blank to create a new Gist") },
                    modifier = Modifier.fillMaxWidth().testTag("gist_id_input"),
                    singleLine = true
                )

                if (statusMessage.isNotEmpty()) {
                    Text(
                        text = statusMessage,
                        color = if (statusMessage.contains("Success", ignoreCase = true)) Color(0xFF137333) else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("gist_status_message")
                    )
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = {
                        viewModel.saveGistSettings(token.trim(), gistId.trim())
                        statusMessage = "Settings saved successfully!"
                    },
                    enabled = !isLoading,
                    modifier = Modifier.testTag("save_gist_settings_button")
                ) {
                    Text("Save Settings")
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        isLoading = true
                        statusMessage = "Downloading from Gist..."
                        viewModel.saveGistSettings(token.trim(), gistId.trim())
                        coroutineScope.launch {
                            val result = viewModel.downloadFromGist()
                            isLoading = false
                            if (result.isSuccess) {
                                statusMessage = "Success! Data imported."
                            } else {
                                statusMessage = "Error: ${result.exceptionOrNull()?.message}"
                            }
                        }
                    },
                    enabled = !isLoading && token.isNotBlank() && gistId.isNotBlank(),
                    modifier = Modifier.testTag("download_gist_button")
                ) {
                    Text("Import")
                }

                Button(
                    onClick = {
                        isLoading = true
                        statusMessage = "Uploading backup to Gist..."
                        viewModel.saveGistSettings(token.trim(), gistId.trim())
                        coroutineScope.launch {
                            val result = viewModel.uploadToGist()
                            isLoading = false
                            if (result.isSuccess) {
                                val newId = result.getOrNull() ?: ""
                                gistId = newId
                                statusMessage = "Success! Data backed up to Gist."
                            } else {
                                statusMessage = "Error: ${result.exceptionOrNull()?.message}"
                            }
                        }
                    },
                    enabled = !isLoading && token.isNotBlank(),
                    modifier = Modifier.testTag("upload_gist_button")
                ) {
                    Text("Export")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading,
                modifier = Modifier.testTag("close_gist_dialog_button")
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
fun ExportJsonDialog(
    jsonString: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dateStr = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) }
    val fileName = "backup-ToDo-$dateStr.JSON"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export JSON Backup") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Copy or share the raw JSON data below. The recommended filename for this backup is:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("backup_filename_label")
                )
                OutlinedTextField(
                    value = jsonString,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .testTag("exported_json_text_field"),
                    textStyle = MaterialTheme.typography.bodySmall,
                    maxLines = 15
                )
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Share Button
                Button(
                    onClick = {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_SUBJECT, fileName)
                            putExtra(android.content.Intent.EXTRA_TEXT, jsonString)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Backup ($fileName)"))
                    },
                    modifier = Modifier.testTag("share_json_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share")
                        Text("Share")
                    }
                }

                // Copy Button
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Friendly ToDo Backup JSON", jsonString)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "Copied backup JSON to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("copy_json_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy")
                        Text("Copy")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_export_dialog_button")
            ) {
                Text("Close")
            }
        }
      )
}

@Composable
fun BrowserNotificationBanner(
    task: HomeTask,
    type: NotificationType,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    onSnooze: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .shadow(12.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Browser notification header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Web Browser",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "https://friendly-todo.app • Just now",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp).testTag("dismiss_notification_${task.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Notification Content
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = if (type == NotificationType.OVERDUE) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (type == NotificationType.OVERDUE) {
                            Icons.Default.Warning
                        } else {
                            Icons.Default.Schedule
                        },
                        contentDescription = "Alert Type",
                        tint = if (type == NotificationType.OVERDUE) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (type == NotificationType.OVERDUE) "⚠️ Task is Overdue!" else "⏰ Task Due Soon!",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (type == NotificationType.OVERDUE) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (task.notes.isNotBlank()) {
                        Text(
                            text = task.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Interactive Action Buttons
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = onSnooze,
                    modifier = Modifier.testTag("snooze_notification_${task.id}")
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = "Snooze", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Snooze (1h)")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onComplete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("complete_notification_${task.id}")
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Complete", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Mark Done")
                }
            }
        }
    }
}

@Composable
fun BrowserNotificationTrayDialog(
    approachingTasks: List<Pair<HomeTask, NotificationType>>,
    onDismiss: () -> Unit,
    onComplete: (HomeTask) -> Unit,
    onSnooze: (HomeTask) -> Unit,
    onClearAll: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Notifications",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Browser Alerts Center")
                }
                if (approachingTasks.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Text("Dismiss All", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        text = {
            if (approachingTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "No Alerts",
                            tint = Color(0xFF137333),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "You are all caught up!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "No tasks are due soon or overdue.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(approachingTasks) { (task, type) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (type == NotificationType.OVERDUE) {
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                }
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (type == NotificationType.OVERDUE) {
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                }
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (type == NotificationType.OVERDUE) "⚠️ OVERDUE" else "⏰ DUE SOON",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (type == NotificationType.OVERDUE) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                    )
                                    val formattedTime = task.dueDate?.let {
                                        SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(it))
                                    } ?: ""
                                    Text(
                                        text = formattedTime,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (task.notes.isNotBlank()) {
                                    Text(
                                        text = task.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.End,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    TextButton(
                                        onClick = { onSnooze(task) },
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Snooze 1h", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { onComplete(task) },
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                    ) {
                                        Text("Mark Done", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveTab(viewModel: HomeViewModel) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var selectedSort by remember { mutableStateOf("Newest") }
    val categories = listOf("All", "Cleaning", "Garden", "General", "Home", "Kitchen", "Maintenance")

    val completedTasks = tasks.filter { it.isCompleted }
    val filteredTasks = if (selectedCategoryFilter == "All") {
        completedTasks
    } else {
        completedTasks.filter { it.category.equals(selectedCategoryFilter, ignoreCase = true) }
    }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd, hh:mm:ss a", Locale.getDefault()) }
    val sortedTasks = remember(filteredTasks, selectedSort) {
        when (selectedSort) {
            "A-Z" -> {
                filteredTasks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            }
            "Newest" -> {
                filteredTasks.sortedWith { t1, t2 ->
                    val d1 = try { t1.completedAt?.let { sdf.parse(it) } } catch (e: Exception) { null }
                    val d2 = try { t2.completedAt?.let { sdf.parse(it) } } catch (e: Exception) { null }
                    when {
                        d1 == null && d2 == null -> t2.id.compareTo(t1.id)
                        d1 == null -> 1
                        d2 == null -> -1
                        else -> d2.compareTo(d1)
                    }
                }
            }
            "Oldest" -> {
                filteredTasks.sortedWith { t1, t2 ->
                    val d1 = try { t1.completedAt?.let { sdf.parse(it) } } catch (e: Exception) { null }
                    val d2 = try { t2.completedAt?.let { sdf.parse(it) } } catch (e: Exception) { null }
                    when {
                        d1 == null && d2 == null -> t1.id.compareTo(t2.id)
                        d1 == null -> 1
                        d2 == null -> -1
                        else -> d1.compareTo(d2)
                    }
                }
            }
            else -> filteredTasks
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = "Archive",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Chores Archive",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "All completed household tasks are safely preserved here for your reference. Unchecking any item will restore it back to your active tasks tab.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Category Filter Tabs
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(categories) { cat ->
                    val isSelected = selectedCategoryFilter == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategoryFilter = cat },
                        label = { Text(cat) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
        }

        // Section Header with Sort Dropdown Menu
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Archived Chores",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${sortedTasks.size} completed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                var expanded by remember { mutableStateOf(false) }
                Box {
                    AssistChip(
                        onClick = { expanded = true },
                        label = { Text("Sort: $selectedSort") },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select sort order",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Newest") },
                            onClick = {
                                selectedSort = "Newest"
                                expanded = false
                            },
                            leadingIcon = {
                                if (selectedSort == "Newest") {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Oldest") },
                            onClick = {
                                selectedSort = "Oldest"
                                expanded = false
                            },
                            leadingIcon = {
                                if (selectedSort == "Oldest") {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("A-Z") },
                            onClick = {
                                selectedSort = "A-Z"
                                expanded = false
                            },
                            leadingIcon = {
                                if (selectedSort == "A-Z") {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        // Task Items List
        if (sortedTasks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = "No completed tasks",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No archived tasks in this category.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(sortedTasks) { task ->
                TaskItemCard(task = task, viewModel = viewModel)
            }
        }
    }
}

