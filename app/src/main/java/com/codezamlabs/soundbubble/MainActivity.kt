package com.codezamlabs.soundbubble

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.codezamlabs.soundbubble.audio.StreamType
import com.codezamlabs.soundbubble.ui.screens.BubbleSettingsScreen
import com.codezamlabs.soundbubble.ui.screens.PermissionGuideScreen
import com.codezamlabs.soundbubble.ui.theme.SoundBubbleTheme
import com.codezamlabs.soundbubble.ui.theme.StreamAlarmColor
import com.codezamlabs.soundbubble.ui.theme.StreamCallColor
import com.codezamlabs.soundbubble.ui.theme.StreamMediaColor
import com.codezamlabs.soundbubble.ui.theme.StreamNotificationColor
import com.codezamlabs.soundbubble.ui.theme.StreamRingColor
import com.codezamlabs.soundbubble.viewmodel.MainViewModel
import com.codezamlabs.soundbubble.viewmodel.VolumeState
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var appUpdateManager: AppUpdateManager

    private val updateResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            // If the update is cancelled or fails, 
            // you can request to start the update again.
            Toast.makeText(this, "Update failed or cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appUpdateManager = AppUpdateManagerFactory.create(this)
        checkForUpdates()
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            SoundBubbleTheme {
                SoundBubbleNavHost()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkForUpdates() {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    updateResultLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                // If an in-app update is already running, resume the update.
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    updateResultLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                )
            }
        }
    }
}

