package io.github.immaghzbad.aetherst.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowScope
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.window.WindowDraggableArea

@Composable
fun WindowScope.AppTitleBar(
    onMinimize: () -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        WindowDraggableArea(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(Color(0xFF0A0A0B)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val iconPainter = runCatching { painterResource("icon.png") }.getOrNull()
                Box(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(18.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF007AFF)),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconPainter != null) {
                        Image(
                            painter = iconPainter,
                            contentDescription = "AetherST",
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = "A",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                Text(
                    text = "AetherST Tunnel",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFEBEBF5).copy(alpha = 0.9f),
                    modifier = Modifier.padding(start = 8.dp)
                )
                Spacer(modifier = Modifier.weight(1f))

                TitleBarButton(
                    label = "\u2013",
                    onClick = onMinimize,
                    hoverColor = Color(0xFF2C2C2E)
                )
                TitleBarButton(
                    icon = { Icons.Default.Close },
                    onClick = onClose,
                    hoverColor = Color(0xFFFF453A)
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF1C1C1E))
    }
}

@Composable
private fun TitleBarButton(
    label: String? = null,
    icon: (() -> androidx.compose.ui.graphics.vector.ImageVector)? = null,
    onClick: () -> Unit,
    hoverColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val contentColor = if (isHovered) Color.White else Color(0xFF8E8E93)

    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier.size(40.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(if (isHovered) hoverColor else Color.Transparent, shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (label != null) {
                Text(
                    text = label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon(),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}