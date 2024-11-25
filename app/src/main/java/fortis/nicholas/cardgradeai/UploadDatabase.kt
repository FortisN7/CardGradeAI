package fortis.nicholas.cardgradeai

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Upload::class], version = 1, exportSchema = false)
abstract class UploadDatabase : RoomDatabase() {
    abstract fun uploadDao(): UploadDao

    companion object {
        @Volatile
        private var INSTANCE: UploadDatabase? = null

        fun getDatabase(context: Context): UploadDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UploadDatabase::class.java,
                    "upload_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