@Composable
fun SoundBubbleNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                onNavigateToSettings = { navController.navigate("bubble_settings") },
                onNavigateToPermission = { navController.navigate("permission_guide") },
            )
        }
        composable("bubble_settings") {
            BubbleSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable("permission_guide") {
            PermissionGuideScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToPermission: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshState(context)
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val isWideLayout = LocalConfiguration.current.screenWidthDp >= 600

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Bubble Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { paddingValues ->
        if (isWideLayout) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                // Left pane: compact hero + permission banner + ringer mode
                Column(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 32.dp),
                ) {
                    // Compact hero
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(primaryColor, primaryColor.copy(alpha = 0.7f)),
                                    ),
                                )
                                .border(
                                    width = 2.dp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.3f),
                                            Color.White.copy(alpha = 0.05f),
                                        ),
                                    ),
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = Color.White,
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "SoundBubble",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Volume Control",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 2.sp,
                        )
                    }

                    // Permission banner (no extra horizontal padding — pane provides it)
                    if (!uiState.overlayPermissionGranted) {
                        Card(
                            onClick = onNavigateToPermission,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Overlay Permission Required",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Text(
                                        text = "Tap to enable floating bubble",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }

                    SectionLabel("Sound Profile")
                    Spacer(modifier = Modifier.height(10.dp))
                    RingerModeSelector(
                        currentMode = uiState.ringerMode,
                        onModeSelected = { viewModel.setRingerMode(context, it) },
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    BubbleToggleButton(
                        isRunning = uiState.serviceRunning,
                        hasPermission = uiState.overlayPermissionGranted,
                        onStart = { viewModel.startBubbleService(context) },
                        onStop = { viewModel.stopBubbleService(context) },
                        onRequestPermission = onNavigateToPermission,
                    )
                }

                // Right pane: volume sliders
                Column(
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 12.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
                ) {
                    SectionLabel("Volume")
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        VolumeSliderCard("Ring", Icons.Filled.PhoneAndroid, StreamRingColor, uiState.volumes[StreamType.RING]) { viewModel.setVolume(StreamType.RING, it) }
                        VolumeSliderCard("Media", Icons.Filled.MusicNote, StreamMediaColor, uiState.volumes[StreamType.MEDIA]) { viewModel.setVolume(StreamType.MEDIA, it) }
                        VolumeSliderCard("Alarm", Icons.Filled.Alarm, StreamAlarmColor, uiState.volumes[StreamType.ALARM]) { viewModel.setVolume(StreamType.ALARM, it) }
                        VolumeSliderCard("Call", Icons.Filled.Call, StreamCallColor, uiState.volumes[StreamType.CALL]) { viewModel.setVolume(StreamType.CALL, it) }
                        VolumeSliderCard("Notification", Icons.Filled.Notifications, StreamNotificationColor, uiState.volumes[StreamType.NOTIFICATION]) { viewModel.setVolume(StreamType.NOTIFICATION, it) }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                // Hero Header with gradient glow
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 28.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .align(Alignment.TopCenter)
                                .offset(y = (-20).dp),
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(primaryColor, primaryColor.copy(alpha = 0.7f)),
                                        ),
                                    )
                                    .border(
                                        width = 2.dp,
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.3f),
                                                Color.White.copy(alpha = 0.05f),
                                            ),
                                        ),
                                        shape = CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = Color.White,
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "SoundBubble",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Volume Control",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 2.sp,
                            )
                        }
                    }
                }

                // Permission Banner
                if (!uiState.overlayPermissionGranted) {
                    item {
                        Card(
                            onClick = onNavigateToPermission,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(bottom = 20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Overlay Permission Required",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Text(
                                        text = "Tap to enable floating bubble",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }
                }

                // Ringer Mode Card
                item {
                    SectionLabel("Sound Profile", Modifier.padding(horizontal = 20.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    RingerModeSelector(
                        currentMode = uiState.ringerMode,
                        onModeSelected = { viewModel.setRingerMode(context, it) },
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                }

                // Volume Controls
                item {
                    SectionLabel("Volume", Modifier.padding(horizontal = 20.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                }

                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    ) {
                        VolumeSliderCard("Ring", Icons.Filled.PhoneAndroid, StreamRingColor, uiState.volumes[StreamType.RING]) { viewModel.setVolume(StreamType.RING, it) }
                        VolumeSliderCard("Media", Icons.Filled.MusicNote, StreamMediaColor, uiState.volumes[StreamType.MEDIA]) { viewModel.setVolume(StreamType.MEDIA, it) }
                        VolumeSliderCard("Alarm", Icons.Filled.Alarm, StreamAlarmColor, uiState.volumes[StreamType.ALARM]) { viewModel.setVolume(StreamType.ALARM, it) }
                        VolumeSliderCard("Call", Icons.Filled.Call, StreamCallColor, uiState.volumes[StreamType.CALL]) { viewModel.setVolume(StreamType.CALL, it) }
                        VolumeSliderCard("Notification", Icons.Filled.Notifications, StreamNotificationColor, uiState.volumes[StreamType.NOTIFICATION]) { viewModel.setVolume(StreamType.NOTIFICATION, it) }
                    }
                }

                // Bubble Toggle
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    BubbleToggleButton(
                        isRunning = uiState.serviceRunning,
                        hasPermission = uiState.overlayPermissionGranted,
                        onStart = { viewModel.startBubbleService(context) },
                        onStop = { viewModel.stopBubbleService(context) },
                        onRequestPermission = onNavigateToPermission,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.5.sp,
        modifier = modifier,
    )
}

@Composable
private fun RingerModeSelector(
    currentMode: Int,
    onModeSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = listOf(
        Triple(AudioManager.RINGER_MODE_NORMAL, "Ring", Icons.Filled.VolumeUp),
        Triple(AudioManager.RINGER_MODE_VIBRATE, "Vibrate", Icons.Outlined.Vibration),
        Triple(AudioManager.RINGER_MODE_SILENT, "Silent", Icons.Filled.VolumeOff),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        modes.forEach { (mode, label, icon) ->
            val isSelected = currentMode == mode
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = tween(250),
                label = "ringerBg",
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(250),
                label = "ringerContent",
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor)
                    .clickable { onModeSelected(mode) }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    modifier = Modifier.size(18.dp),
                    tint = contentColor,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
            }
        }
    }
}

@Composable
private fun VolumeSliderCard(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    volumeState: VolumeState?,
    onValueChange: (Int) -> Unit,
) {
    val current = volumeState?.current ?: 0
    val max = volumeState?.max ?: 1
    val targetValue = if (max > 0) current.toFloat() / max.toFloat() else 0f
    val animatedValue by animateFloatAsState(
        targetValue = targetValue,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "vol_$label",
    )

    val outlineColor = accentColor.copy(alpha = 0.08f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // Subtle left accent bar
                drawRoundRect(
                    color = accentColor.copy(alpha = 0.6f),
                    topLeft = Offset(0f, size.height * 0.2f),
                    size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height * 0.6f),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(1.dp, outlineColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "$current",
                        style = MaterialTheme.typography.labelLarge,
                        color = accentColor,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Slider(
                    value = animatedValue,
                    onValueChange = { percent ->
                        onValueChange((percent * max).toInt())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = accentColor.copy(alpha = 0.15f),
                    ),
                )
            }
        }
    }
}

@Composable
private fun BubbleToggleButton(
    isRunning: Boolean,
    hasPermission: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isRunning) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(300),
        label = "bubbleBtnBg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isRunning) MaterialTheme.colorScheme.error else Color.White,
        animationSpec = tween(300),
        label = "bubbleBtnContent",
    )
    val borderColor = if (isRunning) MaterialTheme.colorScheme.error.copy(alpha = 0.3f) else Color.Transparent

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = if (isRunning) androidx.compose.foundation.BorderStroke(1.dp, borderColor) else null,
        onClick = {
            if (isRunning) onStop()
            else if (hasPermission) onStart()
            else onRequestPermission()
        },
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = contentColor,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (isRunning) "Stop Bubble" else "Start Bubble",
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
            )
        }
    }
}
