package com.shashsam.boop.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.ui.graphics.Color
import com.shashsam.boop.R
import com.shashsam.boop.ui.theme.SocialFacebook
import com.shashsam.boop.ui.theme.SocialInstagram
import com.shashsam.boop.ui.theme.SocialLinkedIn
import com.shashsam.boop.ui.theme.SocialYouTube
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialIconsTest {

    @Test
    fun `email type returns Email icon`() {
        val result = resolveSocialIcon("email", "test@example.com")
        assertEquals(Icons.Filled.Email, result)
    }

    @Test
    fun `phone type returns Phone icon`() {
        val result = resolveSocialIcon("phone", "+1234567890")
        assertEquals(Icons.Filled.Phone, result)
    }

    @Test
    fun `github link returns github drawable`() {
        val result = resolveSocialIcon("link", "https://github.com/user")
        assertEquals(R.drawable.ic_github, result)
    }

    @Test
    fun `twitter link returns twitter drawable`() {
        val result = resolveSocialIcon("link", "https://twitter.com/user")
        assertEquals(R.drawable.ic_twitter, result)
    }

    @Test
    fun `x dot com link returns twitter drawable`() {
        val result = resolveSocialIcon("link", "https://x.com/user")
        assertEquals(R.drawable.ic_twitter, result)
    }

    @Test
    fun `instagram link returns instagram drawable`() {
        val result = resolveSocialIcon("link", "https://instagram.com/user")
        assertEquals(R.drawable.ic_instagram, result)
    }

    @Test
    fun `linkedin link returns linkedin drawable`() {
        val result = resolveSocialIcon("link", "https://linkedin.com/in/user")
        assertEquals(R.drawable.ic_linkedin, result)
    }

    @Test
    fun `youtube link returns youtube drawable`() {
        val result = resolveSocialIcon("link", "https://youtube.com/channel")
        assertEquals(R.drawable.ic_youtube, result)
    }

    @Test
    fun `facebook link returns facebook drawable`() {
        val result = resolveSocialIcon("link", "https://facebook.com/user")
        assertEquals(R.drawable.ic_facebook, result)
    }

    @Test
    fun `unknown link returns Language icon`() {
        val result = resolveSocialIcon("link", "https://example.com")
        assertEquals(Icons.Filled.Language, result)
    }

    @Test
    fun `unknown type returns Language icon`() {
        val result = resolveSocialIcon("unknown", "whatever")
        assertEquals(Icons.Filled.Language, result)
    }

    @Test
    fun `SOCIAL_DOMAINS contains expected keys`() {
        assertTrue(SOCIAL_DOMAINS.containsKey("github.com"))
        assertTrue(SOCIAL_DOMAINS.containsKey("twitter.com"))
        assertTrue(SOCIAL_DOMAINS.containsKey("x.com"))
        assertTrue(SOCIAL_DOMAINS.containsKey("instagram.com"))
        assertTrue(SOCIAL_DOMAINS.containsKey("linkedin.com"))
        assertTrue(SOCIAL_DOMAINS.containsKey("youtube.com"))
        assertTrue(SOCIAL_DOMAINS.containsKey("facebook.com"))
        assertEquals(7, SOCIAL_DOMAINS.size)
    }

    // ── resolveSocialIconColor tests ─────────────────────────────────────

    private val fallback = Color(0xFFF8FFA3) // accent yellow

    @Test
    fun `icon color for github is white`() {
        assertEquals(Color.White, resolveSocialIconColor("link", "https://github.com/user", fallback))
    }

    @Test
    fun `icon color for twitter is white`() {
        assertEquals(Color.White, resolveSocialIconColor("link", "https://twitter.com/user", fallback))
    }

    @Test
    fun `icon color for x dot com is white`() {
        assertEquals(Color.White, resolveSocialIconColor("link", "https://x.com/user", fallback))
    }

    @Test
    fun `icon color for instagram is brand pink`() {
        assertEquals(SocialInstagram, resolveSocialIconColor("link", "https://instagram.com/user", fallback))
    }

    @Test
    fun `icon color for linkedin is brand blue`() {
        assertEquals(SocialLinkedIn, resolveSocialIconColor("link", "https://linkedin.com/in/user", fallback))
    }

    @Test
    fun `icon color for youtube is brand red`() {
        assertEquals(SocialYouTube, resolveSocialIconColor("link", "https://youtube.com/channel", fallback))
    }

    @Test
    fun `icon color for facebook is brand blue`() {
        assertEquals(SocialFacebook, resolveSocialIconColor("link", "https://facebook.com/user", fallback))
    }

    @Test
    fun `icon color for email uses fallback`() {
        assertEquals(fallback, resolveSocialIconColor("email", "test@example.com", fallback))
    }

    @Test
    fun `icon color for phone uses fallback`() {
        assertEquals(fallback, resolveSocialIconColor("phone", "+1234567890", fallback))
    }

    @Test
    fun `icon color for unknown link uses fallback`() {
        assertEquals(fallback, resolveSocialIconColor("link", "https://example.com", fallback))
    }
}
