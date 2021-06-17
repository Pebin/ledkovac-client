package com.bartonsolutions.ledkovac

import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class ColorDetector() {
    // Lower and Upper bounds for range checking in HSV color space
    private val mLowerBound: Scalar = Scalar(160.0, 50.0, 50.0)
    private val mUpperBound: Scalar = Scalar(180.0, 255.0, 255.0)

    fun process(imageSource: Mat): Boolean {
        val image = imageSource.clone()
        Imgproc.cvtColor(image, image, Imgproc.COLOR_RGB2HSV)
        Imgproc.medianBlur(image, image, 5)
        Core.inRange(image, mLowerBound, mUpperBound, image)

        val hierarchy = Mat.zeros(Size(5.0, 5.0), CvType.CV_8UC1)
        val contours = ArrayList<MatOfPoint>();

        Imgproc.findContours(
            image,
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )

        var maxArea = 0.0
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area > maxArea) maxArea = area
        }

//        Imgproc.drawContours(imageSource, contours, -1, Scalar(255.0, 0.0, 0.0, 255.0))

        return maxArea > 10000;
    }
}