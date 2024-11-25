package fortis.nicholas.cardgradeai

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "uploads")
data class Upload(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imagePath: String,
    val apiResponse: String
)