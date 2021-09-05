package com.bartonsolutions.ledkovac

import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.bartonsolutions.ledkovac.Constants.SERVER_PORT
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody


private const val TAG = "SyncService"

class SyncThread(private var activity: MainActivity) : Thread(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private var databaseHelper: DatabaseHelper = DatabaseHelper(activity)
    private var ipAddress: String

    private val client: OkHttpClient = OkHttpClient()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    init {
        val sharedPreferences: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(activity)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        ipAddress = sharedPreferences.getString("ip_address", "192.168.85.47")!!
    }

    override fun run() {
        while (true) {
            sendNotSyncedData()
            sleep(5000)
        }
    }

    private fun sendNotSyncedData() {
        val dataToSync = databaseHelper.getNotSynced()
        try {
            var synced = true
            for (record in dataToSync) {
                val sent = sendRecord(record)
                if (sent) {
                    databaseHelper.setSynced(record.id)
                } else {
                    synced = false
                    break
                }
            }
            activity.setIntegrationStatus(synced)
        } catch (ex: Exception) {
            Log.e(TAG, "onStartJob: crash", ex)
        }
    }


    private fun sendRecord(record: FlashRecord): Boolean {
        val formattedDate: String = Constants.DATEFORMAT.format(record.date)

        val jsonData = """{
            "id": "${record.id}",
            "date": "$formattedDate"
        }""".trimIndent()

        val body = jsonData.toRequestBody(JSON)
        val request = Request.Builder()
            .url("http://$ipAddress:$SERVER_PORT/new-data")  // 10.0.2.2 in emulator
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