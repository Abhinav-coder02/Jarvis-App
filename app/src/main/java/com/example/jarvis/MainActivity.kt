package com.example.jarvis

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private var jarvisService: JarvisService? = null
    private var isBound = mutableStateOf(false)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as JarvisService.JarvisBinder
            jarvisService = binder.getService()
            isBound.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            jarvisService = null
            isBound.value = false
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startJarvisService()
        } else {
            Toast.makeText(this, "Permissions required for Jarvis to operate properly.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRequiredPermissions()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0A0E14)
                ) {
                    JarvisDashboard(
                        isBound = isBound.value,
                        service = jarvisService,
                        onToggleService = { enable ->
                            if (enable) checkPermissionsAndStart() else stopJarvisService()
                        }
                    )
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun checkPermissionsAndStart() {
        requestRequiredPermissions()
        startJarvisService()
    }

    private fun startJarvisService() {
        val intent = Intent(this, JarvisService::class.java)
        startForegroundService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun stopJarvisService() {
        if (isBound.value) {
            unbindService(serviceConnection)
            isBound.value = false
        }
        val intent = Intent(this, JarvisService::class.java)
        stopService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound.value) {
            unbindService(serviceConnection)
            isBound.value = false
        }
    }
}

@Composable
fun JarvisDashboard(
    isBound: Boolean,
    service: JarvisService?,
    onToggleService: (Boolean) -> Unit
) {
    val state by service?.currentState?.collectAsState(initial = JarvisState.SLEEPING_LISTENING_FOR_WAKE_WORD)
        ?: remember { mutableStateOf(JarvisState.SLEEPING_LISTENING_FOR_WAKE_WORD) }

    val logs by service?.logFlow?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf(listOf("Service not connected.")) }

    val statusColor by animateColorAsState(
        targetValue = when (state) {
            JarvisState.SLEEPING_LISTENING_FOR_WAKE_WORD -> Color(0xFF00E5FF)
            JarvisState.LISTENING_FOR_COMMAND -> Color(0xFFFFD600)
            JarvisState.PROCESSING -> Color(0xFFFF9100)
            JarvisState.SPEAKING -> Color(0xFF00E676)
        },
        animationSpec = tween(500), label = "StatusColor"
    )

    val scaleAnimate by animateFloatAsState(
        targetValue = if (state == JarvisState.LISTENING_FOR_COMMAND) 1.2f else 1.0f,
        animationSpec = tween(500), label = "Scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "JARVIS",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF00E5FF)
                )
                Text(
                    text = if (isBound) "SYSTEM ACTIVE" else "SYSTEM OFFLINE",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (isBound) Color.Gray else Color.Red
                )
            }
            IconButton(
                onClick = { onToggleService(!isBound) },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isBound) Color(0xFF1E293B) else Color(0xFF331010))
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Toggle Service",
                    tint = if (isBound) Color(0xFF00E5FF) else Color.Red
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Futuristic Visualizer Ring
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(200.dp)
                .scale(scaleAnimate)
                .border(
                    width = 4.dp,
                    brush = Brush.radialGradient(
                        colors = listOf(statusColor, Color.Transparent)
                    ),
                    shape = CircleShape
                )
                .border(2.dp, statusColor, CircleShape)
                .background(Color(0xFF0F172A), CircleShape)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { if (isBound) service?.triggerManualListening() },
                    modifier = Modifier.size(80.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Manual Mic",
                        modifier = Modifier.fillMaxSize(),
                        tint = statusColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = when (state) {
                JarvisState.SLEEPING_LISTENING_FOR_WAKE_WORD -> "Listening for 'Jarvis'..."
                JarvisState.LISTENING_FOR_COMMAND -> "Listening for command..."
                JarvisState.PROCESSING -> "Processing..."
                JarvisState.SPEAKING -> "Speaking..."
            },
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace,
            color = statusColor
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Activity Logs Terminal
        Text(
            text = "SYSTEM LOGS",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color.LightGray,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF030712))
                .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            LazyColumn(
                reverseLayout = false,
                modifier = Modifier.fillMaxSize()
            ) {
                items(logs) { log ->
                    Text(
                        text = log,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Made by Abhinav Kumar",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.Gray
        )
    }
}