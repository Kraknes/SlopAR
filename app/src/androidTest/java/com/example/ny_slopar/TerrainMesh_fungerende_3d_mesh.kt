package com.example.ny_slopar

import android.content.Context
import android.opengl.Matrix
import android.util.Log
import com.google.android.filament.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TerrainMesh_fungerende_3d_mesh(
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
        generateTerrainMesh()
    }

    private fun loadMaterial() {
        try {
            val buffer = context.assets.open("terrain_material.filamat").use { it.readBytes() }
            val byteBuffer = ByteBuffer.allocateDirect(buffer.size)
                .order(ByteOrder.nativeOrder())
                .put(buffer)
                .apply { position(0) } // Convert ByteArray to ByteBuffer

            val loadedMaterial = Material.Builder()
                .payload(byteBuffer, byteBuffer.remaining()) // Use ByteBuffer
                .build(engine)

            if (loadedMaterial == null) {
                Log.e("TerrainMesh", "❌ Failed to create material from filamat!")
            } else {
                material = loadedMaterial.createInstance() // ✅ Use createInstance()
                Log.d("TerrainMesh", "✅ Material loaded successfully!")
            }

        } catch (e: Exception) {
            Log.e("TerrainMesh", "❌ Failed to load material: ${e.message}")
            material = null // Ensure it's null in case of failure
        }
    }

    private fun generateTerrainMesh() {
        destroyMesh()


        val terrainSize = elevationData.size
        val vertexCount = terrainSize * terrainSize
        val positions = FloatArray(vertexCount * 3)
        if (elevationData.isEmpty() || elevationData[0].isEmpty()) {
            Log.e("TerrainMesh", "❌ elevationData is empty or not properly initialized!")
            return
        }
        try {
            // Choose a base elevation – for instance, the elevation of the first point.
            val baseElevation = elevationData[0][0]
            for (y in elevationData.indices) {
                for (x in elevationData[y].indices) {
                    val index = (y * terrainSize + x) * 3
                    positions[index] = x.toFloat()
                    // Subtract baseElevation to normalize the vertical range.
                    positions[index + 1] = (elevationData[y][x] - baseElevation).toFloat()
                    positions[index + 2] = y.toFloat()
                    Log.d("TerrainMesh", "✅ position: (${positions[index]}, ${positions[index+1]}, ${positions[index+2]})")
                }
            }
        } catch (e: Exception) {
            Log.e("TerrainMesh", "❌ Error in position loop: ${e.message}")
        }

        vertexBuffer = VertexBuffer.Builder()
            .vertexCount(vertexCount)
            .bufferCount(3) // ✅ Now including UVs
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 0)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 1, VertexBuffer.AttributeType.FLOAT4, 0, 0)
            .attribute(VertexBuffer.VertexAttribute.UV0, 2, VertexBuffer.AttributeType.FLOAT2, 0, 0) // ✅ UVs added
            .build(engine)

        val uvs = FloatArray(vertexCount * 2) // Two components per vertex
        for (i in 0 until vertexCount) {
            uvs[i * 2] = (i % terrainSize) / terrainSize.toFloat()
            uvs[i * 2 + 1] = (i / terrainSize) / terrainSize.toFloat()
        }
        val uvBuffer = ByteBuffer.allocateDirect(uvs.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(uvs)
            .apply { position(0) }
        vertexBuffer.setBufferAt(engine, 2, uvBuffer)

        val positionBuffer = ByteBuffer.allocateDirect(positions.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(positions)
            .apply { position(0) }

        vertexBuffer.setBufferAt(engine, 0, positionBuffer)

        val indices = mutableListOf<Short>()
        for (y in 0 until terrainSize - 1) {
            for (x in 0 until terrainSize - 1) {
                val topLeft = (y * terrainSize + x).toShort()
                val topRight = (y * terrainSize + x + 1).toShort()
                val bottomLeft = ((y + 1) * terrainSize + x).toShort()
                val bottomRight = ((y + 1) * terrainSize + x + 1).toShort()

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
        if (renderable == 0) {
            Log.e("TerrainMesh", "❌ Failed to create renderable!")
            return
        }


        Log.d("TerrainMesh", "✅ Terrain mesh generated successfully.")
        if (material == null) {
            Log.e("TerrainMesh", "❌ Material is NULL! Aborting mesh generation.")
            return // Prevent crash
        }
        RenderableManager.Builder(1)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer)
            .material(0, material!!)
            .boundingBox(Box(floatArrayOf(0f, 0f, 0f), floatArrayOf(20f, 10f, 20f)))
            .build(engine, renderable)
        if (material != null) {
            val materialInstance = material!!
            materialInstance.setParameter("baseColor", Colors.RgbType.LINEAR, 1.0f, 0.5f, 0.2f) // ✅ Orange color

            val renderableManager = engine.renderableManager
            val renderableInstance = renderableManager.getInstance(renderable)


            if (renderableInstance != 0) {
                renderableManager.setMaterialInstanceAt(renderableInstance, 0, materialInstance)
                renderableManager.setCastShadows(renderableInstance, true)
                renderableManager.setCulling(renderableInstance, false)


                Log.d("TerrainMesh", "✅ Applied basic color material - Orange.")
            } else {
                Log.d("TerrainMesh", "⚠️ Renderable instance is invalid!")
            }
        } else {
            Log.d("TerrainMesh", "❌ Material is NULL!")
        }





        val transformManager = engine.transformManager
        val instance = transformManager.getInstance(renderable)

        if (instance != 0) {
            val matrix = FloatArray(16)
            Matrix.setIdentityM(matrix, 0)
            // Center the terrain by translating by half its size,
            // and scale X and Z by 5 while scaling Y by only 0.1 to reduce elevation variation.
            Matrix.translateM(matrix, 0, -terrainSize / 2f, 0f, -terrainSize / 2f)
            Matrix.scaleM(matrix, 0, 5f, 0.1f, 5f)
            transformManager.setTransform(instance, matrix)
            Log.d("TerrainMesh", "✅ Transform applied: Scale (5, 0.1, 5), Translation (-terrainSize/2, 0, -terrainSize/2)")
        } else {
            Log.d("TerrainMesh", "⚠️ Failed to get terrain transform instance.")
        }



    }

    fun getRenderable(): Int {
        return renderable
    }

    fun destroyMesh() {
        if (::vertexBuffer.isInitialized) engine.destroyVertexBuffer(vertexBuffer)
        if (::indexBuffer.isInitialized) engine.destroyIndexBuffer(indexBuffer)
        EntityManager.get().destroy(renderable)
    }
}
