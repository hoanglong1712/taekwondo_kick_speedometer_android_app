package com.example.tkdspeed

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.sqrt

class ImpactAnalyzer(
    private val onImpactDetected: () -> Unit,
    private val onBoundingBoxUpdate: (android.graphics.RectF?) -> Unit
) : ImageAnalysis.Analyzer {

    var targetColor: String = "Red"
    var isMonitoring: Boolean = false
    
    // Custom color bounds (Color Picker)
    private var customLow: Scalar? = null
    private var customHigh: Scalar? = null
    
    private var lastCentroid: Point? = null
    var motionThreshold = 50.0 // Cần tinh chỉnh thực tế
    
    private var roiRect: Rect? = null // Region of Interest

    
    // Dải màu HSV cho Đỏ (Red có 2 dải trong HSV)
    private val redLow1 = Scalar(0.0, 100.0, 100.0)
    private val redHigh1 = Scalar(10.0, 255.0, 255.0)
    private val redLow2 = Scalar(160.0, 100.0, 100.0)
    private val redHigh2 = Scalar(180.0, 255.0, 255.0)
    
    // Dải màu HSV cho Xanh dương
    private val blueLow = Scalar(100.0, 100.0, 100.0)
    private val blueHigh = Scalar(130.0, 255.0, 255.0)

    override fun analyze(image: ImageProxy) {
        if (!isMonitoring) {
            image.close()
            return
        }

        // Chuyển ImageProxy sang OpenCV Mat
        val mat = imageToMat(image)
        if (mat == null || mat.empty()) {
            image.close()
            return
        }

        // 1. Chuyển sang HSV
        val hsv = Mat()
        Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_RGB2HSV)

        // 2. Lọc màu (Thresholding)
        val mask = Mat()
        if (customLow != null && customHigh != null) {
            Core.inRange(hsv, customLow!!, customHigh!!, mask)
        } else if (targetColor == "Red") {
            val mask1 = Mat()
            val mask2 = Mat()
            Core.inRange(hsv, redLow1, redHigh1, mask1)
            Core.inRange(hsv, redLow2, redHigh2, mask2)
            Core.addWeighted(mask1, 1.0, mask2, 1.0, 0.0, mask)
            mask1.release()
            mask2.release()
        } else {
            Core.inRange(hsv, blueLow, blueHigh, mask)
        }


        // 3. Tìm Contour lớn nhất
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        var maxArea = 0.0
        var largestContour: MatOfPoint? = null

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area > maxArea) {
                maxArea = area
                largestContour = contour
            }
        }

        // 4. Kiểm tra rung lắc (Impact Detection)
        if (largestContour != null && maxArea > 500) { // Bỏ qua vùng quá nhỏ (nhiễu)
            val rect = Imgproc.boundingRect(largestContour)
            val width = mat.cols().toFloat()
            val height = mat.rows().toFloat()
            
            // Tọa độ chuẩn hóa (0.0 -> 1.0)
            val normalizedRect = android.graphics.RectF(
                rect.x.toFloat() / width,
                rect.y.toFloat() / height,
                (rect.x + rect.width).toFloat() / width,
                (rect.y + rect.height).toFloat() / height
            )
            onBoundingBoxUpdate(normalizedRect)

            val moments = Imgproc.moments(largestContour)
            if (moments._m00 > 0) {
                val cx = moments._m10 / moments._m00
                val cy = moments._m01 / moments._m00
                val currentCentroid = Point(cx, cy)

                if (lastCentroid != null) {
                    val distance = calculateDistance(currentCentroid, lastCentroid!!)
                    
                    // Nếu di chuyển đột ngột vượt ngưỡng -> Va chạm!
                    if (distance > motionThreshold) {
                        Log.d("ImpactAnalyzer", "Impact Detected! Distance: $distance")
                        onImpactDetected()
                        isMonitoring = false // Dừng monitor sau khi detect
                    }
                }
                lastCentroid = currentCentroid
            }
        } else {
            onBoundingBoxUpdate(null)
            lastCentroid = null
        }

        // Giải phóng bộ nhớ
        mat.release()
        hsv.release()
        mask.release()
        hierarchy.release()
        image.close()
    }

    private fun calculateDistance(p1: Point, p2: Point): Double {
        return sqrt((p1.x - p2.x).pow(2.0) + (p1.y - p2.y).pow(2.0))
    }

    private fun Double.pow(exp: Double): Double = Math.pow(this, exp)

    fun setCustomColor(h: Double, s: Double, v: Double) {
        // Tạo dải màu quanh điểm được chọn (+/- 10 cho H, +/- 50 cho S, V)
        val hLow = if (h - 10 < 0) 0.0 else h - 10
        val hHigh = if (h + 10 > 180) 180.0 else h + 10
        val sLow = if (s - 50 < 50) 50.0 else s - 50
        val sHigh = 255.0
        val vLow = if (v - 50 < 50) 50.0 else v - 50
        val vHigh = 255.0
        
        customLow = Scalar(hLow, sLow, vLow)
        customHigh = Scalar(hHigh, sHigh, vHigh)
        Log.d("ImpactAnalyzer", "Custom Color Set: H=$h, S=$s, V=$v")
    }

    fun resetCustomColor() {
        customLow = null
        customHigh = null
    }

    private fun imageToMat(image: ImageProxy): Mat? {
        val bitmap = try {
            image.toBitmap()
        } catch (e: Exception) {
            null
        } ?: return null
        
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        // OpenCV bitmapToMat trả về RGBA, ta cần RGB cho Imgproc.COLOR_RGB2HSV
        val rgb = Mat()
        Imgproc.cvtColor(mat, rgb, Imgproc.COLOR_RGBA2RGB)
        mat.release()
        return rgb
    }
}

