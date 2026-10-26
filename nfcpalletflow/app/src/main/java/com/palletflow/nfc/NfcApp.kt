package com.palletflow.nfc

import android.app.Application
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap

class NfcApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Android solo informa el estado de la linterna por callback; se guarda para poder alternarla.
        runCatching {
            (getSystemService(CAMERA_SERVICE) as CameraManager).registerTorchCallback(
                object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        torchState[cameraId] = enabled
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        }
    }

    companion object {
        val torchState = ConcurrentHashMap<String, Boolean>()
    }
}
