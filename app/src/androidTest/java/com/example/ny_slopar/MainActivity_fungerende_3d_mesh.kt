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
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity_fungerende_3d_mesh : AppCompatActivity() {

    private lateinit var filamentRenderer: FilamentRenderer
    private var terrainMesh: TerrainMesh? = null
    private lateinit var surfaceView: SurfaceView
    private lateinit var fetchTerrainButton: Button
    private lateinit var api: NveApiService // ✅ Reuse Retrofit instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        System.loadLibrary("filament-jni")

        surfaceView = findViewById(R.id.filamentSurfaceView)
        fetchTerrainButton = findViewById(R.id.fetchTerrainButton)

        // ✅ Initialize Filament Renderer before use
        filamentRenderer = FilamentRenderer( surfaceView, this@MainActivity_fungerende_3d_mesh,)

        // ✅ Initialize Retrofit once
        api = Retrofit.Builder()
            .baseUrl("https://ws.geonorge.no/hoydedata/v1/") // ✅ Ensure correct API
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NveApiService::class.java)

        // ✅ Fetch elevation data when button is clicked
        fetchTerrainButton.setOnClickListener {
            fetchElevationData()
        }
    }

    private fun fetchElevationData() {
        val lat = 69.0 // Example latitude
        val lon = 18.0 // Example longitude

        api.getElevation(latitude = lat, longitude = lon).enqueue(object : Callback<ElevationResponse> {
            override fun onResponse(call: Call<ElevationResponse>, response: Response<ElevationResponse>) {
                if (response.isSuccessful) {
                    response.body()?.points?.firstOrNull()?.let { point ->
                        val elevationData = generateElevationGrid(point.elevation) // ✅ Generate 3D grid
                        Log.d("ElevationData", "Fetched elevation: $elevationData") // ✅ Debug log

                        // ✅ Check if terrainMesh already exists and remove it
                        terrainMesh?.destroyMesh()

                        // ✅ Create new terrain mesh and add it to Filament
                        terrainMesh = TerrainMesh(filamentRenderer.engine, elevationData, this@MainActivity_fungerende_3d_mesh)

                        val renderable = terrainMesh?.getRenderable()
                        if (renderable != null && renderable != 0) {
                            filamentRenderer.addRenderable(renderable) // ✅ Ensure terrain is added
                            filamentRenderer.render() // ✅ Start rendering
                        } else {
                            Log.e("MainActivity", "❌ Renderable entity is invalid!")
                        }
                    }
                } else {
                    Log.e("API", "Error fetching elevation: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<ElevationResponse>, t: Throwable) {
                Log.e("API", "Failed to fetch elevation: ${t.message}")
            }
        })
    }

// Denne legger til randomhet i flatheten, det er derfor den endrer seg hele tiden. Få den til å hente inn elevation data
    private fun generateElevationGrid(baseElevation: Double): List<List<Double>> {
        val gridSize = 50
        return List(gridSize) { y ->
            List(gridSize) { x ->
                baseElevation + Math.random() * 5  // Simulated variation in terrain
            }
        }
    }
}
