package com.shashsam.boop.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UlidTest {

    @Test
    fun `generateUlid returns 26 characters`() {
        val ulid = generateUlid()
        assertEquals(26, ulid.length)
    }

    @Test
    fun `generateUlid uses Crockford Base32 characters only`() {
        val valid = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toSet()
        val ulid = generateUlid()
        assertTrue("ULID should only contain Crockford Base32 chars, got: $ulid",
            ulid.all { it in valid })
    }

    @Test
    fun `generateUlid produces unique values`() {
        val ulids = (1..100).map { generateUlid() }.toSet()
        assertEquals("100 generated ULIDs should all be unique", 100, ulids.size)
    }

    @Test
    fun `generateUlid is lexicographically sortable by time`() {
        val first = generateUlid()
        Thread.sleep(2) // ensure different timestamp
        val second = generateUlid()
        assertTrue("Later ULID should sort after earlier one", second > first)
    }

    @Test
    fun `two sequential ULIDs are different`() {
        val a = generateUlid()
        val b = generateUlid()
        assertNotEquals(a, b)
    }
}
