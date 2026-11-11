package io.github.lucf15.localai.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.lucf15.localai.sdk.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder

@RunWith(AndroidJUnit4::class)
class ReaderBoundaryTest {
    @Test fun sdkRejectsInvalidUtf8SizeAndMapsDeniedAccess() = runBlocking {
        val client = LocalAiClient(InstrumentationRegistry.getInstrumentation().targetContext)
        suspend fun expect(error: LocalAiError, text: String) {
            try { client.transform(TransformRequest(Capability.SUMMARIZE, text)).collect(); fail("Expected $error") }
            catch (actual: LocalAiException) { assertEquals(error, actual.error) }
        }
        expect(LocalAiError.INVALID_INPUT, " ")
        expect(LocalAiError.INPUT_TOO_LARGE, "é".repeat(513))
        // Explicit mode: routine SDK tests never accidentally start native inference or download weights.
        if (InstrumentationRegistry.getArguments().getString("grant_mode") == "denied") {
            expect(LocalAiError.PERMISSION_DENIED, SAMPLE_TEXT)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) = Unit
                override fun onServiceDisconnected(name: ComponentName) = Unit
            }
            val blocked = try {
                val bound = context.bindService(Intent().setComponent(ComponentName(LocalAiClient.MANAGER,
                    "${LocalAiClient.MANAGER}.service.LocalAiService")), connection, Context.BIND_AUTO_CREATE)
                if (bound) context.unbindService(connection)
                !bound
            } catch (_: SecurityException) { true }
            assertTrue("Android must protect the service even without the SDK's preflight check", blocked)
        }
    }
}
