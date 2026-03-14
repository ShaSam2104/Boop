package com.shashsam.boop

import com.shashsam.boop.utils.requiredPermissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun permissionList_isNotNull() {
        val permissions = requiredPermissions()
        assertNotNull("requiredPermissions() must return a non-null array", permissions)
        assertTrue("requiredPermissions() must return a non-empty array", permissions.isNotEmpty())
    }
}
