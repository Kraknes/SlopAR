package com.example.ny_slopar

import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.ny_slopar.network.ElevationResponse
import com.example.ny_slopar.network.NveApiService
import com.example.ny_slopar.render.FilamentRenderer
import com.example.ny_slopar.render.TerrainMesh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder


class MainActivity : AppCompatActivity() {

    private lateinit var filamentRenderer: FilamentRenderer
    private var terrainMesh: TerrainMesh? = null
    private lateinit var surfaceView: SurfaceView
    private lateinit var fetchTerrainButton: Button
    private lateinit var api: NveApiService

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

        fetchTerrainButton.setOnClickListener {
            fetchElevationData()
        }
    }

    private var cachedElevationData: List<List<Double>>? = null


    private fun fetchElevationData() {
        if (cachedElevationData != null) {
            Log.d("ElevationData", "✅ Using cached elevation data!")
            onElevationDataFetched(cachedElevationData!!)
            return
        }

        val latStart = 69.667545
        val lonStart = 18.87583
        val gridSize = 25  // Fetch a 50x50 grid
        val stepSize = 0.005

        val coordinates = mutableListOf<Pair<Double, Double>>()

        for (y in 0 until gridSize) {
            for (x in 0 until gridSize) {
                val lat = latStart + (y * stepSize)
                val lon = lonStart + (x * stepSize * 2)
                coordinates.add(Pair(lon, lat)) // ✅ Ensure (Easting, Northing) order
            }
        }

        val batches = coordinates.chunked(50).map { batch ->
            val coordinateString =
                batch.joinToString(separator = ",") { "[${it.first},${it.second}]" }
            val encodedCoordinates = URLEncoder.encode("[$coordinateString]", "UTF-8")
            "https://ws.geonorge.no/hoydedata/v1/punkt?koordsys=4258&geojson=false&punkter=$encodedCoordinates"
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val responses = batches.map { url ->
                    val response = api.getElevationBatch(url).execute()
                    response.body()
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
                    val interpolatedElevationData = upscaleElevationData(elevationData, 200) // 🔹 Scale up
                    onElevationDataFetched(interpolatedElevationData) // 🔹 Use new upscaled data
                }
            } catch (e: Exception) {
                Log.e("API", "❌ Error fetching elevation: ${e.message}")
            }
        }
    }
    // Interpoering for å gjøre meshen større
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

        Log.d("ElevationData", "✅ Interpolated ${apiGrid.size}x${apiGrid.size} grid to ${newSize}x${newSize}")
        return newGrid
    }


    fun onElevationDataFetched(elevationData: List<List<Double>>) {
        Log.d("ElevationData", "✅ Processing elevation data...")

        // ✅ Destroy previous terrain before reloading
        terrainMesh?.destroyMesh()

        // ✅ Apply new terrain mesh
        terrainMesh = TerrainMesh(filamentRenderer.engine, elevationData, this@MainActivity)

        val renderable = terrainMesh?.getRenderable()
        if (renderable != null && renderable != 0) {
            filamentRenderer.addRenderable(renderable)
            filamentRenderer.render()
            Log.d("MainActivity", "✅ Terrain added and rendering started!")
        } else {
            Log.e("MainActivity", "❌ Renderable entity is invalid! Terrain might not be visible.")
        }
    }

}
