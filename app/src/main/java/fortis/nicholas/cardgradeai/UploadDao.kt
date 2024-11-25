package fortis.nicholas.cardgradeai

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface UploadDao {
    @Insert
    suspend fun insert(upload: Upload)

    @Query("SELECT * FROM uploads ORDER BY id DESC")
    suspend fun getAllUploads(): List<Upload>

    @Query("SELECT * FROM uploads WHERE id = :id LIMIT 1")
    suspend fun getUploadById(id: Long): Upload

    @Query("DELETE FROM uploads")
    suspend fun deleteAll()

    @Query("SELECT * FROM uploads ORDER BY id DESC LIMIT 1")
    suspend fun getLatestUpload(): Upload

}
