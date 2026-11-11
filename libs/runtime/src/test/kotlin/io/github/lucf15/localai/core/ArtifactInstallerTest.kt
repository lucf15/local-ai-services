package io.github.lucf15.localai.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.*

class ArtifactInstallerTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun model(server: MockWebServer, content: String) = ModelDescriptor(
        "test-model", "Test model", "owner/repo", "a".repeat(40),
        ModelArtifact("test.litertlm", content.toByteArray().size.toLong(), sha256(content.toByteArray()), server.url("/model").toString()),
    )

    private fun partial(root: File, model: ModelDescriptor) = File(root, "${model.id}/${model.revision}/${model.artifact.filename}.part").apply { parentFile.mkdirs() }

    @Test fun installsOnlyAfterIntegrityVerification() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("correct-model"))
            val model = model(server, "correct-model")
            val installer = ArtifactInstaller(temporary.newFolder(), allowHttpForTests = true)
            assertNull(installer.installedFile(model))
            val file = installer.install(model) { }
            assertEquals("correct-model", file.readText())
            assertEquals(file, installer.installedFile(model))
        }
    }

    @Test fun rejectsSameSizeCorruption() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("wrong"))
            val model = model(server, "right")
            val installer = ArtifactInstaller(temporary.newFolder(), allowHttpForTests = true)
            assertEquals("integrity", assertFailsWith<InstallationException> { installer.install(model) { } }.kind)
            assertNull(installer.installedFile(model))
        }
    }

    @Test fun resumesAtVerifiedRangeBoundary() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 4-9/10").setBody("456789"))
            val model = model(server, "0123456789")
            val root = temporary.newFolder()
            partial(root, model).writeText("0123")
            val installer = ArtifactInstaller(root, allowHttpForTests = true)
            assertEquals("0123456789", installer.install(model) { }.readText())
            assertEquals("bytes=4-", server.takeRequest().getHeader("Range"))
        }
    }

    @Test fun fullResponseReplacesRatherThanAppendsPartial() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("0123456789"))
            val model = model(server, "0123456789")
            val root = temporary.newFolder()
            partial(root, model).writeText("0123")
            assertEquals("0123456789", ArtifactInstaller(root, allowHttpForTests = true).install(model) { }.readText())
        }
    }

    @Test fun rejectsWrongRangeWithoutActivatingFile() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 3-9/10").setBody("3456789"))
            val model = model(server, "0123456789")
            val root = temporary.newFolder()
            partial(root, model).writeText("0123")
            val installer = ArtifactInstaller(root, allowHttpForTests = true)
            assertEquals("range", assertFailsWith<InstallationException> { installer.install(model) { } }.kind)
            assertEquals("0123", partial(root, model).readText())
            assertNull(installer.installedFile(model))
        }
    }

    @Test fun cancellationClosesOldWriterBeforeNextInstallation() = runBlocking {
        MockWebServer().use { server ->
            val content = "0123456789abcdef"
            server.enqueue(MockResponse().setBody(content).throttleBody(4, 100, TimeUnit.MILLISECONDS))
            server.enqueue(MockResponse().setBody(content))
            val model = model(server, content)
            val installer = ArtifactInstaller(temporary.newFolder(), allowHttpForTests = true)
            val started = CompletableDeferred<Unit>()
            val job = launch { installer.install(model) { if (it.received > 0) started.complete(Unit) } }
            withTimeout(5000) { started.await(); job.cancelAndJoin() }
            assertNull(installer.installedFile(model))
            val file = withTimeout(5000) { installer.install(model) { } }
            assertEquals(content, file.readText())
            assertEquals(file, installer.installedFile(model))
        }
    }

    @Test fun completePartialIsVerifiedWithoutDownloadingAgain() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val model = model(server, "ready")
            val root = temporary.newFolder()
            partial(root, model).writeText("ready")
            assertEquals("ready", ArtifactInstaller(root, allowHttpForTests = true).install(model) { }.readText())
            assertEquals(0, server.requestCount)
        }
    }
}
