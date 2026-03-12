package net.bluemangoo.messagerepeater.data

import android.content.Context
import androidx.room.*

@Entity(tableName = "app_rules")
data class AppRuleConfig(
    @PrimaryKey val packageName: String,
    val ruleTreeJson: String
)

@Dao
interface RuleDao {
    @Query("SELECT * FROM app_rules")
    suspend fun getAllRulesFlow(): List<AppRuleConfig>

    @Query("SELECT * FROM app_rules WHERE packageName = :pkg LIMIT 1")
    suspend fun getRuleByPackage(pkg: String): AppRuleConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRule(config: AppRuleConfig)

    @Query("DELETE FROM app_rules WHERE packageName = :pkg")
    suspend fun deleteRuleByPackage(pkg: String)
}

@Database(entities = [AppRuleConfig::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "repeater_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}