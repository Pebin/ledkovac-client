package com.bartonsolutions.ledkovac

import android.Manifest.permission.CAMERA
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.widget.TextView
import com.vmadalin.easypermissions.EasyPermissions
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.util.*
import kotlin.collections.ArrayList


private const val REQUEST_CODE_CAMERA_PERMISSION = 123
private const val TAG = "MainActivity"

class MainActivityBackup : Activity(), CvCameraViewListener2 {
    private var mRgba: Mat? = null

    private var mDetector: ColorDetector? = null
    private var mOpenCvCameraView: CameraBridgeViewBase? = null
    private var dataCounter: TextView? = null

    private var data = ArrayList<Date>()

    private var lastDetected = false


    private val mLoaderCallback: BaseLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                SUCCESS -> {
                    Log.i(TAG, "OpenCV loaded successfully")
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

//        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        mOpenCvCameraView = findViewById<View>(R.id.camera_view) as CameraBridgeViewBase
        mOpenCvCameraView!!.visibility = SurfaceView.VISIBLE
        mOpenCvCameraView!!.setCvCameraViewListener(this)

        dataCounter = findViewById<View>(R.id.number_of_detected_flashes) as TextView
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
            data.add(Date())

            dataCounter?.text = data.size.toString()
            lastDetected = true
        }

        if (!detected && lastDetected) {
            lastDetected = false
        }

        return mRgba
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }
}