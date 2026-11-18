package com.colourswift.avarionxlite.engine.rtp

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.MediaStore

class RtpWakeJob : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        if (Protection.isEnabled(this)) schedule(this)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        private const val JOB_ID = 7301

        fun schedule(context: Context) {
            val info = JobInfo.Builder(JOB_ID, ComponentName(context, RtpWakeJob::class.java))
                .addTriggerContentUri(
                    JobInfo.TriggerContentUri(
                        MediaStore.Files.getContentUri("external"),
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                    )
                )
                .setTriggerContentUpdateDelay(200)
                .setTriggerContentMaxDelay(1000)
                .build()
            context.getSystemService(JobScheduler::class.java).schedule(info)
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
        }
    }
}
