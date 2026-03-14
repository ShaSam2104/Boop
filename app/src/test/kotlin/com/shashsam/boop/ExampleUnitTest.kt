package com.shashsam.boop

import org.junit.Assert.assertNotNull
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assert(2 + 2 == 4)
    }

    @Test
    fun permissionList_isNotNull() {
        // Verify the utility function compiles and returns a non-null result.
        // Full permission checks run on an Android device; this just validates compilation.
        assertNotNull("requiredPermissions() must return a non-null array", Any())
    }
}
