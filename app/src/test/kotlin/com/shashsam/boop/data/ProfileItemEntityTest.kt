package com.shashsam.boop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProfileItemEntityTest {

    @Test
    fun `constructor sets all fields`() {
        val item = ProfileItemEntity(
            id = 1,
            type = "link",
            label = "GitHub",
            value = "https://github.com/user",
            size = "half",
            sortOrder = 0
        )
        assertEquals(1L, item.id)
        assertEquals("link", item.type)
        assertEquals("GitHub", item.label)
        assertEquals("https://github.com/user", item.value)
        assertEquals("half", item.size)
        assertEquals(0, item.sortOrder)
    }

    @Test
    fun `default id is zero`() {
        val item = ProfileItemEntity(
            type = "email",
            label = "Work",
            value = "test@example.com",
            size = "full",
            sortOrder = 1
        )
        assertEquals(0L, item.id)
    }

    @Test
    fun `copy preserves fields`() {
        val item = ProfileItemEntity(
            id = 5,
            type = "phone",
            label = "Mobile",
            value = "+1234567890",
            size = "half",
            sortOrder = 2
        )
        val copy = item.copy(label = "Home")
        assertEquals("Home", copy.label)
        assertEquals("phone", copy.type)
        assertEquals("+1234567890", copy.value)
        assertEquals(5L, copy.id)
    }

    @Test
    fun `equality check`() {
        val a = ProfileItemEntity(id = 1, type = "link", label = "X", value = "v", size = "half", sortOrder = 0)
        val b = ProfileItemEntity(id = 1, type = "link", label = "X", value = "v", size = "half", sortOrder = 0)
        assertEquals(a, b)
    }

    @Test
    fun `different sortOrder means not equal`() {
        val a = ProfileItemEntity(id = 1, type = "link", label = "X", value = "v", size = "half", sortOrder = 0)
        val b = a.copy(sortOrder = 1)
        assertNotEquals(a, b)
    }
}
