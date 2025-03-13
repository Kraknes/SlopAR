package com.example.ny_slopar.render

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.util.Log
import kotlin.math.*
import com.google.android.filament.*
import com.google.android.filament.utils.Float3

class FilamentRenderer(private val surfaceView: SurfaceView, context: Context) {

    val engine: Engine = Engine.create()
    val scene: Scene = engine.createScene()
    val renderer: Renderer = engine.createRenderer()
    val view: View = engine.createView()
    val camera: Camera = engine.createCamera(EntityManager.get().create())
    var swapChain: SwapChain? = null

    private val cameraController = CameraController(camera)

    private val gestureDetector = GestureDetector(context, GestureListener())
    private val scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
    private var cameraDistance = 1500.0 // 🔹 Increased to ensure visibility
    private var rotationAngleX = 0.0
    private var rotationAngleY = 60.0 // 🔹 Adjusted to view from above initially

    init {
        setupView()
        setupLight()
        setupSurface()
        surfaceView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            cameraController.handleTouch(event)
            true
        }
    }

    private fun setupView() {
        view.camera = camera
        scene.skybox = null
        view.scene = scene
        camera.setProjection(45.0, 16.0 / 9.0, 0.1, 1000000.0, Camera.Fov.VERTICAL)

        renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = floatArrayOf(0.0f, 0.0f, 0.0f, 1.0f)
        }

        cameraController.resetCamera()
    }

    private fun setupLight() {
        val sunlight = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1.0f, 1.0f, 1.0f)
            .intensity(120000.0f)
            .direction(-1f, 1.5f, -2.5f)
            .castShadows(true)
            .build(engine, sunlight)

        scene.addEntity(sunlight)

        val indirectLight = IndirectLight.Builder()
            .intensity(10000f)
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

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            cameraController.rotateCamera(-distanceX, distanceY)
            return true
        }

        override fun onDoubleTap(event: MotionEvent): Boolean {
            cameraController.resetCamera()
            return true
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            cameraController.zoomCamera(detector.scaleFactor)
            return true
        }
    }

    class CameraController(private val camera: Camera) {
        private var position = Float3(0f, 2f, 5f)
        private var yaw = 0f
        private var pitch = 0f

        private val lookSensitivity = 0.2f

        fun handleTouch(event: MotionEvent) {}

        fun rotateCamera(dx: Float, dy: Float) {
            yaw += dx * lookSensitivity
            pitch = (pitch + dy * lookSensitivity).coerceIn(-89f, 89f)
            updateCamera()
        }

        fun zoomCamera(scaleFactor: Float) {
            val zoomSpeed = 2000.0f
            val direction = getDirection().normalize() // ✅ Ensure it's a unit vector

            val zoomAmount = zoomSpeed * (1.0f - scaleFactor)
            val newPosition = Float3(
                position.x + direction.x * zoomAmount,
                position.y + direction.y * zoomAmount,
                position.z + direction.z * zoomAmount
            )

            val distanceFromOrigin = sqrt(
                newPosition.x.pow(2) + newPosition.y.pow(2) + newPosition.z.pow(2)
            )

            val minDistance = 5f
            val maxDistance = 10000f

            if (distanceFromOrigin in minDistance..maxDistance) {
                position = newPosition // ✅ Now correctly assigned as Float3
            } else {
                Log.d("CameraController", "Zoom limited: $distanceFromOrigin")
            }

            updateCamera()
        }

        // ✅ Ensure normalization works
        fun Float3.normalize(): Float3 {
            val length = sqrt(this.x.pow(2) + this.y.pow(2) + this.z.pow(2))
            return if (length != 0f) {
                Float3(this.x / length, this.y / length, this.z / length)
            } else {
                this
            }
        }


        fun resetCamera() {
            position = Float3(0f, 2f, 5f)
            yaw = 0f
            pitch = 0f
            updateCamera()
        }

        private fun updateCamera() {
            val forward = getDirection()
            val target = position + forward
            camera.lookAt(
                position.x.toDouble(), position.y.toDouble(), position.z.toDouble(),
                target.x.toDouble(), target.y.toDouble(), target.z.toDouble(),
                0.0, 1.0, 0.0
            )
        }

        private fun getDirection(): Float3 {
            val yawRad = Math.toRadians(yaw.toDouble()).toFloat()
            val pitchRad = Math.toRadians(pitch.toDouble()).toFloat()
            return Float3(
                cos(yawRad) * cos(pitchRad),
                sin(pitchRad),
                sin(yawRad) * cos(pitchRad)
            )
        }

    }
}
