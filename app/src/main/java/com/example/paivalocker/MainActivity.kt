package com.example.paivalocker

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.paivalocker.data.AppPreferences
import com.example.paivalocker.service.AppMonitorService
import com.example.paivalocker.ui.theme.PaivaLockerTheme
import kotlinx.coroutines.launch

data class AppInfo(
    val name: String,
    val packageName: String,
    val isSystemApp: Boolean,
    val icon: Drawable
)

class MainActivity : ComponentActivity() {
    private lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appPreferences = AppPreferences(this)
        
        // Start the monitoring service
        startService(Intent(this, AppMonitorService::class.java))
        
        enableEdgeToEdge()
        setContent {
            PaivaLockerTheme {
                var isSelectionMode by remember { mutableStateOf(false) }
                var selectedApps by remember { mutableStateOf(setOf<String>()) }
                val scope = rememberCoroutineScope()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("PaivaLocker") },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            actions = {
                                TextButton(
                                    onClick = { isSelectionMode = !isSelectionMode }
                                ) {
                                    Text(if (isSelectionMode) "Done" else "Modify")
                                }
                                
                                if (isSelectionMode) {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                appPreferences.setLockedApps(selectedApps)
                                            }
                                            isSelectionMode = false
                                        }
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = "Save")
                                    }
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    AppList(
                        modifier = Modifier.padding(innerPadding),
                        isSelectionMode = isSelectionMode,
                        selectedApps = selectedApps,
                        onAppSelected = { packageName, isSelected ->
                            selectedApps = if (isSelected) {
                                selectedApps + packageName
                            } else {
                                selectedApps - packageName
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AppList(
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    selectedApps: Set<String> = emptySet(),
    onAppSelected: (String, Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val appPreferences = remember { AppPreferences(context) }
    val lockedApps by appPreferences.lockedApps.collectAsStateWithLifecycle(initialValue = emptySet())
    
    val apps = remember {
        packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .map { app ->
                AppInfo(
                    name = packageManager.getApplicationLabel(app).toString(),
                    packageName = app.packageName,
                    isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    icon = packageManager.getApplicationIcon(app.packageName)
                )
            }
            .sortedWith(
                compareBy<AppInfo> { it.isSystemApp }
                    .thenBy { it.name.lowercase() }
            )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(apps) { app ->
            AppItem(
                app = app,
                isSelectionMode = isSelectionMode,
                isSelected = app.packageName in selectedApps,
                isLocked = app.packageName in lockedApps,
                onAppSelected = onAppSelected
            )
        }
    }
}

@Composable
fun AppItem(
    app: AppInfo,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isLocked: Boolean,
    onAppSelected: (String, Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onAppSelected(app.packageName, it) }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            androidx.compose.foundation.Image(
                bitmap = app.icon.toBitmap().asImageBitmap(),
                contentDescription = "App icon for ${app.name}",
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (app.isSystemApp) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "System App",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                if (isLocked) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Locked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}