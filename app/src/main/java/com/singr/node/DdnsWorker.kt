package com.singr.node

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Periodically pushes the box's global IPv6 to its AAAA record. IPv6 prefixes
 * rotate slowly, so the WorkManager 15-minute floor is plenty. No-op when
 * ddns.json is absent.
 */
class DdnsWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val file = Config.ddnsConfig(applicationContext)
        if (!file.exists()) return Result.success()

        val cfg = DdnsConfig.parse(file.readText()) ?: run {
            Log.w(Config.TAG, "ddns.json present but unparseable")
            return Result.success()
        }
        val ipv6 = Ddns.globalIpv6() ?: run {
            Log.w(Config.TAG, "no global IPv6 yet; retrying")
            return Result.retry()
        }
        return if (Ddns.update(cfg, ipv6)) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK = "singr-ddns"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<DdnsWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.KEEP, req,
            )
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(WORK)
        }
    }
}
