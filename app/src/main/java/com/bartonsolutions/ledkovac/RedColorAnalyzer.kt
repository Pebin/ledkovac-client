package com.bartonsolutions.ledkovac

import android.graphics.Color
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

class RedColorAnalyzer(private val listenerRed: RedColorListener) : ImageAnalysis.Analyzer {
    // Lower and Upper bounds for range checking in HSV color space
    private val mLowerBound: FloatArray = floatArrayOf(320.0F, 0.196F, 0.196F)
    private val mUpperBound: FloatArray = floatArrayOf(360.0F, 1.0F, 1.0F)

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }


    private fun getHSVfromYUV(image: ImageProxy): FloatArray {
        val planes = image.planes

        val height = image.height
        val width = image.width

        // Y
        val yArr = planes[0].buffer
        val yArrByteArray = yArr.toByteArray()
        val yPixelStride = planes[0].pixelStride
        val yRowStride = planes[0].rowStride

        // U
        val uArr = planes[1].buffer
        val uArrByteArray = uArr.toByteArray()
        val uPixelStride = planes[1].pixelStride
        val uRowStride = planes[1].rowStride

        // V
        val vArr = planes[2].buffer
        val vArrByteArray = vArr.toByteArray()
        val vPixelStride = planes[2].pixelStride
        val vRowStride = planes[2].rowStride

        val y = yArrByteArray[(height * yRowStride + width * yPixelStride) / 2].toInt() and 255
        val u =
            (uArrByteArray[(height * uRowStride + width * uPixelStride) / 4].toInt() and 255) - 128
        val v =
            (vArrByteArray[(height * vRowStride + width * vPixelStride) / 4].toInt() and 255) - 128

        val r = y + (1.370705 * v)
        val g = y - (0.698001 * v) - (0.337633 * u)
        val b = y + (1.732446 * u)

        val hsv = FloatArray(3)
        Color.RGBToHSV(r.toInt(), g.toInt(), b.toInt(), hsv)

        return hsv
    }

    private fun inRedRange(hsv: FloatArray): Boolean {
        return (hsv[0] >= mLowerBound[0] && hsv[0] <= mUpperBound[0]) &&
                (hsv[1] >= mLowerBound[1] && hsv[1] <= mUpperBound[1]) &&
                (hsv[2] >= mLowerBound[2] && hsv[2] <= mUpperBound[2])
    }

    override fun analyze(image: ImageProxy) {
        val hsv = getHSVfromYUV(image)
        listenerRed(inRedRange(hsv))
        image.close()
    }
}