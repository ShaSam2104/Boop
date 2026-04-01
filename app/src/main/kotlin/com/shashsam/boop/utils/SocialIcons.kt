package com.shashsam.boop.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.shashsam.boop.R
import com.shashsam.boop.ui.theme.SocialFacebook
import com.shashsam.boop.ui.theme.SocialInstagram
import com.shashsam.boop.ui.theme.SocialLinkedIn
import com.shashsam.boop.ui.theme.SocialYouTube

/**
 * Resolves a social icon for a profile item based on its type and value.
 *
 * @return Either a Material [ImageVector] or an [Int] drawable resource ID.
 */
fun resolveSocialIcon(type: String, value: String): Any {
    return when (type) {
        "email" -> Icons.Filled.Email
        "phone" -> Icons.Filled.Phone
        "link" -> resolveLinkIcon(value)
        else -> Icons.Filled.Language
    }
}

private fun resolveLinkIcon(url: String): Any {
    val lower = url.lowercase()
    return when {
        "github.com" in lower -> R.drawable.ic_github
        "twitter.com" in lower || "x.com" in lower -> R.drawable.ic_twitter
        "instagram.com" in lower -> R.drawable.ic_instagram
        "linkedin.com" in lower -> R.drawable.ic_linkedin
        "youtube.com" in lower -> R.drawable.ic_youtube
        "facebook.com" in lower -> R.drawable.ic_facebook
        else -> Icons.Filled.Language
    }
}

/** Domain patterns used for icon resolution. Exposed for testing. */
val SOCIAL_DOMAINS = mapOf(
    "github.com" to "GitHub",
    "twitter.com" to "Twitter",
    "x.com" to "Twitter",
    "instagram.com" to "Instagram",
    "linkedin.com" to "LinkedIn",
    "youtube.com" to "YouTube",
    "facebook.com" to "Facebook"
)

/**
 * Resolves the brand color for a social icon based on type and value.
 * GitHub/Twitter use white; Instagram, LinkedIn, YouTube, Facebook use brand colors.
 * Email, phone, and unknown links use [fallbackColor].
 */
fun resolveSocialIconColor(type: String, value: String, fallbackColor: Color): Color {
    return when (type) {
        "email", "phone" -> fallbackColor
        "link" -> resolveLinkIconColor(value, fallbackColor)
        else -> fallbackColor
    }
}

private fun resolveLinkIconColor(url: String, fallbackColor: Color): Color {
    val lower = url.lowercase()
    return when {
        "github.com" in lower -> Color.White
        "twitter.com" in lower || "x.com" in lower -> Color.White
        "instagram.com" in lower -> SocialInstagram
        "linkedin.com" in lower -> SocialLinkedIn
        "youtube.com" in lower -> SocialYouTube
        "facebook.com" in lower -> SocialFacebook
        else -> fallbackColor
    }
}
