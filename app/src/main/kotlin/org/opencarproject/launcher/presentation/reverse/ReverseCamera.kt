package org.opencarproject.launcher.presentation.reverse

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.Surface

/**
 * Preview of one Camera2 device onto a [SurfaceTexture].
 *
 * Analog (CVBS) reverse cameras are 720x480: asking for that native size avoids the HAL's own
 * 480i→1080p upscale, the GPU scales once with filtering, and HIGH_QUALITY edge/noise modes
 * sharpen what the decoder delivers.
 */
class ReverseCamera(context: Context, private val cameraId: String, private val onError: () -> Unit) {

    private val manager = context.getSystemService(CameraManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var surface: Surface? = null
    private var closed = false

    @SuppressLint("MissingPermission") // checked by the caller; a SecurityException lands in onError
    fun open(texture: SurfaceTexture) {
        runCatching {
            val size = previewSize()
            texture.setDefaultBufferSize(size.width, size.height)
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (closed) return camera.close()
                    device = camera
                    startPreview(camera, texture)
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close(); onError() }
                override fun onError(camera: CameraDevice, error: Int) { camera.close(); onError() }
            }, handler)
        }.onFailure { onError() }
    }

    @Suppress("DEPRECATION") // SessionConfiguration needs API 28; minSdk is 26
    private fun startPreview(camera: CameraDevice, texture: SurfaceTexture) {
        val target = Surface(texture).also { surface = it }
        camera.createCaptureSession(listOf(target), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) {
                if (closed) return s.close()
                session = s
                val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(target)
                    set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
                    set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                }.build()
                runCatching { s.setRepeatingRequest(request, null, handler) }.onFailure { onError() }
            }
            override fun onConfigureFailed(s: CameraCaptureSession) = onError()
        }, handler)
    }

    /** 720x480 when offered, else the smallest size at least 640 wide (still near the source). */
    private fun previewSize(): Size {
        val sizes = manager.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(SurfaceTexture::class.java).orEmpty()
        return sizes.firstOrNull { it.width == 720 && it.height == 480 }
            ?: sizes.filter { it.width >= 640 }.minByOrNull { it.width * it.height }
            ?: Size(720, 480)
    }

    fun close() {
        closed = true
        runCatching { session?.close() }
        runCatching { device?.close() }
        surface?.release()
        session = null; device = null; surface = null
    }
}
