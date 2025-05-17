package com.example.paivalocker

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
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
import com.example.paivalocker.service.OverlayService
import com.example.paivalocker.ui.theme.PaivaLockerTheme
import kotlinx.coroutines.launch
import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import android.provider.Settings
import android.net.Uri
import android.widget.Toast
import android.os.Build

data class AppInfo(
    val name: String,
    val packageName: String,
    val isSystemApp: Boolean,
    val icon: Drawable
)

class MainActivity : ComponentActivity() {
    private lateinit var appPreferences: AppPreferences

    private val requestUsageStatsPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Check if permission was granted
        if (hasUsageStatsPermission()) {
            startService(Intent(this, AppMonitorService::class.java))
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestUsageStatsPermission() {
        if (!hasUsageStatsPermission()) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            requestUsageStatsPermission.launch(intent)
        } else {
            startService(Intent(this, AppMonitorService::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appPreferences = AppPreferences(this)
        
        // Request usage stats permission and start service
        requestUsageStatsPermission()
        
        // Request overlay permission if not granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
        }
        
        enableEdgeToEdge()
        setContent {
            PaivaLockerTheme {
                var isSelectionMode by remember { mutableStateOf(false) }
                var selectedApps by remember { mutableStateOf(setOf<String>()) }
                val scope = rememberCoroutineScope()
                val lockedApps by appPreferences.lockedApps.collectAsStateWithLifecycle(initialValue = emptySet())

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("PaivaLocker") },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                                actionIconContentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            actions = {
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
                                } else {
                                    TextButton(
                                        onClick = { 
                                            selectedApps = lockedApps
                                            isSelectionMode = true 
                                        }
                                    ) {
                                        Text("Modify")
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    // Permission granted, start the service
                    startService(Intent(this, OverlayService::class.java))
                } else {
                    // Permission denied
                    Toast.makeText(this, "Overlay permission is required", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        private const val OVERLAY_PERMISSION_REQ_CODE = 1234
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
    var searchQuery by remember { mutableStateOf("") }
    var showOnlyLocked by remember { mutableStateOf(false) }
    
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

    val filteredApps = remember(searchQuery, apps, showOnlyLocked, lockedApps) {
        apps.filter { app ->
            val matchesSearch = searchQuery.isEmpty() ||
                app.name.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            
            val matchesLockedFilter = !showOnlyLocked || app.packageName in lockedApps
            
            matchesSearch && matchesLockedFilter
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .weight(1f),
                placeholder = { Text("Search apps...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Checkbox(
                    checked = showOnlyLocked,
                    onCheckedChange = { showOnlyLocked = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.outline,
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                Text(
                    text = "Locked",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(filteredApps) { app ->
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
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
                    onCheckedChange = { onAppSelected(app.packageName, it) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.outline,
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary
                    )
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
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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