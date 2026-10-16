package com.riffado.meetings.ai

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.riffado.meetings.MeetingsApp
import java.util.concurrent.TimeUnit

/**
 * Daily worker that checks if any exhausted providers should be recovered.
 * Runs once per day to ensure providers are automatically re-enabled after their billing cycle.
 */
class ProviderRecoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    
    private val TAG = "ProviderRecoveryWorker"
    
    override suspend fun doWork(): Result {
        val app = applicationContext as MeetingsApp
        val coordinator = TranscriptionCoordinator(applicationContext, app.transcriptionProviders)
        
        Log.d(TAG, "Running daily provider recovery check")
        
        // Check and recover any providers past their reset date
        val settings = app.transcriptionProviders.settings.value
        coordinator.checkAndRecoverProviders(settings)
        
        Log.d(TAG, "Provider recovery check complete")
        return Result.success()
    }
    
    companion object {
        private const val WORK_NAME = "provider_recovery"
        
        /**
         * Schedule daily provider recovery checks.
         * Called on app startup.
         */
        fun schedule(context: Context) {
            val work = PeriodicWorkRequestBuilder<ProviderRecoveryWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.DAYS,
            ).build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // Don't restart if already scheduled
                work,
            )
            
            Log.d("ProviderRecoveryWorker", "Scheduled daily provider recovery checks")
        }
    }
}
