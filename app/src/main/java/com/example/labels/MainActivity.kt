package com.example.labels

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.widget.Toast
import androidx.camera.view.PreviewView
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.TextView
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.labels.database.AppDatabase
import com.example.labels.database.daos.ProductDao
import com.example.labels.database.populateDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Callback
import retrofit2.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService
    private val CAMERA_PERMISSION_CODE = 100

    private lateinit var database: AppDatabase
    private lateinit var productDao: ProductDao

    private lateinit var resultsTextView: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        resultsTextView = findViewById(R.id.resultsTextView)

        applicationContext.deleteDatabase("product_database")
        Log.d("Database", "Existing database deleted.")

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "product_database"
        )
            .fallbackToDestructiveMigration() // Ensure database resets if schema changes
            .build()

        productDao = database.productDao()

        // Populate the database after building it
        CoroutineScope(Dispatchers.IO).launch {
            if (productDao.getAllProducts().isEmpty()) { // Check if already populated
                Log.d("Database", "Populating database for the first time...")
                populateDatabase(productDao)
                logProductList(productDao)
            } else {
                Log.d("Database", "Database already populated.")
                logProductList(productDao)
            }
        }

        if (isCameraPermissionGranted()) {
            startCamera() // Start camera if permission is granted
        } else {
            requestCameraPermission()
        }

        // Initialize Camera Executor
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_CODE
        )
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

    private var lastSnapshot: File? = null

    private fun captureSnapshot() {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this), // Avoid blocking the main thread
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    CoroutineScope(Dispatchers.IO).launch {
                        processImageProxy(image) // Process in the background
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
        fun processImage(@Body image: RequestBody): Call<ResponseBody>
    }

    // Retrofit client setup
    object RetrofitClient {
        private const val BASE_URL = "https://ftt-api.onrender.com/" // Replace with your server's URL

        private val client = OkHttpClient.Builder().build()

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
    private fun sendToApi(imageData: ByteArray) {
        val requestBody = RequestBody.create("application/octet-stream".toMediaTypeOrNull(), imageData)

        val apiService = RetrofitClient.instance
        val call = apiService.processImage(requestBody)

        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    val jsonResponse = response.body()?.string()
                    val labels = parseResponse(jsonResponse ?: "[]")

                    // Filter labels before updating the UI
                    CoroutineScope(Dispatchers.IO).launch {
                        val filteredLabels = filterLabelsByProducts(labels)
                        runOnUiThread {
                            if (filteredLabels.isNotEmpty()) {
                                displayLabels(filteredLabels)
                            } else {
                                resultsTextView.text = "No matching labels found."
                            }
                        }
                    }
                } else {
                    Log.e("API Error", "Error Response: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.e("API Failure", "Failed to call API: ${t.message}")
            }
        })
    }


    // Filter labels by checking if their text contains a product name
    private suspend fun filterLabelsByProducts(labels: List<LabelResponse>): List<LabelResponse> {
        val products = productDao.getAllProducts()
        return labels.filter { label ->
            products.any { product ->
                label.text.contains(product.name, ignoreCase = true)
            }
        }.also {
            Log.d("Database", "Filtered labels count: ${it.size}")
        }
    }


    private fun parseResponse(json: String): List<LabelResponse> {
        val gson = Gson()
        val type = object : TypeToken<List<LabelResponse>>() {}.type
        return gson.fromJson(json, type)
    }

    private fun displayLabels(labels: List<LabelResponse>) {
        val formattedText = labels.joinToString("\n\n") { label ->
            "${label.label_id}:\n${label.text}"
        }
        resultsTextView.text = formattedText
    }

    private fun logProductList(productDao: ProductDao) {
        CoroutineScope(Dispatchers.IO).launch {
            val productList = productDao.getAllProducts()
            if (productList.isEmpty()) {
                Log.d("Database", "No products found in the database.")
            } else {
                Log.d("Database", "Products in the database:")
                productList.forEach { product ->
                    Log.d("Database", "Product ID: ${product.id}, Name: ${product.name}")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

}
