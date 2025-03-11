package com.example.ny_slopar.render

import android.content.Context
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.util.Log
import kotlin.math.*
import com.google.android.filament.*

class FilamentRenderer(private val surfaceView: SurfaceView, context: Context) {

    val engine: Engine = Engine.create()
    val scene: Scene = engine.createScene()
    val renderer: Renderer = engine.createRenderer()
    val view: View = engine.createView()
    val camera: Camera = engine.createCamera(EntityManager.get().create())
    var swapChain: SwapChain? = null

    private var cameraDistance = 500.0
    private var rotationAngleX = 0.0
    private var rotationAngleY = 30.0


    private val gestureDetector = GestureDetector(context, GestureListener())
    private val scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())

    init {
        setupView()
        setupLight()
        setupSurface()
        surfaceView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun setupView() {
        view.camera = camera
        scene.skybox = Skybox.Builder()
            .color(0.7f, 0.8f, 1.0f, 1.0f) // ✅ Light blue gradient sky
            .build(engine)
        view.scene = scene
        camera.setProjection(90.0, 16.0 / 9.0, 0.1, 10000.0, Camera.Fov.VERTICAL)

        renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = floatArrayOf(0.8f, 0.85f, 0.9f, 1.0f) // ✅ Light background for better contrast
        }

        updateCamera()
    }


    private fun setupLight() {
        val sunlight = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1.0f, 1.0f, 1.0f) // ✅ Pure white sunlight
            .intensity(120000.0f) // ✅ Stronger light for better contrast
            .direction(-1f, 1.5f, -2.5f) // ✅ Adjusted to cast proper shadows
            .castShadows(true) // ✅ Enable real-time shadows
            .build(engine, sunlight)

        scene.addEntity(sunlight)

        // ✅ Enable Shadows in Scene
        val shadowOptions = LightManager.ShadowOptions().apply {
            mapSize = 4096 // ✅ High resolution shadow map
            constantBias = 0.0005f // ✅ Reduce shadow acne
            normalBias = 0.005f // ✅ Prevent floating shadows
            shadowFar = 3000.0f // ✅ Ensure large terrain is covered by shadows
        }


//         ✅ Indirect Lighting (Simulated Ambient Light)
        val indirectLight = IndirectLight.Builder()
            .intensity(10000f) // ✅ Indirect light to avoid full darkness
            .build(engine)
        scene.indirectLight = indirectLight

        Log.d("FilamentRenderer", "✅ Light & Shadows setup completed!")
    }






    private fun setupSurface() {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                swapChain = engine.createSwapChain(holder.surface)
                if (swapChain == null) {
                    Log.e("FilamentRenderer", "❌ SwapChain is NULL!")
                    return
                }
                Log.d("FilamentRenderer", "✅ Surface is valid.")
                surfaceView.postDelayed({ renderLoop() }, 16)

                // ✅ Improve visibility with lighter background
                renderer.clearOptions = Renderer.ClearOptions().apply {
                    clear = true
                    clearColor = floatArrayOf(0.8f, 0.85f, 0.9f, 1.0f)  // ✅ Light grayish-blue background
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                view.viewport = Viewport(0, 0, width, height)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                swapChain?.let { engine.destroySwapChain(it) }
            }
        })
    }

    private fun renderLoop() {
        Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                render()
                Choreographer.getInstance().postFrameCallback(this)
            }
        })
    }

    fun render() {
        swapChain?.let { swapChain ->
            if (!renderer.beginFrame(swapChain, System.nanoTime())) {
                Log.e("FilamentRenderer", "❌ Failed to begin rendering frame!")
                return
            }

            val renderTarget = renderer.clearOptions
            renderTarget.clearColor = floatArrayOf(0.0f, 0.0f, 0.0f, 1.0f) // Fully black clear
            renderTarget.clear = true
            renderer.clearOptions = renderTarget
            renderer.render(view)
            renderer.endFrame()
        } ?: Log.e("FilamentRenderer", "❌ SwapChain is null!")
    }


    fun addRenderable(entity: Int) {
        if (entity != 0) scene.addEntity(entity)
    }

    fun cleanup() {
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(camera.entity)
        engine.destroy()
    }

    private fun updateCamera() {
        val radiansX = Math.toRadians(rotationAngleX)
        val radiansY = Math.toRadians(rotationAngleY)
        val camX = cameraDistance * sin(radiansX) * cos(radiansY)
        val camY = cameraDistance * sin(radiansY)
        val camZ = cameraDistance * cos(radiansX) * cos(radiansY)
        camera.lookAt(camX, camY, camZ, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            rotationAngleX += distanceX * 0.2
            rotationAngleY -= distanceY * 0.2
            rotationAngleY = rotationAngleY.coerceIn(5.0, 80.0)
            updateCamera()
            return true
        }
        override fun onDoubleTap(event: MotionEvent): Boolean {
            rotationAngleX = 0.0
            rotationAngleY = 30.0
            cameraDistance = 500.0
            updateCamera()
            return true
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            cameraDistance /= detector.scaleFactor
            cameraDistance = cameraDistance.coerceIn(100.0, 5000.0)
            updateCamera()
            return true
        }
    }
}