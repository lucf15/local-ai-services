package io.github.lucf15.localai.runner

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.lucf15.localai.core.InstallationProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class ModelDownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = coroutineScope {
        val modelId = inputData.getString("model_id") ?: return@coroutineScope Result.failure()
        val graph = (applicationContext as RunnerApplication).graph
        val progress = MutableStateFlow<InstallationProgress?>(null)
        val updates = launch {
            progress.filterNotNull().collect {
                setProgress(workDataOf("phase" to it.phase, "received" to it.received, "total" to it.total))
            }
        }
        try {
            val inputs = graph.inputs.await()
            val model = inputs.catalog.models.first { it.id == modelId }
            setForeground(foreground(model.displayName))
            graph.installer.install(model) { progress.value = it }
            Result.success(workDataOf("phase" to "installed"))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            Result.failure(workDataOf("error" to (error.message ?: error.javaClass.simpleName)))
        } finally { updates.cancelAndJoin() }
    }

    private fun foreground(model: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("models", "Model downloads", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, "models")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Installing $model")
            .setContentText("Downloading and verifying the local model")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Pause", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(id.hashCode(), notification)
    }
}
