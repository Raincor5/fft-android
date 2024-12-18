package com.example.labels

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.widget.Toast
import androidx.camera.view.PreviewView
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.labels.utils.LabelAdapter
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Callback
import retrofit2.Response
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MainActivity : AppCompatActivity() {

    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService
    private val CAMERA_PERMISSION_CODE = 100

    private lateinit var loadingLayout: View
    private lateinit var loadingText: TextView
    private lateinit var viewFinder: PreviewView
    private lateinit var recyclerView: RecyclerView

    private val labelMap = LinkedHashMap<String, LabelResponse>() // Storage for unique labels
    private lateinit var adapter: LabelAdapter

    private val savedLabels = mutableSetOf<String>() // Store unique keys for saved labels

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        loadingLayout = findViewById(R.id.loadingLayout)
        loadingText = findViewById(R.id.loadingText)
        viewFinder = findViewById(R.id.viewFinder)
        recyclerView = findViewById(R.id.labelRecyclerView)


        // Setup RecyclerView and Adapter
        adapter = LabelAdapter(labelMap)
        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerView.adapter = adapter
        // Show loading screen with random duration
        simulateLoadingScreen()
        // Initialize Camera Executor
        cameraExecutor = Executors.newSingleThreadExecutor()
    }


    // TODO: Implement loading of components check here
    private fun simulateLoadingScreen() {
        // Keep loading screen visible
        loadingLayout.visibility = View.VISIBLE
        viewFinder.visibility = View.GONE
        recyclerView.visibility = View.GONE

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Step 1: Initialize Camera
                updateLoadingText("Adjusting the lens... Please hold still!")
                val cameraDeferred = async { initializeCamera() }
                delay(500)

                // Step 2: Awaken API
                updateLoadingText("Summoning the server from slumber...")
                val apiDeferred = async { awakenApi() }

                updateLoadingText("Fetching saved labels...")
                fetchSavedLabels() // Fetch saved labels from the API
                delay(500)

                // Step 3: Setup Swipe Gestures
                updateLoadingText("Setting up label interactions...")
                setupSwipeGestures()
                delay(500)

                // Wait for camera and API initialization
                awaitAll(cameraDeferred, apiDeferred)

                // Step 4: Finalize loading
                updateLoadingText("Almost ready... Cooking up the magic!")
                delay(1000) // Small suspenseful delay

                // Show the app's main content
                loadingLayout.visibility = View.GONE
                viewFinder.visibility = View.VISIBLE
                recyclerView.visibility = View.VISIBLE

                Toast.makeText(this@MainActivity, "App is Ready!", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Log.e("LoadingScreen", "Error during initialization", e)
                updateLoadingText("Oops! Something went wrong. Please restart.")
                Toast.makeText(this@MainActivity, "Error during initialization", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun fetchSavedLabels() {
        withContext(Dispatchers.IO) {
            try {
                val response = RetrofitClient.instance.fetchSavedLabels().execute()
                if (response.isSuccessful && response.body() != null) {
                    val labels = response.body()!!
                    for (label in labels) {
                        val uniqueKey = generateUniqueKey(label)
                        savedLabels.add(uniqueKey)
                    }
                    Log.d("SavedLabels", "Fetched ${savedLabels.size} saved labels")
                } else {
                    Log.e("SavedLabels", "Failed to fetch saved labels: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("SavedLabels", "Error fetching saved labels", e)
            }
        }
    }

    private fun generateUniqueKey(label: LabelResponse): String {
        val productName = label.parsed_data.product_name
        val preppedDate = label.parsed_data.dates.getOrNull(0) ?: "N/A"
        val useByDate = label.parsed_data.dates.getOrNull(1) ?: "N/A"
        return "$productName|$preppedDate|$useByDate"
    }

    private fun saveLabelToDatabase(label: LabelResponse) {
        // TODO: Implement saving to Render database
        Log.d("SaveLabel", "Saving label: $label")
    }

    private fun updateLoadingText(text: String) {
        loadingText.text = text
        Log.d("LoadingScreen", text) // Optional: Log the loading text for debugging
    }

    private suspend fun initializeCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        return suspendCancellableCoroutine { continuation ->
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(viewFinder.surfaceProvider)
                    }

                imageCapture = ImageCapture.Builder().build()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                    continuation.resume(Unit)

                    // Schedule snapshots after the camera is ready
                    scheduleSnapshots()

                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(this))
        }
    }


    private suspend fun awakenApi() {
        withContext(Dispatchers.IO) {
            try {
                val requestBody = RequestBody.create("application/octet-stream".toMediaTypeOrNull(), ByteArray(0))
                val response = RetrofitClient.instance.processImage(requestBody).execute()
                Log.d("API", "API Awakened: ${response.code()}")
            } catch (e: Exception) {
                Log.e("API", "Failed to awaken API", e)
                throw e
            }
        }
    }

    private fun setupSwipeGestures() {
        // Configure swipe gestures for RecyclerView
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.UP or ItemTouchHelper.DOWN) {

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false // Drag-and-drop not needed
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val itemView = viewHolder.itemView

                if (direction == ItemTouchHelper.UP) {
                    // Fade-out and shrink animation for swipe-up
                    itemView.animate()
                        .alpha(0f)
                        .scaleX(0.5f)
                        .scaleY(0.5f)
                        .setDuration(300L)
                        .withEndAction {
                            recyclerView.post {
                                val key = labelMap.keys.toList()[position]
                                labelMap.remove(key)
                                adapter.notifyItemRemoved(position)
                                Toast.makeText(this@MainActivity, "Label discarded!", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .start()
                } else if (direction == ItemTouchHelper.DOWN) {
                    // Swipe-down logic remains unchanged (save)
                    recyclerView.post {
                        val key = labelMap.keys.toList()[position]
                        val label = labelMap[key]
                        if (label != null) {
                            saveLabelToDatabase(label)
                            labelMap.remove(key)
                            adapter.notifyItemRemoved(position)
                            Toast.makeText(this@MainActivity, "Label saved!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            override fun onChildDraw(
                canvas: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val itemView = viewHolder.itemView

                    // Retrieve the original background color
                    val originalColor = itemView.getTag(R.id.original_background_color) as Int

                    // Calculate swipe progress (0 = start, 1 = full swipe)
                    val progress = Math.abs(dY) / recyclerView.height.toFloat()

                    // Subtle color blending: Soft Blue for Save, Light Gray for Discard
                    val blendedColor = if (dY > 0) {
                        blendColors(originalColor, Color.parseColor("#D6EAF8"), progress) // Swipe Down: Soft Blue
                    } else {
                        blendColors(originalColor, Color.parseColor("#E5E5E5"), progress) // Swipe Up: Light Gray
                    }
                    itemView.setBackgroundColor(blendedColor)

                    // Apply scaling and fading
                    val scale = 1 - progress * 0.1f
                    itemView.scaleX = scale
                    itemView.scaleY = scale
                    itemView.alpha = 1 - progress

                    // Translate the item
                    itemView.translationY = dY
                } else {
                    super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                }
            }

            private fun blendColors(colorFrom: Int, colorTo: Int, ratio: Float): Int {
                val inverseRatio = 1 - ratio
                val r = (Color.red(colorFrom) * inverseRatio + Color.red(colorTo) * ratio).toInt()
                val g = (Color.green(colorFrom) * inverseRatio + Color.green(colorTo) * ratio).toInt()
                val b = (Color.blue(colorFrom) * inverseRatio + Color.blue(colorTo) * ratio).toInt()
                return Color.rgb(r, g, b)
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val itemView = viewHolder.itemView

                // Restore the original background color
                val originalColor = itemView.getTag(R.id.original_background_color) as? Int ?: Color.BLUE
                itemView.setBackgroundColor(originalColor)

                // Reset transformations
                itemView.alpha = 1f
                itemView.scaleX = 1f
                itemView.scaleY = 1f
                itemView.translationY = 0f
            }
        }

        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required to use this app", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // CameraProvider instance
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Set up Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(findViewById<PreviewView>(R.id.viewFinder).surfaceProvider)
                }

            // Set up ImageCapture
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind previous use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to lifecycle
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

                // Schedule snapshots
                scheduleSnapshots()

            } catch (exc: Exception) {
                exc.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun scheduleSnapshots() {
        Log.d("CameraApp", "Scheduling snapshots every 5 seconds")
        val handler = Handler(Looper.getMainLooper())
        val interval = 5000L // Every 5 seconds

        val takeSnapshot = object : Runnable {
            override fun run() {
                captureSnapshot()
                handler.postDelayed(this, interval) // Repeat every X seconds
            }
        }

        handler.post(takeSnapshot)
    }


    private fun captureSnapshot() {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    // Launch a coroutine to process the image
                    CoroutineScope(Dispatchers.IO).launch {
                        processImageProxy(image)
                    }
                    super.onCaptureSuccess(image)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraApp", "Snapshot failed: ${exception.message}", exception)
                }
            }
        )
    }


    private suspend fun processImageProxy(image: ImageProxy) {
        try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            // Send bytes to API or save as required
            sendToApi(bytes)

        } finally {
            image.close() // Always close the ImageProxy to free resources
        }
    }

    // Define the Retrofit interface
    interface ApiService {
        @POST("process-image")
        fun processImage(@Body image: RequestBody): Call<List<LabelResponse>>

        @POST("fetch-saved-labels")
        fun fetchSavedLabels(): Call<List<LabelResponse>>

    }


    // Retrofit client setup
    object RetrofitClient {
        private const val BASE_URL = "https://ftt-api.onrender.com/" // Replace with your server's URL

        private val client = OkHttpClient.Builder()
            .connectTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS) // No connection timeout
            .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)    // No read timeout
            .writeTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)   // No write timeout
            .build()

        val instance: ApiService by lazy {
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }

    // TODO: Implement your API call here
    // Update sendToApi Function
    private fun sendToApi(bytes: ByteArray) {
        val requestBody = RequestBody.create("application/octet-stream".toMediaTypeOrNull(), bytes)

        RetrofitClient.instance.processImage(requestBody).enqueue(object : Callback<List<LabelResponse>> {
            override fun onResponse(call: Call<List<LabelResponse>>, response: Response<List<LabelResponse>>) {
                if (response.isSuccessful && response.body() != null) {
                    val newLabels = response.body()!!
                    Log.d("API", "Received Labels: $newLabels")
                    runOnUiThread {
                        appendNewLabels(newLabels)
                    }
                } else {
                    Log.e("API", "Error: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<List<LabelResponse>>, t: Throwable) {
                Log.e("API", "API call failed: ${t.message}")
            }
        })
    }


    // Call this method when new label data is received from the API
    private fun appendNewLabels(newLabels: List<LabelResponse>) {
        var labelAdded = false

        for (label in newLabels) {
            val uniqueKey = generateUniqueKey(label)

            // Skip if the label is already saved or displayed
            if (savedLabels.contains(uniqueKey) || labelMap.containsKey(uniqueKey)) {
                Log.d("appendNewLabels", "Skipping duplicate label: $uniqueKey")
                continue
            }

            // Add to displayed labels
            labelMap[uniqueKey] = label
            labelAdded = true
        }

        if (labelAdded) {
            adapter.notifyDataSetChanged() // Refresh the RecyclerView
            recyclerView.scrollToPosition(labelMap.size - 1) // Scroll to the last added label
        }
    }





    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
