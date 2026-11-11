package io.github.lucf15.localai.reader

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.lucf15.localai.sdk.*
import io.github.lucf15.localai.transport.v1.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TransportLifecycleProbe {
    private fun context(): Context {
        check(InstrumentationRegistry.getArguments().getString("run_local_inference") == "true")
        return InstrumentationRegistry.getInstrumentation().targetContext
    }
    @Test fun unacknowledgedProgressIsBoundedAndCancellationDrainsNativeWork() {
        val context = context()
        val ready = CountDownLatch(1)
        var remote: IBinder? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) { remote = binder; ready.countDown() }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        assertTrue(context.bindService(Intent().setComponent(ComponentName(LocalAiClient.MANAGER,
            "${LocalAiClient.MANAGER}.service.LocalAiService")), connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            val service = ILocalAiService.Stub.asInterface(checkNotNull(remote))
            val events = LinkedBlockingQueue<CapabilityEvent>()
            val operation = service.submit(CapabilityRequest().apply {
                capability = "summarize"; language = "it"; text = SAMPLE_TEXT
            }, object : ICapabilityCallback.Stub() {
                override fun onEvent(event: CapabilityEvent) { events.offer(event) }
            })
            val first = checkNotNull(events.poll(300, TimeUnit.SECONDS))
            assertEquals(CapabilityEvent.PROGRESS, first.status)
            assertTrue(first.text.isNotBlank())
            // The runtime keeps generating, but this client intentionally never acknowledges.
            assertNull("Only one progress transaction may remain unacknowledged", events.poll(500, TimeUnit.MILLISECONDS))
            operation.cancel()
            val terminal = checkNotNull(events.poll(30, TimeUnit.SECONDS))
            assertEquals(CapabilityEvent.CANCELLED, terminal.status)
            assertNull(events.poll(200, TimeUnit.MILLISECONDS))
        } finally { context.unbindService(connection) }
    }

    @Test fun managerDeathClosesSdkStreamWithoutReplay() = runBlocking {
        val context = context()
        var stopped = false
        try {
            withTimeout(360_000) {
                LocalAiClient(context).transform(TransformRequest(Capability.SUMMARIZE, SAMPLE_TEXT)).collect { update ->
                    if (update is TransformUpdate.Text && !stopped) {
                        assertFalse(update.complete)
                        stopped = true
                        // Test shell stops only our manager, preserving all installed models and grants.
                        InstrumentationRegistry.getInstrumentation().uiAutomation
                            .executeShellCommand("am force-stop io.github.lucf15.localai.runner").close()
                    }
                }
            }
            fail("A dead manager must not produce a silent success or replay")
        } catch (error: LocalAiException) { assertEquals(LocalAiError.MANAGER_UNAVAILABLE, error.error) }
        assertTrue(stopped)
    }
}
