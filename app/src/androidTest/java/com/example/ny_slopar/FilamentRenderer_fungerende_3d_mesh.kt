package com.example.ny_slopar

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

class FilamentRenderer_fungerende_3d_mesh(private val surfaceView: SurfaceView, context: Context) {

    val engine: Engine = Engine.create()
    val scene: Scene = engine.createScene()
    val renderer: Renderer = engine.createRenderer()
    val view: View = engine.createView()
    val camera: Camera = engine.createCamera(EntityManager.get().create())
    var swapChain: SwapChain? = null

    private var cameraDistance = 250.0
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
        scene.skybox = null
        view.scene = scene
        camera.setProjection(50.0, 16.0 / 9.0, 0.1, 10000.0, Camera.Fov.VERTICAL)
        updateCamera()
    }

    private fun setupLight() {
        val lightManager = engine.lightManager
        val sunlight = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1.0F, 1.0F, 1.0F)
            .intensity(70000.0f)
            .direction(-0.0f, 1.0f, -15f)
            .castShadows(true)
            .build(engine, sunlight)
        scene.addEntity(sunlight)
        Log.d("FilamentRenderer", "✅ Directional light added.")
    }

    private fun setupSurface() {
        Log.d("FilamentRenderer", "Setupsurface initiated")
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Log.d("FilamentRenderer", "surfaceCreated initiated")
                swapChain = engine.createSwapChain(holder.surface)
                swapChain?.let {
                    Log.d("FilamentRenderer", "✅ SwapChain initialized successfully!")
                } ?: Log.e("FilamentRenderer", "❌ SwapChain is NULL!")

                if (!holder.surface.isValid) {
                    Log.e("FilamentRenderer", "❌ Surface is not valid. Cannot create SwapChain.")
                    return
                }

                Log.d("FilamentRenderer", "✅ Surface is valid.")
                surfaceView.postDelayed({ renderLoop() }, 16)
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                if (width == 0 || height == 0) {
                    Log.e("FilamentRenderer", "❌ Viewport size is invalid: $width x $height")
                    return
                }
                view.viewport = Viewport(0, 0, width, height)
                Log.d("FilamentRenderer", "✅ Viewport updated: ${view.viewport.width} x ${view.viewport.height}")
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                swapChain?.let { engine.destroySwapChain(it) }
                Log.d("FilamentRenderer", "Surface destroyed")
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
        if (entity == 0) {
            Log.e("FilamentRenderer", "❌ Entity is INVALID! It won't be added to the scene.")
            return
        }
        scene.addEntity(entity)
        Log.d("FilamentRenderer", "✅ Added entity: $entity")
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
        camera.lookAt(camX, camY, camZ, 0.0, 00.0, 0.0, 0.0, 1.0, 0.0)
        Log.d("FilamentRenderer", "📷 Camera Updated -> X:$camX, Y:$camY, Z:$camZ")
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
            cameraDistance = 250.0
            updateCamera()
            return true
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            cameraDistance /= detector.scaleFactor
            cameraDistance = cameraDistance.coerceIn(50.0, 1000.0)
            updateCamera()
            return true
        }
    }
}
