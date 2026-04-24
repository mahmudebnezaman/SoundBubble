package com.codezamlabs.soundbubble.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codezamlabs.soundbubble.data.BubbleShape
import com.codezamlabs.soundbubble.ui.theme.BubblePresetColors
import com.codezamlabs.soundbubble.viewmodel.BubbleSettingsViewModel

private val BubblePresetColorNames = listOf(
    "Royal", "Violet", "Teal", "Emerald", "Amber",
    "Red", "Pink", "Indigo", "Slate", "Dark",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BubbleSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: BubbleSettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Customize Bubble",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Live Preview
            val bubbleColor by animateColorAsState(
                targetValue = Color(settings.color),
                animationSpec = tween(300),
                label = "previewColor",
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    bubbleColor.copy(alpha = 0.08f),
                                    Color.Transparent,
                                ),
                                radius = 400f,
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val isButton = settings.shape == BubbleShape.BUTTON
                    val previewHeight = settings.size.dp
                    // Vertical pill: width driven by thickness, height = bubble size
                    val pillWidthDp = ((settings.size - 24) * settings.buttonThickness)
                        .coerceAtLeast(10f)
                    val previewWidth = if (isButton) pillWidthDp.dp else settings.size.dp
                    val previewShape = if (isButton) RoundedCornerShape(50) else CircleShape

                    // Shadow behind preview bubble
                    Box(
                        modifier = Modifier
                            .width(previewWidth + 4.dp)
                            .height(previewHeight + 4.dp)
                            .alpha(0.2f)
                            .clip(previewShape)
                            .background(bubbleColor),
                    )
                    // Preview bubble
                    Box(
                        modifier = Modifier
                            .width(previewWidth)
                            .height(previewHeight)
                            .alpha(settings.opacity)
                            .clip(previewShape)
                            .background(bubbleColor)
                            .border(
                                width = 1.5.dp,
                                color = Color.White.copy(alpha = 0.2f),
                                shape = previewShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!isButton) {
                            Icon(
                                imageVector = Icons.Filled.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size((settings.size * 0.45f).dp),
                                tint = Color.White,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Opacity Control
            SettingSection(
                title = "Opacity",
                value = "${(settings.opacity * 100).toInt()}%",
            ) {
                Slider(
                    value = settings.opacity,
                    onValueChange = { viewModel.setOpacity(it) },
                    valueRange = 0.2f..1.0f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    ),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Size Control
            SettingSection(
                title = "Size",
                value = "${settings.size.toInt()}dp",
            ) {
                Slider(
                    value = settings.size,
                    onValueChange = { viewModel.setSize(it) },
                    valueRange = 40f..100f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    ),
                )
            }

            AnimatedVisibility(
                visible = settings.shape == BubbleShape.BUTTON,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    Spacer(modifier = Modifier.height(20.dp))
                    SettingSection(
                        title = "Thickness",
                        value = "${(settings.buttonThickness * 100).toInt()}%",
                    ) {
                        Slider(
                            value = settings.buttonThickness,
                            onValueChange = { viewModel.setButtonThickness(it) },
                            valueRange = 0.3f..0.7f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            ),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Shape Selector
            ShapeSelector(
                selectedShape = settings.shape,
                onShapeSelected = { viewModel.setShape(it) },
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Color Picker
            Text(
                text = "COLOR",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.5.sp,
            )
            Spacer(modifier = Modifier.height(14.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                BubblePresetColors.forEachIndexed { index, color ->
                    val isSelected = color.toArgb() == settings.color
                    val colorName = BubblePresetColorNames.getOrElse(index) { "" }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(52.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            width = 2.5.dp,
                                            color = Color.White,
                                            shape = CircleShape,
                                        )
                                    } else {
                                        Modifier.border(
                                            width = 1.dp,
                                            color = Color.White.copy(alpha = 0.1f),
                                            shape = CircleShape,
                                        )
                                    },
                                )
                                .clickable { viewModel.setColor(color.toArgb()) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = colorName,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }

                // Custom color option
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(52.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.sweepGradient(
                                    listOf(
                                        Color(0xFFFF6B6B), Color(0xFFFFC93C), Color(0xFF6BCB77),
                                        Color(0xFF4FC3F7), Color(0xFF7C4DFF), Color(0xFFFF6B6B),
                                    ),
                                ),
                            )
                            .then(
                                if (BubblePresetColors.none { it.toArgb() == settings.color }) {
                                    Modifier.border(2.5.dp, Color.White, CircleShape)
                                } else {
                                    Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                                },
                            )
                            .clickable {
                                val customColors = listOf(
                                    0xFFFF5722.toInt(),
                                    0xFF795548.toInt(),
                                    0xFF009688.toInt(),
                                    0xFF3F51B5.toInt(),
                                    0xFFCDDC39.toInt(),
                                )
                                val currentIndex = customColors.indexOf(settings.color)
                                val nextIndex = (currentIndex + 1) % customColors.size
                                viewModel.setColor(customColors[nextIndex])
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "+",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Custom",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ShapeSelector(
    selectedShape: BubbleShape,
    onShapeSelected: (BubbleShape) -> Unit,
) {
    SettingSection(title = "Shape", value = if (selectedShape == BubbleShape.CIRCLE) "Circle" else "Button") {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BubbleShape.entries.forEach { shape ->
                val isSelected = shape == selectedShape
                val bgColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                } else {
                    Color.Transparent
                }
                val borderColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                }
                val borderWidth = if (isSelected) 2.dp else 1.dp
                val label = if (shape == BubbleShape.CIRCLE) "Circle" else "Button"

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgColor)
                        .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
                        .clickable { onShapeSelected(shape) }
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (shape == BubbleShape.CIRCLE) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.White,
                            )
                        }
                    } else {
                        // Vertical pill preview
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(40.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                ),
                        )
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSection(
    title: String,
    value: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.5.sp,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            content()
        }
    }
}
