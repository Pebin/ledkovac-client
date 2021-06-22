package com.bartonsolutions.ledkovac

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.*


private const val TAG = "SyncService"

class SyncService : JobService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var ipAddress: String

    private val client: OkHttpClient = OkHttpClient()
    private val JSON = "application/json; charset=utf-8".toMediaType()


    override fun onCreate() {
        super.onCreate()
        databaseHelper = DatabaseHelper(this)


        val sharedPreferences: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        ipAddress = sharedPreferences.getString("ip_address", "192.168.85.47")!!
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        val dataToSync = databaseHelper.getNotSynced()
        try {
            for (record in dataToSync){
                sendInThreadAndUpdate(record)
            }
        } catch (ex: Exception) {
            Log.e(TAG, "onStartJob: crash", ex)
        }
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true
    }

    private fun sendInThreadAndUpdate(record: FlashRecord){
        Thread {
            val sent = sendRecord(record)
            if (sent) {
                databaseHelper.setSynced(record.id)
            }
        }.start()
    }


    private fun sendRecord(record: FlashRecord): Boolean {
        val formattedDate: String = Constants.DATEFORMAT.format(record.date)

        val jsonData = """{
            "id": "${record.id}",
            "date": "$formattedDate"
        }""".trimIndent()

        val body = jsonData.toRequestBody(JSON)
        val request = Request.Builder()
            .url("http://$ipAddress:5050/new-data")  // 10.0.2.2 in emulator
            .post(body)
            .build()
        return try {
            val response = client.newCall(request).execute()
            response.close()
            Log.d(TAG, response.body.toString())
            true
        } catch (ex: Exception) {
            Log.e(TAG, ex.toString())
            false
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        ipAddress = sharedPreferences!!.getString("ip_address", "")!!
    }
}