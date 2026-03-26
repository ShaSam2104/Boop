package com.shashsam.boop.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import com.shashsam.boop.R
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
}
