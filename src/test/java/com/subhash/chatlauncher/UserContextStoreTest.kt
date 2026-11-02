package com.subhash.chatlauncher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserContextStoreTest {

    private lateinit var store: UserContextStore

    @Before
    fun setUp() {
        val c = ApplicationProvider.getApplicationContext<Context>()
        c.getSharedPreferences("user_context_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        store = UserContextStore(c)
    }

    @Test
    fun emptyByDefault() {
        assertTrue(store.isEmpty())
        assertNull(store.goal())
    }

    @Test
    fun addAndReadBack() {
        store.add(UserContextStore.QUESTIONS[0], "drums", ts = 1000L)
        store.add(UserContextStore.QUESTIONS[1], "doomscrolling", ts = 1001L)
        val e = store.entries()
        assertEquals(2, e.size)
        assertEquals("drums", e[0].answer)
        assertEquals("drums", store.goal())
    }

    @Test
    fun persistsAcrossInstances() {
        store.add(UserContextStore.QUESTIONS[0], "read more", ts = 1000L)
        val fresh = UserContextStore(ApplicationProvider.getApplicationContext())
        assertEquals("read more", fresh.goal())
    }
}
