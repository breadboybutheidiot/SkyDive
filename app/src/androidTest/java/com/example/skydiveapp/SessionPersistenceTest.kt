package com.example.skydiveapp

import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SessionPersistenceTest {
    @Test fun encryptedSessionSurvivesNewAuthInstanceAndIsRemovedOnClear() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        val folder = File(app.cacheDir, "session-test-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(app) {
            override fun getNoBackupFilesDir(): File = folder
        }
        try {
            val first = AuthSession("test-access-token", "test-refresh-token", "test@example.com", 2_000_000_000L)
            SupabaseAuth(isolated).save(first)
            val contents = File(folder, "auth_session").readText()
            assertFalse(contents.contains(first.accessToken))
            assertFalse(contents.contains(first.refreshToken))
            assertEquals(first, SupabaseAuth(isolated).load())

            val updated = first.copy(refreshToken = "rotated-refresh-token")
            SupabaseAuth(isolated).save(updated)
            val restored = SupabaseAuth(isolated)
            assertEquals(updated, restored.load())
            restored.clear()
            assertNull(SupabaseAuth(isolated).load())
        } finally {
            folder.deleteRecursively()
        }
    }
}
