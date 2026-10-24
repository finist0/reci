package org.kostja.reci.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ProgressDb(ctx: Context) : SQLiteOpenHelper(ctx, "progress.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE progress (key TEXT PRIMARY KEY, state INTEGER NOT NULL, " +
                "fav INTEGER NOT NULL, seen INTEGER NOT NULL, last INTEGER NOT NULL, " +
                "due INTEGER NOT NULL DEFAULT 0, step INTEGER NOT NULL DEFAULT 0, " +
                "phase INTEGER NOT NULL DEFAULT 0, streak INTEGER NOT NULL DEFAULT 0)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE progress ADD COLUMN due INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE progress ADD COLUMN step INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE progress ADD COLUMN phase INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE progress ADD COLUMN streak INTEGER NOT NULL DEFAULT 0")
            // Words marked known before the learning stages existed count as learned.
            db.execSQL("UPDATE progress SET phase = 3 WHERE state = 3")
        }
    }

    fun loadAll(): Map<String, Progress> {
        val out = HashMap<String, Progress>()
        readableDatabase.rawQuery("SELECT key, state, fav, seen, last, due, step, phase, streak FROM progress", null).use { c ->
            while (c.moveToNext()) {
                out[c.getString(0)] = Progress(
                    state = State.entries.getOrElse(c.getInt(1)) { State.NEW },
                    fav = c.getInt(2) != 0,
                    seen = c.getInt(3),
                    last = c.getLong(4),
                    due = c.getLong(5),
                    step = c.getInt(6),
                    phase = c.getInt(7),
                    streak = c.getInt(8),
                )
            }
        }
        return out
    }

    fun put(key: String, p: Progress) {
        val v = ContentValues().apply {
            put("key", key)
            put("state", p.state.ordinal)
            put("fav", if (p.fav) 1 else 0)
            put("seen", p.seen)
            put("last", p.last)
            put("due", p.due)
            put("step", p.step)
            put("phase", p.phase)
            put("streak", p.streak)
        }
        writableDatabase.insertWithOnConflict("progress", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun clear() {
        writableDatabase.delete("progress", null, null)
    }
}
