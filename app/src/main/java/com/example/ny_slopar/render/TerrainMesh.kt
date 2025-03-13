package com.example.ny_slopar.render

import android.content.Context
import android.opengl.Matrix
import android.util.Log
import com.google.android.filament.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
            val safeMin = minElevation.toFloat()
            val safeMax = maxElevation.toFloat().coerceAtLeast(safeMin + 1.0f) // Prevent zero range

            Log.d("TerrainMesh", "✅ MinHeight: $safeMin, MaxHeight: $safeMax")

            loadMaterial(safeMin, safeMax)
            generateTerrainMesh()
        } else {
            Log.e("TerrainMesh", "❌ Elevation data is empty! Material and mesh not initialized.")
        }
    }

    private fun loadMaterial(minElevation: Float, maxElevation: Float) {
        try {
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
                material?.setParameter("minHeight", minElevation)
                material?.setParameter("maxHeight", maxElevation)
                Log.d("TerrainMesh", "✅ Material parameters set: MinHeight: $minElevation, MaxHeight: $maxElevation")
            }
        } catch (e: Exception) {
            Log.e("TerrainMesh", "❌ Failed to load material: ${e.message}")
            material = null
        }
    }

    private fun generateTerrainMesh() {
        destroyMesh()

        if (elevationData.isEmpty() || elevationData[0].isEmpty()) {
            Log.e("TerrainMesh", "❌ Elevation data is empty!")
            return
        }

        val terrainSizeX = elevationData.size
        val terrainSizeY = elevationData[0].size
        val vertexCount = terrainSizeX * terrainSizeY
        val positions = FloatArray(vertexCount * 3)
        val uvs = FloatArray(vertexCount * 2)

        val minElevation = elevationData.flatten().minOrNull() ?: 0.0
        val maxElevation = elevationData.flatten().maxOrNull() ?: minElevation
        val elevationRange = (maxElevation - minElevation).takeIf { it > 0.0 } ?: 1.0 // Prevent div by zero

        val terrainScale = 10.0f  // Adjust terrain size
        val heightScale = 400.0f   // Exaggerate elevation for visibility

        for (y in elevationData.indices) {
            for (x in elevationData[y].indices) {
                val index = (y * terrainSizeX + x) * 3
                val uvIndex = (y * terrainSizeX + x) * 2

                // Normalize height and apply scaling
                val normalizedHeight = ((elevationData[y][x] - minElevation) / elevationRange).toFloat()
                positions[index] = (x * terrainScale).toFloat()  // ✅ Scale X (east-west)
                positions[index + 1] = (normalizedHeight * heightScale).toFloat()  // ✅ Elevation scaling
                positions[index + 2] = -(y * terrainScale).toFloat()  // ✅ Scale Z (north-south)

                uvs[uvIndex] = x.toFloat() / terrainSizeX
                uvs[uvIndex + 1] = normalizedHeight.toFloat()


            }

        }
        Log.d("TerrainMesh", "✅ Generating mesh with ${elevationData.size}x${elevationData[0].size} points.")
        // ✅ Allocate buffers efficiently
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
            .attribute(VertexBuffer.VertexAttribute.UV0, 1, VertexBuffer.AttributeType.FLOAT2, 0, 0)
            .build(engine)

        vertexBuffer.setBufferAt(engine, 0, positionBuffer)
        vertexBuffer.setBufferAt(engine, 1, uvBuffer)

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
            .boundingBox(Box(floatArrayOf(0f, 0f, 0f), floatArrayOf(1000f, 300f, 1000f))) // Increased size
            .build(engine, renderable)

        val transformManager = engine.transformManager
        val instance = transformManager.getInstance(renderable)

        if (instance != 0) {
            val matrix = FloatArray(16)
            Matrix.setIdentityM(matrix, 0)
            Matrix.translateM(matrix, 0, -terrainSizeX / 2f, -10f, -terrainSizeY / 2f) // Centering fix
            Matrix.scaleM(matrix, 0, 10f, 5.0f, 10f) // Adjust scale

            transformManager.setTransform(instance, matrix)
            Log.d("TerrainMesh", "✅ Transform applied.")
        } else {
            Log.e("TerrainMesh", "❌ Transform instance is invalid!")
        }

        Log.d("TerrainMesh", "✅ Terrain mesh generated successfully.")
    }

    fun getRenderable(): Int = renderable

    fun destroyMesh() {
        if (::vertexBuffer.isInitialized) engine.destroyVertexBuffer(vertexBuffer)
        if (::indexBuffer.isInitialized) engine.destroyIndexBuffer(indexBuffer)
        EntityManager.get().destroy(renderable)
    }
}
