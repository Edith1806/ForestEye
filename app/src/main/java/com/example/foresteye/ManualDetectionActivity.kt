package com.example.foresteye

import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

class ManualDetectionActivity : AppCompatActivity() {

    private lateinit var tflite: Interpreter
    private lateinit var imageView: ImageView
    private lateinit var uploadButton: Button
    private lateinit var chartView: BarChart

    private val inputSize = 640
    private val labels = listOf(
        "Buffalo", "Camel", "Cat", "Cheetah", "Cow", "Deer", "Dog", "Elephant", "Goat",
        "Gorilla", "Hippo", "Horse", "Lion", "Monkeys", "Panda", "Rat","Rhino", "Tiger",
        "Wolf", "Zebra"
    )

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            Log.d("PhotoPicker", "Selected URI: $uri")
            val bitmap = uriToBitmap(uri)
            if (bitmap != null) {
                imageView.post {
                    runObjectDetection(bitmap)
                }
            }
        } else {
            Log.d("PhotoPicker", "No media selected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_detection)
        imageView = findViewById(R.id.imageView)
        uploadButton = findViewById(R.id.btnUpload)
        chartView = findViewById(R.id.chartView)

        try {
            tflite = Interpreter(loadModelFile("best_float32.tflite"))
            Toast.makeText(this, "Model loaded successfully!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Model loading failed: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("TFLite", "Error loading model", e)
            return
        }

        uploadButton.setOnClickListener {
            pickMedia.launch(
                androidx.activity.result.PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    .build()
            )
        }

        val inputBitmap = BitmapFactory.decodeResource(resources, R.drawable.sample_forest)
        imageView.post {
            runObjectDetection(inputBitmap)
        }
    }

    @Throws(IOException::class)
    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(modelName)
        FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }
    }

    private fun runObjectDetection(bitmap: Bitmap) {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val input = bitmapToByteBuffer(resizedBitmap)
        val output = Array(1) { Array(24) { FloatArray(8400) } }

        try {
            tflite.run(input, output)
            val results = processOutput(output, bitmap.width, bitmap.height)
            val outputBitmap = drawDetections(bitmap, results)
            imageView.setImageBitmap(outputBitmap)
            updateChart(results)
        } catch (e: Exception) {
            Log.e("TFLite", "Detection failed", e)
            Toast.makeText(this, "Detection failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateChart(results: List<DetectionResult>) {
        val entries = mutableListOf<BarEntry>()
        val classCounts = mutableMapOf<String, Int>()

        for (result in results) {
            val label = result.label
            classCounts[label] = classCounts.getOrDefault(label, 0) + 1
        }

        val sortedLabels = classCounts.keys.sorted()
        var index = 0
        val labelsForChart = mutableListOf<String>()

        for (label in sortedLabels) {
            entries.add(BarEntry(index.toFloat(), classCounts[label]!!.toFloat()))
            labelsForChart.add(label)
            index++
        }

        if (entries.isEmpty()) {
            chartView.clear()
            return
        }

        val dataSet = BarDataSet(entries, "Detected Objects")
        dataSet.color = Color.parseColor("#009688")
        val data = BarData(dataSet)
        data.barWidth = 0.5f

        chartView.data = data
        chartView.description.isEnabled = false
        chartView.xAxis.valueFormatter = IndexAxisValueFormatter(labelsForChart)
        chartView.xAxis.setDrawGridLines(false)
        chartView.xAxis.labelCount = labelsForChart.size
        chartView.animateY(1000)
        chartView.invalidate()
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val pixelValue = intValues[i * inputSize + j]
                // Corrected: The order should be putFloat, not putFloatArray
                byteBuffer.putFloat(((pixelValue shr 16 and 0xFF) / 255.0f))
                byteBuffer.putFloat(((pixelValue shr 8 and 0xFF) / 255.0f))
                byteBuffer.putFloat(((pixelValue and 0xFF) / 255.0f))
            }
        }
        byteBuffer.rewind()
        return byteBuffer
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: IOException) {
            Log.e("UriToBitmap", "Error decoding bitmap from URI", e)
            null
        }
    }

    private fun processOutput(output: Array<Array<FloatArray>>, originalWidth: Int, originalHeight: Int): List<DetectionResult> {
        val confidenceThreshold = 0.25f
        val iouThreshold = 0.45f

        val reshapedOutput = Array(8400) { FloatArray(24) }
        for (i in 0 until 8400) {
            for (j in 0 until 24) {
                reshapedOutput[i][j] = output[0][j][i]
            }
        }

        val detections = mutableListOf<DetectionResult>()
        for (i in reshapedOutput.indices) {
            val row = reshapedOutput[i]
            var maxScore = 0f
            var maxClassIndex = -1
            for (j in 4 until 24) {
                if (row[j] > maxScore) {
                    maxScore = row[j]
                    maxClassIndex = j - 4
                }
            }

            if (maxScore > confidenceThreshold) {
                // Corrected: Accessing specific indices from the row array
                val xCenter = row[0]
                val yCenter = row[1]
                val width = row[2]
                val height = row[3]

                val x1 = (xCenter - width / 2) * originalWidth / inputSize
                val y1 = (yCenter - height / 2) * originalHeight / inputSize
                val x2 = (xCenter + width / 2) * originalWidth / inputSize
                val y2 = (yCenter + height / 2) * originalHeight / inputSize

                val box = RectF(x1, y1, x2, y2)
                val label = if (maxClassIndex != -1 && maxClassIndex < labels.size) labels[maxClassIndex] else "Unknown"

                detections.add(DetectionResult(label, maxScore, box))
            }
        }
        return applyNMS(detections, iouThreshold)
    }

    private fun applyNMS(detections: List<DetectionResult>, iouThreshold: Float): List<DetectionResult> {
        val sortedDetections = detections.sortedByDescending { it.confidence }.toMutableList()
        val finalDetections = mutableListOf<DetectionResult>()

        while (sortedDetections.isNotEmpty()) {
            val first = sortedDetections.first()
            finalDetections.add(first)
            sortedDetections.removeAt(0)

            val iterator = sortedDetections.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                if (calculateIoU(first.boundingBox, next.boundingBox) >= iouThreshold) {
                    iterator.remove()
                }
            }
        }
        return finalDetections
    }

    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val intersection = RectF(
            max(box1.left, box2.left),
            max(box1.top, box2.top),
            min(box1.right, box2.right),
            min(box1.bottom, box2.bottom)
        )

        val intersectionArea = max(0f, intersection.width()) * max(0f, intersection.height())
        val unionArea = (box1.width() * box1.height()) + (box2.width() * box2.height()) - intersectionArea

        return if (unionArea == 0f) 0f else intersectionArea / unionArea
    }

    private fun drawDetections(bitmap: Bitmap, detections: List<DetectionResult>): Bitmap {
        val displayWidth = imageView.width
        val displayHeight = imageView.height

        if (displayWidth <= 0 || displayHeight <= 0) {
            return bitmap
        }

        val scaleFactor = min(displayWidth.toFloat() / bitmap.width, displayHeight.toFloat() / bitmap.height)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scaleFactor).toInt(), (bitmap.height * scaleFactor).toInt(), true)

        val outputBitmap = scaledBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)

        val paint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 3f / scaleFactor
        }
        val textPaint = Paint().apply {
            color = Color.YELLOW
            textSize = 32f / scaleFactor
            style = Paint.Style.FILL
        }

        for (result in detections) {
            val box = result.boundingBox
            val scaledBox = RectF(
                box.left * scaleFactor,
                box.top * scaleFactor,
                box.right * scaleFactor,
                box.bottom * scaleFactor
            )
            canvas.drawRect(scaledBox, paint)
            canvas.drawText("${result.label} ${(result.confidence * 100).toInt()}%", scaledBox.left, scaledBox.top - (10f / scaleFactor), textPaint)
        }

        return outputBitmap
    }

    data class DetectionResult(
        val label: String,
        val confidence: Float,
        val boundingBox: RectF
    )

    override fun onDestroy() {
        super.onDestroy()
        tflite.close()
    }
}
