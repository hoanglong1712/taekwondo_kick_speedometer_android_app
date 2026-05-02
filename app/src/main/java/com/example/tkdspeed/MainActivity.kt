package com.example.tkdspeed

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import android.widget.SeekBar
import android.view.MotionEvent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import java.util.Locale
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.tkdspeed.databinding.ActivityMainBinding
import org.opencv.android.OpenCVLoader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var impactAnalyzer: ImpactAnalyzer? = null
    
    private var isRunning = false
    private var isOpenCVLoaded = false
    private var startTime: Long = 0
    private var targetColor: String = "Red"
    
    // Sound variables
    private lateinit var soundPool: SoundPool
    private var soundStart: Int = 0
    private var soundHit: Int = 0
    
    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                val elapsedNano = System.nanoTime() - startTime
                val elapsedSeconds = elapsedNano / 1_000_000_000.0
                viewBinding.tvTimer.text = String.format(Locale.US, "%.6f", elapsedSeconds)
                handler.postDelayed(this, 10)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // 1. Khởi tạo SoundPool
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()
        
        // Load âm thanh (Cần tạo thư mục res/raw và thêm file vào đó)
        soundStart = soundPool.load(this, R.raw.start_beep, 1)
        soundHit = soundPool.load(this, R.raw.hit_sound, 1)
        // TODO: Thêm file start_beep.mp3 và hit_sound.mp3 vào thư mục res/raw để sử dụng âm thanh

        // 2. Khởi tạo OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "Internal OpenCV library not found. Trying manual load.")
            try {
                // Nạp thư viện C++ STL trước để tránh lỗi "cannot locate symbol"
                try { System.loadLibrary("c++_shared") } catch (e: Throwable) { }
                
                System.loadLibrary("opencv_java4")
                isOpenCVLoaded = true
                Log.d("OpenCV", "OpenCV loaded manually with STL")
            } catch (e: Throwable) {
                val errorMsg = e.message ?: e.toString()
                Log.e("OpenCV", "OpenCV load failed: $errorMsg", e)
                isOpenCVLoaded = false
                Toast.makeText(this, "OpenCV Load Failed: $errorMsg", Toast.LENGTH_LONG).show()
            }
        } else {
            Log.d("OpenCV", "OpenCV loaded successfully via initDebug")
            isOpenCVLoaded = true
        }

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        viewBinding.btnRed.setOnClickListener {
            targetColor = "Red"
            impactAnalyzer?.targetColor = "Red"
            impactAnalyzer?.resetCustomColor()
            viewBinding.tvStatus.text = "Target: Red"
        }

        viewBinding.btnBlue.setOnClickListener {
            targetColor = "Blue"
            impactAnalyzer?.targetColor = "Blue"
            impactAnalyzer?.resetCustomColor()
            viewBinding.tvStatus.text = "Target: Blue"
        }

        viewBinding.viewFinder.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                calibrateColor(event.x, event.y)
            }
            true
        }


        viewBinding.btnStart.setOnClickListener {
            toggleTimer()
        }

        viewBinding.seekBarThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                viewBinding.tvThreshold.text = "Sensitivity (Threshold): $progress"
                impactAnalyzer?.motionThreshold = progress.toDouble()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun toggleTimer() {
        if (!isOpenCVLoaded) {
            Toast.makeText(this, "OpenCV is not loaded!", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isRunning) {
            // Start
            isRunning = true
            startTime = System.nanoTime()
            viewBinding.btnStart.text = "STOP"
            viewBinding.tvStatus.text = "Monitoring Impact..."
            
            // Phát âm thanh bắt đầu
            soundPool.play(soundStart, 1f, 1f, 0, 0, 1f)
            
            // Kích hoạt analyzer
            impactAnalyzer?.isMonitoring = true
            
            handler.post(timerRunnable)
        } else {
            stopTimer()
        }
    }

    private fun stopTimer() {
        isRunning = false
        impactAnalyzer?.isMonitoring = false
        viewBinding.btnStart.text = "START"
        viewBinding.tvStatus.text = "Finished"
        handler.removeCallbacks(timerRunnable)
    }

    private fun calibrateColor(x: Float, y: Float) {
        val bitmap = viewBinding.viewFinder.bitmap ?: return
        
        // Map View coordinates to Bitmap coordinates
        val bitmapX = (x / viewBinding.viewFinder.width * bitmap.width).toInt()
        val bitmapY = (y / viewBinding.viewFinder.height * bitmap.height).toInt()
        
        if (bitmapX < 0 || bitmapX >= bitmap.width || bitmapY < 0 || bitmapY >= bitmap.height) return
        
        val pixel = bitmap.getPixel(bitmapX, bitmapY)
        val r = AndroidColor.red(pixel)
        val g = AndroidColor.green(pixel)
        val b = AndroidColor.blue(pixel)
        
        val hsv = FloatArray(3)
        AndroidColor.RGBToHSV(r, g, b, hsv)
        
        // OpenCV HSV: H: 0-180, S: 0-255, V: 0-255
        // Android HSV: H: 0-360, S: 0-1, V: 0-1
        impactAnalyzer?.setCustomColor(hsv[0] / 2.0, hsv[1] * 255.0, hsv[2] * 255.0)
        viewBinding.tvStatus.text = "Calibrated to Color at ($bitmapX, $bitmapY)"
        Toast.makeText(this, "Color Calibrated!", Toast.LENGTH_SHORT).show()
    }


    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // 1. Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            // 2. Image Analysis (OpenCV)
            impactAnalyzer = ImpactAnalyzer(
                onImpactDetected = {
                    // Callback khi phát hiện va chạm
                    runOnUiThread {
                        if (isRunning) {
                            // Phát âm thanh khi đá trúng
                            soundPool.play(soundHit, 1f, 1f, 0, 0, 1f)
                            
                            stopTimer()
                            viewBinding.tvStatus.text = "IMPACT DETECTED!"
                            Toast.makeText(this, "Hit!", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onBoundingBoxUpdate = { rect ->
                    runOnUiThread {
                        viewBinding.overlay.boundingBox = rect
                    }
                }
            )
            impactAnalyzer?.targetColor = targetColor
            impactAnalyzer?.motionThreshold = viewBinding.seekBarThreshold.progress.toDouble()

            val rotation = viewBinding.viewFinder.display?.rotation ?: android.view.Surface.ROTATION_0
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(rotation)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, impactAnalyzer!!)
                }


            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch(exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
