package com.bartonsolutions.ledkovac

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.widget.Toast
import com.bartonsolutions.ledkovac.Constants.DATEFORMAT
import java.util.*
import kotlin.collections.ArrayList

const val DATABASENAME = "LEDKOVACDB"
const val TABLENAME = "FlashRecords"
const val COL_SYNCED = "synced"
const val COL_DATE = "date"
const val COL_ID = "id"

class DatabaseHelper(var context: Context) : SQLiteOpenHelper(context, DATABASENAME, null, 1) {
    override fun onCreate(db: SQLiteDatabase?) {
        val createTable = "" +
                "CREATE TABLE " + TABLENAME + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_DATE + " DATETIME, " +
                COL_SYNCED + " BOOLEAN DEFAULT FALSE )"
        db?.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
    }

    fun insertFlash(date: Date) {
        val contentValues = ContentValues()
        contentValues.put(COL_DATE, DATEFORMAT.format(date))

        val result = this.writableDatabase.insert(TABLENAME, null, contentValues)
        if (result == (0).toLong()) {
            Toast.makeText(context, "Failed to insert bump", Toast.LENGTH_LONG).show()
        }
    }

    fun getNotSynced(): MutableList<FlashRecord> {
        val records: MutableList<FlashRecord> = ArrayList()
        val query = "SELECT * FROM $TABLENAME WHERE NOT(SYNCED)"
        val result = this.readableDatabase.rawQuery(query, null)
        if (result.moveToFirst()) {
            do {
                val record = FlashRecord(
                    result.getInt(result.getColumnIndex(COL_ID)),
                    DATEFORMAT.parse(result.getString(result.getColumnIndex(COL_DATE))),
                    result.getString(result.getColumnIndex(COL_SYNCED)).toBoolean()
                )
                records.add(record)
            } while (result.moveToNext())
        }
        result.close()
        return records
    }

    fun setSynced(id: Int) {
        val content = ContentValues()
        content.put(COL_SYNCED, true)

        val result = this.writableDatabase.update(TABLENAME, content, "$COL_ID = $id", null)

        if (result == -1) {
            Toast.makeText(context, "Failed to update $id", Toast.LENGTH_LONG).show()
        }
    }

    fun getFlashesCount(): Int {
        var result = 0
        val cursor = this.readableDatabase.rawQuery("SELECT count(*) FROM $TABLENAME", null)
        if (cursor.moveToFirst()) {
            result = cursor.getInt(0)
        }
        return result
    }
}

class FlashRecord(
    val id: Int,
    val date: Date,
    val synced: Boolean
)
