package com.example.ny_slopar

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.ny_slopar.network.NveApiService
import com.example.ny_slopar.render.FilamentRenderer
import com.example.ny_slopar.render.TerrainMesh
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private lateinit var filamentRenderer: FilamentRenderer
    private var terrainMesh: TerrainMesh? = null
    private lateinit var surfaceView: SurfaceView
    private lateinit var fetchTerrainButton: Button
    private lateinit var api: NveApiService
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var cachedElevationData: List<List<Double>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        System.loadLibrary("filament-jni")

        surfaceView = findViewById(R.id.filamentSurfaceView)
        fetchTerrainButton = findViewById(R.id.fetchTerrainButton)

        filamentRenderer = FilamentRenderer(surfaceView, this@MainActivity)

        api = Retrofit.Builder()
            .baseUrl("https://ws.geonorge.no/hoydedata/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NveApiService::class.java)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // ✅ Request location and fetch elevation on button click
        fetchTerrainButton.setOnClickListener {
            requestLocationPermission()
        }
    }

    // ✅ Ask for location permission before fetching GPS data
    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            getCurrentLocation()
        }
    }

    // ✅ Fetch the user's GPS location
    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val latitude = location.latitude
                val longitude = location.longitude

                Log.d("GPS", "📍 User location: $latitude, $longitude")

                // Call fetchElevationData with the correct location
                fetchElevationData(latitude, longitude)
            } else {
                Log.e("GPS", "❌ Failed to get location")
            }
        }
    }

    // ✅ Fetch elevation data using the user's current GPS location
    private fun fetchElevationData(latCenter: Double, lonCenter: Double) {
        if (cachedElevationData != null) {
            Log.d("ElevationData", "✅ Using cached elevation data!")
            onElevationDataFetched(cachedElevationData!!)
            return
        }

        val gridSize = 25  // 25x25 grid
        val stepSize = 0.005

        val halfGrid = gridSize / 2  // Half of the grid in each direction
        val coordinates = mutableListOf<Pair<Double, Double>>()

        for (y in 0 until gridSize) {
            for (x in 0 until gridSize) {
                val lat = latCenter + ((y - halfGrid) * stepSize)
                val lon = lonCenter + ((x - halfGrid) * stepSize * 2) // Adjust for longitude spacing

                coordinates.add(Pair(lat, lon))
            }
        }


        Log.d("Grid Centering", "📍 Grid Centered on ($latCenter, $lonCenter)")

        Log.d("Grid Centering", "📍 Centered on ($latCenter, $lonCenter)")

        val batches = coordinates.chunked(50).map { batch ->
            val coordinateArray = batch.joinToString(",") { "[${it.second},${it.first}]" } // 🛠️ Fix order: (longitude, latitude)
            val apiUrl = "https://ws.geonorge.no/hoydedata/v1/punkt?koordsys=4258&geojson=false&punkter=[$coordinateArray]"

            Log.d("API Request", "📡 Sending request: $apiUrl") // ✅ Debugging
            apiUrl
        }


        CoroutineScope(Dispatchers.IO).launch {
            try {
                val responses = batches.map { url ->
                    val response = api.getElevationBatch(url).execute()

                    if (response.isSuccessful) {
                        val body = response.body()
                        Log.d("API Response", "📡 Full API Response: $body") // ✅ Print full response
                        body
                    } else {
                        Log.e("API Error", "❌ Failed to fetch elevation: ${response.errorBody()?.string()}")
                        null
                    }
                }



                val elevationData = MutableList(gridSize) { MutableList(gridSize) { 0.0 } }

                responses.forEachIndexed { batchIndex, response ->
                    if (response != null) {
                        response.points.forEachIndexed { pointIndex, point ->
                            val index = (batchIndex * 50) + pointIndex
                            val y = index / gridSize
                            val x = index % gridSize

                            if (y < gridSize && x < gridSize) {
                                val terrainType = point.terrain ?: "Unknown"
                                val elevation =
                                    if (terrainType == "Havflate") 0.0 else point.elevation
                                elevationData[y][x] = elevation
                            }
                        }
                    }
                }

                cachedElevationData = elevationData

                withContext(Dispatchers.Main) {
                    val interpolatedElevationData = upscaleElevationData(elevationData, 200)
                    onElevationDataFetched(interpolatedElevationData)
                }
            } catch (e: Exception) {
                Log.e("API", "❌ Error fetching elevation: ${e.message}")
            }
        }
    }

    // ✅ Scale up elevation data
    fun upscaleElevationData(apiGrid: List<List<Double>>, newSize: Int): List<List<Double>> {
        val oldSize = apiGrid.size
        val scale = (oldSize - 1).toDouble() / (newSize - 1).toDouble()
        val newGrid = MutableList(newSize) { MutableList(newSize) { 0.0 } }

        for (y in 0 until newSize) {
            for (x in 0 until newSize) {
                val srcX = x * scale
                val srcY = y * scale

                val x1 = srcX.toInt()
                val x2 = (x1 + 1).coerceAtMost(oldSize - 1)
                val y1 = srcY.toInt()
                val y2 = (y1 + 1).coerceAtMost(oldSize - 1)

                val q11 = apiGrid[y1][x1]
                val q21 = apiGrid[y1][x2]
                val q12 = apiGrid[y2][x1]
                val q22 = apiGrid[y2][x2]

                val xFrac = srcX - x1
                val yFrac = srcY - y1

                val interpolated = (q11 * (1 - xFrac) * (1 - yFrac)) +
                        (q21 * xFrac * (1 - yFrac)) +
                        (q12 * (1 - xFrac) * yFrac) +
                        (q22 * xFrac * yFrac)

                newGrid[y][x] = interpolated
            }
        }

        return newGrid
    }

    private fun onElevationDataFetched(elevationData: List<List<Double>>) {
        Log.d("ElevationData", "✅ Received Elevation Data: ${elevationData.flatten()}") // ✅ Debug log

        terrainMesh?.destroyMesh()
        terrainMesh = TerrainMesh(filamentRenderer.engine, elevationData, this@MainActivity)

        terrainMesh?.getRenderable()?.let { renderable ->
            if (renderable != 0) {
                filamentRenderer.addRenderable(renderable)
                filamentRenderer.render()
            } else {
                Log.e("MainActivity", "❌ Renderable entity is invalid!")
            }
        }
    }


    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
}
