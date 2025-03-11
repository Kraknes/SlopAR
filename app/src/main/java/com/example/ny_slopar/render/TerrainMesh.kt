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
        loadMaterial()
        generateTerrainMesh() // ✅ Call mesh generation synchronously
    }

    private fun loadMaterial() {
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
                Log.e("TerrainMesh", "❌ Failed to create material from filamat!")
            } else {
                material = loadedMaterial.createInstance()
                material?.setParameter("baseColor", Colors.RgbType.SRGB, 1.0f, 0.6f, 0.3f) // ✅ Apply color!
                Log.d("TerrainMesh", "✅ Material loaded successfully!")
            }

        } catch (e: Exception) {
            Log.e("TerrainMesh", "❌ Failed to load material: ${e.message}")
            material = null
        }
    }

    private fun generateTerrainMesh() {
        destroyMesh() // ✅ Clear old data before generating new mesh

        if (elevationData.isEmpty() || elevationData[0].isEmpty()) {
            Log.e("TerrainMesh", "❌ Elevation data is empty!")
            return
        }

        val terrainSizeX = elevationData.size
        val terrainSizeY = elevationData[0].size
        val vertexCount = terrainSizeX * terrainSizeY
        val positions = FloatArray(vertexCount * 3)

        val minElevation = elevationData.flatten().minOrNull() ?: 0.0
        val maxElevation = elevationData.flatten().maxOrNull() ?: minElevation
        val elevationRange = maxElevation - minElevation
        val scaleFactor =
            if (elevationRange == 0.0) 1.0 else 10.0 / elevationRange // ✅ Scale for visibility

        // ✅ Faster loop instead of coroutines
        for (y in elevationData.indices) {
            for (x in elevationData[y].indices) {
                val index = (y * terrainSizeX + x) * 3
                positions[index] = x.toFloat()
                positions[index + 1] =
                    ((elevationData[y][x] - minElevation) * scaleFactor).toFloat()
                positions[index + 2] = y.toFloat()
            }
        }


        // ✅ Fast buffer allocation
        val positionBuffer = ByteBuffer.allocateDirect(positions.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(positions)
            .apply { position(0) }

        vertexBuffer = VertexBuffer.Builder()
            .vertexCount(vertexCount)
            .bufferCount(1)
            .attribute(
                VertexBuffer.VertexAttribute.POSITION,
                0,
                VertexBuffer.AttributeType.FLOAT3,
                0,
                0
            )
            .build(engine)

        vertexBuffer.setBufferAt(engine, 0, positionBuffer)

        // ✅ Generate indices faster
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

        Log.d("TerrainMesh", "✅ Generated 100x100 terrain mesh successfully.")
    }

    // ✅ Move these functions outside of `generateTerrainMesh`
    fun getRenderable(): Int {
        return renderable
    }

    fun destroyMesh() {
        if (::vertexBuffer.isInitialized) engine.destroyVertexBuffer(vertexBuffer)
        if (::indexBuffer.isInitialized) engine.destroyIndexBuffer(indexBuffer)
        EntityManager.get().destroy(renderable)
    }
}
