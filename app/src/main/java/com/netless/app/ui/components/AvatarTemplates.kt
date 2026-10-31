// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

package com.netless.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Curated Anime Avatar templates with character themes, emojis, and vibrant chakra gradients
 */
data class AvatarTemplate(
    val id: Int,
    val name: String,
    val anime: String,
    val emoji: String,
    val gradient: List<Color>,
    val icon: ImageVector
)

val AVATAR_TEMPLATES = listOf(
    AvatarTemplate(
        id = 0,
        name = "Naruto",
        anime = "Naruto Shippuden",
        emoji = "🍥",
        gradient = listOf(Color(0xFFFF5722), Color(0xFFFFB300)),
        icon = Icons.Default.Bolt
    ),
    AvatarTemplate(
        id = 1,
        name = "Zoro",
        anime = "One Piece",
        emoji = "⚔️",
        gradient = listOf(Color(0xFF047857), Color(0xFF10B981)),
        icon = Icons.Default.Security
    ),
    AvatarTemplate(
        id = 2,
        name = "Luffy",
        anime = "One Piece",
        emoji = "👒",
        gradient = listOf(Color(0xFFDC2626), Color(0xFFF59E0B)),
        icon = Icons.Default.Star
    ),
    AvatarTemplate(
        id = 3,
        name = "Goku",
        anime = "Dragon Ball Z",
        emoji = "⚡",
        gradient = listOf(Color(0xFFEA580C), Color(0xFFFBBF24)),
        icon = Icons.Default.Bolt
    ),
    AvatarTemplate(
        id = 4,
        name = "Gojo",
        anime = "Jujutsu Kaisen",
        emoji = "👁️",
        gradient = listOf(Color(0xFF0284C7), Color(0xFF6366F1)),
        icon = Icons.Default.Spa
    ),
    AvatarTemplate(
        id = 5,
        name = "Tanjiro",
        anime = "Demon Slayer",
        emoji = "🌊",
        gradient = listOf(Color(0xFF0F766E), Color(0xFF059669)),
        icon = Icons.Default.Explore
    ),
    AvatarTemplate(
        id = 6,
        name = "Itachi",
        anime = "Naruto Shippuden",
        emoji = "🦅",
        gradient = listOf(Color(0xFF991B1B), Color(0xFF1E1B4B)),
        icon = Icons.Default.Pets
    ),
    AvatarTemplate(
        id = 7,
        name = "Kakashi",
        anime = "Naruto Shippuden",
        emoji = "🐺",
        gradient = listOf(Color(0xFF2563EB), Color(0xFF64748B)),
        icon = Icons.Default.Flight
    ),
    AvatarTemplate(
        id = 8,
        name = "Sukuna",
        anime = "Jujutsu Kaisen",
        emoji = "🔥",
        gradient = listOf(Color(0xFF7F1D1D), Color(0xFFC026D3)),
        icon = Icons.Default.Bolt
    ),
    AvatarTemplate(
        id = 9,
        name = "Saitama",
        anime = "One Punch Man",
        emoji = "🥊",
        gradient = listOf(Color(0xFFEAB308), Color(0xFFEF4444)),
        icon = Icons.Default.Star
    )
)

fun getAvatarTemplate(id: Int): AvatarTemplate {
    return AVATAR_TEMPLATES.getOrNull(id) ?: AVATAR_TEMPLATES[0]
}

@Composable
fun AvatarCircle(
    avatarId: Int,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val template = getAvatarTemplate(avatarId)
    val clickModifier = if (onClick != null) modifier.clickable { onClick() } else modifier

    Box(
        modifier = clickModifier.size(size + if (isSelected) 6.dp else 0.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .shadow(elevation = if (isSelected) 8.dp else 2.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(Brush.linearGradient(template.gradient))
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) Color(0xFF10B981) else Color.White.copy(alpha = 0.5f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = template.emoji,
                fontSize = (size.value * 0.46f).sp
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(size * 0.38f)
                    .align(Alignment.BottomEnd)
                    .shadow(elevation = 4.dp, shape = CircleShape)
                    .background(Color(0xFF10B981), CircleShape)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.24f)
                )
            }
        }
    }
}
