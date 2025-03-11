package com.example.ny_slopar.render

import android.content.Context
import android.opengl.Matrix
import android.util.Log
import androidx.compose.ui.geometry.times
import com.google.android.filament.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.time.times

class TerrainMesh(
    private val engine: Engine,
    private val elevationData: List<List<Double>>,
    private val context: Context
) {

    private lateinit var vertexBuffer: VertexBuffer
    private lateinit var indexBuffer: IndexBuffer
    private var renderable: Int = 0
    private var material: MaterialInstance? = null

    init {
        if (elevationData.isNotEmpty() && elevationData[0].isNotEmpty()) {
            val minElevation = elevationData.flatten().minOrNull() ?: 0.0
            val maxElevation = elevationData.flatten().maxOrNull() ?: minElevation

            // ✅ Ensure a valid range (avoid minHeight == maxHeight issue)
            val adjustedMinHeight = minElevation.toFloat()
            val adjustedMaxHeight = maxElevation.toFloat().coerceAtLeast(adjustedMinHeight + 1.0f) // ✅ Prevents zero range

            Log.d("TerrainMesh", "✅ MinHeight: $adjustedMinHeight, MaxHeight: $adjustedMaxHeight")

            loadMaterial(adjustedMinHeight, adjustedMaxHeight) // ✅ Pass adjusted values
            generateTerrainMesh()
        } else {
            Log.e("TerrainMesh", "❌ Elevation data is empty! Material and mesh not initialized.")
        }
    }

    private fun loadMaterial(minElevation: Float, maxElevation: Float) {
        try {
            val adjustedMinHeight = minElevation
            val adjustedMaxHeight = maxElevation.coerceAtLeast(adjustedMinHeight + 1.0f)
            val buffer = context.assets.open("terrain_material.filamat").use { it.readBytes() }
            val byteBuffer = ByteBuffer.allocateDirect(buffer.size)
                .order(ByteOrder.nativeOrder())
                .put(buffer)
                .apply { position(0) }

            val loadedMaterial = Material.Builder()
                .payload(byteBuffer, byteBuffer.remaining())
                .build(engine)

            if (loadedMaterial == null) {
                Log.e("TerrainMesh", "❌ Failed to create terrain material!")
            } else {
                material = loadedMaterial.createInstance()
                material?.setParameter("minHeight", adjustedMinHeight)
                material?.setParameter("maxHeight", adjustedMaxHeight)

                Log.d("TerrainMesh", "✅ MinHeight: $minElevation, MaxHeight: $maxElevation")

                // 🔹 Debug: Print ALL height values with normalization
                for (y in elevationData.indices) {
                    for (x in elevationData[y].indices) {
                        val normalizedHeight =
                            ((elevationData[y][x] - minElevation) / (maxElevation - minElevation)).toFloat()
//                        Log.d(
//                            "TerrainMesh",
//                            "🔹 Height: ${elevationData[y][x]}, Normalized: $normalizedHeight"
//                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TerrainMesh", "❌ Failed to load material: ${e.message}")
            material = null
        }
    }

    private fun generateTerrainMesh() {
        destroyMesh() // ✅ Clear old data

        if (elevationData.isEmpty() || elevationData[0].isEmpty()) {
            Log.e("TerrainMesh", "❌ Elevation data is empty!")
            return
        }

        val terrainSizeX = elevationData.size
        val terrainSizeY = elevationData[0].size
        val vertexCount = terrainSizeX * terrainSizeY
        val positions = FloatArray(vertexCount * 3)
        val uvs = FloatArray(vertexCount * 2)

        // ✅ Compute min & max elevation
        val minElevation = elevationData.flatten().minOrNull() ?: 0.0
        val maxElevation = elevationData.flatten().maxOrNull() ?: minElevation
        val adjustedMinHeight = minElevation.toFloat()
        val adjustedMaxHeight = maxElevation.toFloat().coerceAtLeast(adjustedMinHeight + 1.0f) // ✅ Prevents zero range
        val elevationRange = maxElevation - minElevation
        val safeRange = if (elevationRange == 0.0) 1.0 else elevationRange // ✅ Prevent division by zero

        Log.d("TerrainMesh", "✅ MinHeight: $minElevation, MaxHeight: $maxElevation")
        Log.d("TerrainMesh", "✅ Adjusted MinHeight: $adjustedMinHeight, MaxHeight: $adjustedMaxHeight")

        val scaleFactor = when {
            elevationRange < 10.0 -> 100.0 / elevationRange // ✅ Adjust dynamically
            else -> 1.5 / elevationRange
        }

        for (y in elevationData.indices) {
            for (x in elevationData[y].indices) {
                val index = (y * terrainSizeX + x) * 3
                val uvIndex = (y * terrainSizeX + x) * 2
                val terrainScale = 10.0f  // ✅ Increase terrain scale (Change as needed)
                val heightScale = 150.0f   // ✅ Exaggerate elevation to make it visible

                // ✅ Normalize height before scaling
                val normalizedHeight = (elevationData[y][x] - minElevation) / (maxElevation - minElevation)
                positions[index] = (x * terrainScale).toFloat()  // ✅ Scale X (east-west)
                positions[index + 1] = ((normalizedHeight * heightScale).toFloat()) // ✅ Scale Y (height)
                positions[index + 2] = -(y * terrainScale).toFloat()  // ✅ Scale Z (north-south)


                uvs[uvIndex] = x.toFloat() / terrainSizeX
                uvs[uvIndex + 1] = normalizedHeight.toFloat()
                Log.d("TerrainMesh", "🔹 UV Height at ($x, $y): $normalizedHeight")
            }
        }

        // ✅ Pass min/max elevation to material
        loadMaterial(adjustedMinHeight, adjustedMaxHeight)

        // ✅ Fast buffer allocation
        val positionBuffer = ByteBuffer.allocateDirect(positions.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(positions)
            .apply { position(0) }

        val uvBuffer = ByteBuffer.allocateDirect(uvs.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(uvs)
            .apply { position(0) }

        vertexBuffer = VertexBuffer.Builder()
            .vertexCount(vertexCount)
            .bufferCount(2)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 0)
            .attribute(VertexBuffer.VertexAttribute.UV0, 1, VertexBuffer.AttributeType.FLOAT2, 0, 0) // ✅ Add UV mapping
            .build(engine)

        vertexBuffer.setBufferAt(engine, 0, positionBuffer)
        vertexBuffer.setBufferAt(engine, 1, uvBuffer) // ✅ Pass UV data

        // ✅ Generate indices
        val indices = mutableListOf<Short>()
        for (y in 0 until terrainSizeX - 1) {
            for (x in 0 until terrainSizeY - 1) {
                val topLeft = (y * terrainSizeX + x).toShort()
                val topRight = (y * terrainSizeX + x + 1).toShort()
                val bottomLeft = ((y + 1) * terrainSizeX + x).toShort()
                val bottomRight = ((y + 1) * terrainSizeX + x + 1).toShort()

                indices.add(topLeft)
                indices.add(bottomLeft)
                indices.add(topRight)
                indices.add(topRight)
                indices.add(bottomLeft)
                indices.add(bottomRight)
            }
        }

        indexBuffer = IndexBuffer.Builder()
            .indexCount(indices.size)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)

        val indexBufferData = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(indices.toShortArray())
            .apply { position(0) }
        indexBuffer.setBuffer(engine, indexBufferData)

        renderable = EntityManager.get().create()
        RenderableManager.Builder(1)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer)
            .material(0, material!!)
            .boundingBox(Box(floatArrayOf(0f, 0f, 0f), floatArrayOf(100f, 50f, 100f)))
            .build(engine, renderable)

        Log.d("TerrainMesh", "✅ Generated terrain mesh successfully.")
    }

    fun getRenderable(): Int = renderable

    fun destroyMesh() {
        if (::vertexBuffer.isInitialized) engine.destroyVertexBuffer(vertexBuffer)
        if (::indexBuffer.isInitialized) engine.destroyIndexBuffer(indexBuffer)
        EntityManager.get().destroy(renderable)
    }
}
