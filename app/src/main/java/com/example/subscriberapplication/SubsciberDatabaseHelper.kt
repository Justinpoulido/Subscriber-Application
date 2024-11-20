package com.example.subscriberapplication

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SubscriberDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "SubscriberData.db"
        const val DATABASE_VERSION = 1

        // Table and Column names
        const val TABLE_NAME = "LocationData"
        const val COLUMN_ID = "id"
        const val COLUMN_STUDENT_ID = "student_id"
        const val COLUMN_SPEED = "speed"
        const val COLUMN_LATITUDE = "latitude"
        const val COLUMN_LONGITUDE = "longitude"
        const val COLUMN_TIMESTAMP = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_STUDENT_ID TEXT,
                $COLUMN_SPEED REAL,
                $COLUMN_LATITUDE REAL,
                $COLUMN_LONGITUDE REAL,
                $COLUMN_TIMESTAMP TEXT
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun insertLocationData(studentId: String, speed: Double, latitude: Double, longitude: Double, timestamp: String) {
        val db = writableDatabase
        val contentValues = ContentValues().apply {
            put(COLUMN_STUDENT_ID, studentId)
            put(COLUMN_SPEED, speed)
            put(COLUMN_LATITUDE, latitude)
            put(COLUMN_LONGITUDE, longitude)
            put(COLUMN_TIMESTAMP, timestamp)
        }
        db.insert(TABLE_NAME, null, contentValues)
        db.close()
    }

    fun getAllLocationData(): List<LocationData> {
        val locationDataList = mutableListOf<LocationData>()
        val db = readableDatabase
        val cursor = db.query(TABLE_NAME, null, null, null, null, null, null)

        while (cursor.moveToNext()) {
            val id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID))
            val studentId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_STUDENT_ID))
            val speed = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_SPEED))
            val latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LATITUDE))
            val longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LONGITUDE))
            val timestamp = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))

            locationDataList.add(LocationData(id, studentId, speed, latitude, longitude, timestamp))
        }
        cursor.close()
        db.close()
        return locationDataList
    }
}

data class LocationData(
    val id: Int,
    val studentId: String,
    val speed: Double,
    val latitude: Double,
    val longitude: Double,
    val timestamp: String
)
