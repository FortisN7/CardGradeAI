package fortis.nicholas.cardgradeai

import android.app.Application
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig

class MyApplication : Application(), CameraXConfig.Provider {

    val db by lazy { UploadDatabase.getDatabase(this) }
    val uploadDao by lazy { db.uploadDao() }

    override fun getCameraXConfig(): CameraXConfig {
        return Camera2Config.defaultConfig()
    }
}
