package dev.pawelduraj.memstash.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.pawelduraj.memstash.MemStashApp
import java.util.concurrent.TimeUnit

/** Deletes gallery copies whose time is up, then plans the run for the next one. */
class CopyCleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = (applicationContext as MemStashApp).settingsStore.settings.value
        val lifetime = settings.copyCleanup.lifetimeMillis ?: return Result.success()
        Sender(applicationContext).deleteGalleryCopies(olderThanMillis = lifetime)
        // Appended, because replacing would cancel this run.
        schedule(applicationContext, lifetime, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "delete-gallery-copies"

        /** After a new copy: a run planned for an older copy comes sooner and plans this one's after it. */
        suspend fun scheduleAfterCopy(context: Context, lifetimeMillis: Long) =
            schedule(context, lifetimeMillis, ExistingWorkPolicy.KEEP)

        /** After the time was changed: plans anew for the copies in the gallery. */
        suspend fun reschedule(context: Context, lifetimeMillis: Long) =
            schedule(context, lifetimeMillis, ExistingWorkPolicy.REPLACE)

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        private suspend fun schedule(context: Context, lifetimeMillis: Long, policy: ExistingWorkPolicy) {
            val oldest = Sender(context).oldestCopyTime() ?: return
            val delay = (oldest + lifetimeMillis - System.currentTimeMillis()).coerceAtLeast(0) + 5_000
            val request = OneTimeWorkRequestBuilder<CopyCleanupWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
        }
    }
}
