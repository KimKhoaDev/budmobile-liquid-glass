package com.phimbud.mobile.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phimbud.mobile.core.platform.PlatformBackHandler
import com.phimbud.mobile.ui.components.LiquidSliderThumb
import com.phimbud.mobile.ui.components.LiquidToggle
import com.phimbud.mobile.ui.components.liquidGlass
import com.phimbud.mobile.ui.components.liquidPress
import com.phimbud.mobile.ui.settings.GlassAccent
import com.phimbud.mobile.ui.settings.GlassSettings
import com.phimbud.mobile.ui.settings.LocalGlassSettings
import com.phimbud.mobile.ui.settings.LocalGlassSettingsController
import com.phimbud.mobile.ui.theme.NetflixBlack
import com.phimbud.mobile.ui.theme.NetflixRed
import com.phimbud.mobile.ui.theme.NetflixTextPrimary
import com.phimbud.mobile.ui.theme.NetflixTextSecondary
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val controller = LocalGlassSettingsController.current ?: return
    val settings = LocalGlassSettings.current

    PlatformBackHandler(enabled = true) { onBackClick() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.liquidPress().liquidGlass(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Quay lại",
                    tint = Color.White
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = "Cài đặt giao diện",
                color = NetflixTextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        GlassPreview(settings)

        SettingsCard {
            ToggleRow(
                title = "Hiệu ứng Liquid Glass",
                subtitle = "Tắt để dùng bề mặt mờ phẳng, nhẹ máy hơn",
                checked = settings.enabled,
                onCheckedChange = { v -> controller.update { it.copy(enabled = v) } }
            )
        }

        SettingsCard {
            ToggleRow(
                title = "Vật lý khi chạm",
                subtitle = "Nút và chip phồng lên rồi nảy về như lò xo",
                checked = settings.pressPhysics,
                onCheckedChange = { v -> controller.update { it.copy(pressPhysics = v) } }
            )
            Spacer(Modifier.height(16.dp))
            ToggleRow(
                title = "Khúc xạ ánh sáng",
                subtitle = "Thanh điều hướng và nút Quay lại bẻ cong nội dung phía sau (chỉ Android)",
                checked = settings.refraction,
                onCheckedChange = { v -> controller.update { it.copy(refraction = v) } }
            )
        }

        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Độ đậm của kính",
                    color = NetflixTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(settings.intensity * 100f).roundToInt()}%",
                    color = NetflixTextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            val interaction = remember { MutableInteractionSource() }
            Slider(
                value = settings.intensity,
                onValueChange = { v -> controller.update { it.copy(intensity = v) } },
                valueRange = GlassSettings.MIN_INTENSITY..GlassSettings.MAX_INTENSITY,
                colors = SliderDefaults.colors(
                    thumbColor = settings.accent.color,
                    activeTrackColor = settings.accent.color,
                    inactiveTrackColor = Color.Gray.copy(alpha = 0.4f)
                ),
                interactionSource = interaction,
                thumb = { LiquidSliderThumb(interaction) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingsCard {
            Text(
                text = "Màu nhấn",
                color = NetflixTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GlassAccent.entries.forEach { accent ->
                    AccentSwatch(
                        accent = accent,
                        selected = accent == settings.accent,
                        onClick = { controller.update { it.copy(accent = accent) } }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidPress()
                .liquidGlass(RoundedCornerShape(50), fillAlpha = 0.12f)
                .clip(RoundedCornerShape(50))
                .clickable { controller.reset() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Khôi phục mặc định",
                color = NetflixTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** Live preview: sample controls over a colourful backdrop so every setting is visible at once. */
@Composable
private fun GlassPreview(settings: GlassSettings) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF7A1F3D), Color(0xFF1B2A6B), Color(0xFF0F6B5E))
                )
            )
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .liquidPress()
                    .liquidGlass(RoundedCornerShape(50), tint = Color.White, fillAlpha = 0.23f)
                    .clip(RoundedCornerShape(50))
                    .clickable { }
                    .padding(horizontal = 22.dp, vertical = 11.dp)
            ) {
                Text("Phát", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Box(
                Modifier
                    .liquidPress()
                    .liquidGlass(RoundedCornerShape(50), tint = NetflixRed, fillAlpha = 0.22f)
                    .clip(RoundedCornerShape(50))
                    .clickable { }
                    .padding(horizontal = 18.dp, vertical = 11.dp)
            ) {
                Text("Hành động", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Box(
                Modifier
                    .liquidPress()
                    .liquidGlass(CircleShape)
                    .clip(CircleShape)
                    .clickable { }
                    .size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
        Text(
            text = "Xem trước",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(24.dp), fillAlpha = 0.09f)
            .clip(RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = NetflixTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, color = NetflixTextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        LiquidToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AccentSwatch(
    accent: GlassAccent,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .liquidPress()
            .size(38.dp)
            .clip(CircleShape)
            .background(accent.color)
            .then(
                if (selected) Modifier.border(2.5.dp, Color.White, CircleShape) else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = accent.label,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
