package com.example.dishapp

import android.app.AlertDialog
import android.content.*
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.JavaCameraView
import org.opencv.android.Utils
import org.opencv.core.Mat
import ai.onnxruntime.*
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.serenegiant.widget.UVCCameraTextureView

class MainActivity : AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2 {
    private lateinit var resultText: TextView
    private lateinit var templateManager: TemplateManager
    private lateinit var recognizer: Recognizer

    // 内置摄像头
    private lateinit var internalCameraView: JavaCameraView

    // USB 摄像头
    private lateinit var usbManager: UsbManager
    private lateinit var usbMonitor: USBMonitor
    private var uvcCamera: UVCCamera? = null
    private lateinit var usbCameraView: UVCCameraTextureView

    private var usingUsbCamera = false

    private var similarityThreshold: Float = 0.5f
    private val PREFS_NAME = "DishAppPrefs"
    private val KEY_THRESHOLD = "similarity_threshold"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        resultText = findViewById(R.id.resultText)
        internalCameraView = findViewById(R.id.cameraView)
        usbCameraView = findViewById(R.id.usbCameraView)

        // ONNXRuntime 初始化
        val ortEnv = OrtEnvironment.getEnvironment()
        val modelBytes = assets.open("mobilenet_feature_extractor.onnx").readBytes()
        val session = ortEnv.createSession(modelBytes)
        templateManager = TemplateManager(this)
        recognizer = Recognizer(ortEnv, session)

        // 恢复阈值
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        similarityThreshold = prefs.getFloat(KEY_THRESHOLD, 0.5f)

        val thresholdSeek = findViewById<SeekBar>(R.id.thresholdSeek)
        val thresholdText = findViewById<TextView>(R.id.thresholdText)
        thresholdSeek.max = 100
        thresholdSeek.progress = (similarityThreshold * 100).toInt()
        thresholdText.text = "识别阈值: ${"%.2f".format(similarityThreshold)}"
        thresholdSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                similarityThreshold = progress / 100f
                thresholdText.text = "识别阈值: ${"%.2f".format(similarityThreshold)}"
                prefs.edit().putFloat(KEY_THRESHOLD, similarityThreshold).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 保存模板
        val saveBtn = findViewById<Button>(R.id.saveBtn)
        saveBtn.setOnClickListener {
            val editText = EditText(this)
            AlertDialog.Builder(this)
                .setTitle("输入菜品名称")
                .setView(editText)
                .setPositiveButton("确定") { _, _ ->
                    val dishName = editText.text.toString().trim()
                    if (dishName.isNotEmpty()) {
                        val bmp = captureFrame()
                        bmp?.let {
                            val emb = recognizer.getEmbedding(it)
                            templateManager.addTemplate(dishName, it, emb)
                            resultText.text = "✅ 模板已保存: $dishName"
                        }
                    } else {
                        resultText.text = "❌ 菜品名称不能为空"
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // 查看模板
        val viewBtn = findViewById<Button>(R.id.viewBtn)
        viewBtn.setOnClickListener {
            val templates = templateManager.getAllTemplates()
            if (templates.isEmpty()) {
                AlertDialog.Builder(this).setTitle("模板库")
                    .setMessage("当前没有模板")
                    .setPositiveButton("确定", null).show()
            } else {
                val items = templates.map { (dish, vecs) -> "$dish (${vecs.size} 张)" }.toTypedArray()
                AlertDialog.Builder(this).setTitle("模板库")
                    .setItems(items) { _, which ->
                        val dishName = templates.keys.elementAt(which)
                        AlertDialog.Builder(this)
                            .setTitle("删除确认")
                            .setMessage("确定要删除 [$dishName] 的所有模板吗？")
                            .setPositiveButton("删除") { _, _ ->
                                templateManager.deleteDish(dishName)
                                resultText.text = "❌ 已删除模板: $dishName"
                            }
                            .setNegativeButton("取消", null).show()
                    }
                    .setPositiveButton("关闭", null).show()
            }
        }

        // 初始化 USB
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        if (checkUsbCamera()) {
            startUsbCamera()
            usingUsbCamera = true
            resultText.text = "📷 使用USB摄像头"
        } else {
            startInternalCamera()
            usingUsbCamera = false
            resultText.text = "📷 使用内置摄像头"
        }

        // USB 插拔监听
        val filter = IntentFilter()
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        registerReceiver(usbReceiver, filter)
    }

    private fun captureFrame(): Bitmap? {
        return if (usingUsbCamera) {
            usbCameraView.bitmap
        } else {
            null // OpenCV JavaCameraView 需额外封装拍照
        }
    }

    private fun checkUsbCamera(): Boolean {
        for (device in usbManager.deviceList.values) {
            if (device.deviceClass == android.hardware.usb.UsbConstants.USB_CLASS_VIDEO) {
                return true
            }
        }
        return false
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    if (!usingUsbCamera && checkUsbCamera()) {
                        stopInternalCamera()
                        startUsbCamera()
                        usingUsbCamera = true
                        resultText.text = "📷 自动切换到USB摄像头"
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    if (usingUsbCamera) {
                        stopUsbCamera()
                        startInternalCamera()
                        usingUsbCamera = false
                        resultText.text = "📷 自动切换到内置摄像头"
                    }
                }
            }
        }
    }

    private fun startInternalCamera() {
        internalCameraView.visibility = JavaCameraView.VISIBLE
        internalCameraView.setCvCameraViewListener(this)
        internalCameraView.enableView()
    }

    private fun stopInternalCamera() {
        internalCameraView.disableView()
        internalCameraView.visibility = JavaCameraView.GONE
    }

    private fun startUsbCamera() {
        usbMonitor = USBMonitor(this, object : USBMonitor.OnDeviceConnectListener {
            override fun onAttach(device: UsbDevice?) {}
            override fun onDettach(device: UsbDevice?) {}
            override fun onConnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?, createNew: Boolean) {
                uvcCamera = UVCCamera().apply {
                    open(ctrlBlock)
                    setPreviewSize(640, 480, UVCCamera.FRAME_FORMAT_MJPEG)
                    setPreviewDisplay(usbCameraView.surface)
                    startPreview()
                }
            }
            override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                stopUsbCamera()
            }
            override fun onCancel(device: UsbDevice?) {}
        })
        usbMonitor.register()
        usbCameraView.visibility = UVCCameraTextureView.VISIBLE
    }

    private fun stopUsbCamera() {
        uvcCamera?.destroy()
        uvcCamera = null
        usbMonitor.unregister()
        usbCameraView.visibility = UVCCameraTextureView.GONE
    }

    // 内置摄像头帧回调
    override fun onCameraViewStarted(width: Int, height: Int) {}
    override fun onCameraViewStopped() {}
    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        val mat = inputFrame.rgba()
        val bmp = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bmp)
        val results = recognizer.recognizeTopK(bmp, templateManager, similarityThreshold, 3)
        runOnUiThread {
            resultText.text = if (results.isNotEmpty()) {
                val builder = StringBuilder("✅ Top-3 识别结果:\n")
                results.forEachIndexed { i, (dish, sim) ->
                    builder.append("${i + 1}. $dish | 相似度=${"%.3f".format(sim)}\n")
                }
                builder.toString()
            } else "未识别 (低于阈值 ${"%.2f".format(similarityThreshold)})"
        }
        return mat
    }
}
