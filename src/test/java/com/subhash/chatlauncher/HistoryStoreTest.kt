package com.subhash.chatlauncher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryStoreTest {

    private lateinit var store: HistoryStore

    @Before
    fun setUp() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("history_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = HistoryStore(prefs)
    }

    @Test
    fun appendsAndReadsNewestFirst() {
        store.append("user", "local", "hello", now = 1000)
        store.append("assistant", "local", "hi there", now = 2000)
        store.append("assistant", "cloud", "cloud reply", now = 3000)
        val all = store.all()
        assertEquals(3, all.size)
        assertEquals("cloud reply", all[0].text)
        assertEquals("cloud", all[0].source)
        assertEquals("hello", all[2].text)
        assertEquals("user", all[2].role)
    }

    @Test
    fun capsAtThreeHundred() {
        for (i in 1..305) store.append("user", "local", "line $i", now = i.toLong())
        val all = store.all()
        assertEquals(300, all.size)
        assertEquals("line 305", all[0].text)
        assertEquals("line 6", all[299].text)
    }

    @Test
    fun blankTextNotRecorded() {
        store.append("assistant", "local", "  ")
        assertTrue(store.all().isEmpty())
    }
}
