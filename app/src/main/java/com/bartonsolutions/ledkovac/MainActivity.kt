package com.bartonsolutions.ledkovac

import android.Manifest.permission.CAMERA
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.bartonsolutions.ledkovac.Constants.SERVER_PORT
import com.vmadalin.easypermissions.EasyPermissions
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias RedColorListener = (isRed: Boolean) -> Unit
typealias ColorListener = (hsv: FloatArray) -> Unit

private const val REQUEST_CODE_CAMERA_PERMISSION = 123
private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private var dataCounter: TextView? = null
    private var integrationStatus: TextView? = null
    private var fpsCount: TextView? = null
    private var hsvText: TextView? = null

    private var dataCount: Int = 0

    private var database: DatabaseHelper? = null

    private var lastDetected = false
    private val client: OkHttpClient = OkHttpClient()

    private var ipAddress: String? = null

    private var sendingThread: SyncThread? = null
    val lastFrames: Queue<Long> = LinkedList()
    private lateinit var cameraExecutor: ExecutorService


    public override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "called onCreate")
        super.onCreate(savedInstanceState)

        if (!EasyPermissions.hasPermissions(this, CAMERA)) {
            EasyPermissions.requestPermissions(
                this,
                getString(R.string.camera_permissions),
                REQUEST_CODE_CAMERA_PERMISSION,
                CAMERA
            )
        }

        startCamera()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        cameraExecutor = Executors.newSingleThreadExecutor()

        database = DatabaseHelper(this)
        dataCount = database!!.getFlashesCount()

        dataCounter = findViewById<View>(R.id.number_of_detected_flashes) as TextView
        integrationStatus = findViewById<View>(R.id.integration_status) as TextView
        fpsCount = findViewById<View>(R.id.fps_count) as TextView
        hsvText = findViewById<View>(R.id.hsv_text) as TextView

        val sharedPreferences: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        ipAddress = sharedPreferences.getString("ip_address", null)
        if (!ipAddress.isNullOrEmpty()) {
            testEndpoint()
        }

        sendingThread = SyncThread(this)
        sendingThread!!.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(camera_view.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, RedColorAnalyzer({ isRed ->

                        if (isRed && !lastDetected) {
                            database?.insertFlash(Date())

                            dataCount += 1
                            dataCounter?.text = dataCount.toString()
                            lastDetected = true
                        }

                        if (!isRed && lastDetected) {
                            lastDetected = false
                        }

                        update_fps()
                    }, { hsv ->
                        hsvText?.text =
                            String.format("%.2f --- %.2f --- %.2f", hsv[0], hsv[1], hsv[2])
                    }))
                }


            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )


            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        return super.onOptionsItemSelected(item)
    }


    fun update_fps() {
        val newTime = System.currentTimeMillis()
        if (lastFrames.size < 5) {
            lastFrames.add(newTime)
            return
        }
        val lastFrameTime = lastFrames.remove()
        val delta = newTime - lastFrameTime
        val fps = String.format("%.1f", 5 / (delta.toDouble() / 1000))
        fpsCount?.text = "FPS: $fps"
    }

    private fun testEndpoint() {
        Thread {
            try {
                val request = Request.Builder()
                    .url("http://$ipAddress:$SERVER_PORT/")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                response.close()
                Log.d(TAG, response.body.toString())
                setIntegrationStatus(true)
            } catch (ex: Exception) {
                Log.e(TAG, ex.toString())
                setIntegrationStatus(false)
            }
        }.start()

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        ipAddress = sharedPreferences!!.getString("ip_address", null)
        testEndpoint()
    }


    fun setIntegrationStatus(working: Boolean) {
        integrationStatus?.text = "$ipAddress - ${if (working) "✔" else "❌"}"
    }
}