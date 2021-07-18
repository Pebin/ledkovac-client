package com.bartonsolutions.ledkovac

import android.Manifest.permission.CAMERA
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.TextView
import android.widget.Toolbar
import androidx.preference.PreferenceManager
import com.bartonsolutions.ledkovac.Constants.SERVER_PORT
import com.vmadalin.easypermissions.EasyPermissions
import okhttp3.OkHttpClient
import okhttp3.Request
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.util.*


private const val REQUEST_CODE_CAMERA_PERMISSION = 123
private const val TAG = "MainActivity"

class MainActivity : Activity(), CvCameraViewListener2,
    SharedPreferences.OnSharedPreferenceChangeListener {
    private var mRgba: Mat? = null

    private var mDetector: ColorDetector? = null
    private var mOpenCvCameraView: CameraBridgeViewBase? = null
    private var dataCounter: TextView? = null
    private var toolBar: Toolbar? = null
    private var integrationStatus: TextView? = null

    private var dataCount: Int = 0

    private var database: DatabaseHelper? = null

    private var lastDetected = false
    private val client: OkHttpClient = OkHttpClient()

    private var ipAddress: String? = null

    private var sendingThread: SyncThread? = null


    private val mLoaderCallback: BaseLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                SUCCESS -> {
                    Log.i(TAG, "OpenCV loaded successfully")
                    mOpenCvCameraView!!.setCameraPermissionGranted();
                    mOpenCvCameraView!!.enableView()
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }

    init {
        Log.i(TAG, "Instantiated new ${this.javaClass}")
    }


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

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        toolBar = findViewById(R.id.main_toolbar)
        setActionBar(toolBar)

        database = DatabaseHelper(this)
        dataCount = database!!.getFlashesCount()

        mOpenCvCameraView = findViewById<View>(R.id.camera_view) as CameraBridgeViewBase
        mOpenCvCameraView!!.visibility = SurfaceView.VISIBLE
        mOpenCvCameraView!!.setCvCameraViewListener(this)

        dataCounter = findViewById<View>(R.id.number_of_detected_flashes) as TextView
        integrationStatus = findViewById<View>(R.id.integreation_status) as TextView

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

    public override fun onPause() {
        super.onPause()
        mOpenCvCameraView?.disableView()
    }

    public override fun onResume() {
        super.onResume()
        if (OpenCVLoader.initDebug()) {
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        } else {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback)
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        mOpenCvCameraView?.disableView()
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        mRgba = Mat(height, width, CvType.CV_8UC4)
        mDetector = ColorDetector()
    }

    override fun onCameraViewStopped() {
        mRgba!!.release()
    }

    override fun onCameraFrame(inputFrame: CvCameraViewFrame): Mat {
        val mRgba = inputFrame.rgba()
        val detected = mDetector!!.process(mRgba)

        if (detected && !lastDetected) {
            database?.insertFlash(Date())

            dataCount += 1
            runOnUiThread {
                dataCounter?.text = dataCount.toString()
            }
            lastDetected = true
        }

        if (!detected && lastDetected) {
            lastDetected = false
        }

        return mRgba
    }

    private fun testEndpoint() {
        Thread {
            val request = Request.Builder()
                .url("http://$ipAddress:$SERVER_PORT/")
                .get()
                .build()
            try {
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


    private fun setIntegrationStatus(working: Boolean) {
        integrationStatus?.text = "$ipAddress - ${if (working) "✔" else "❌"}"
    }
}