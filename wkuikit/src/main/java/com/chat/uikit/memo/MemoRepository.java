package com.chat.uikit.memo;

import android.content.ContentValues;
import android.database.Cursor;

import com.chat.base.WKBaseApplication;
import com.chat.base.db.DBHelper;

import java.util.ArrayList;
import java.util.List;

public final class MemoRepository {
    private static final String TABLE = "memo_records";

    private MemoRepository() {
    }

    public static List<MemoRecord> getAll() throws Exception {
        ArrayList<MemoRecord> result = new ArrayList<>();
        Cursor cursor = db().rawQuery("SELECT * FROM " + TABLE + " ORDER BY updated_at DESC", null);
        if (cursor == null) {
            return result;
        }
        try {
            while (cursor.moveToNext()) {
                result.add(read(cursor));
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    public static MemoRecord get(long id) throws Exception {
        Cursor cursor = db().rawQuery("SELECT * FROM " + TABLE + " WHERE id=? LIMIT 1",
                new String[]{String.valueOf(id)});
        if (cursor == null) {
            return null;
        }
        try {
            return cursor.moveToFirst() ? read(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    public static long save(MemoRecord record) throws Exception {
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("title", MemoCrypto.encrypt(record.title));
        values.put("content", MemoCrypto.encrypt(record.content));
        values.put("updated_at", now);
        if (record.id > 0) {
            boolean updated = db().update(TABLE, values, "id=?", new String[]{String.valueOf(record.id)});
            if (!updated) {
                throw new IllegalStateException("Memo update failed");
            }
            record.updatedAt = now;
            return record.id;
        }
        values.put("created_at", now);
        long id = db().insert(TABLE, values);
        if (id <= 0) {
            throw new IllegalStateException("Memo insert failed");
        }
        record.id = id;
        record.createdAt = now;
        record.updatedAt = now;
        return id;
    }

    public static boolean delete(long id) {
        return db().delete(TABLE, "id=?", new String[]{String.valueOf(id)});
    }

    private static MemoRecord read(Cursor cursor) throws Exception {
        MemoRecord record = new MemoRecord();
        record.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        record.title = MemoCrypto.decrypt(cursor.getString(cursor.getColumnIndexOrThrow("title")));
        record.content = MemoCrypto.decrypt(cursor.getString(cursor.getColumnIndexOrThrow("content")));
        record.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
        record.updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"));
        return record;
    }

    private static DBHelper db() {
        DBHelper helper = WKBaseApplication.getInstance().getDbHelper();
        if (helper == null || helper.getDB() == null) {
            throw new IllegalStateException("Database unavailable");
        }
        return helper;
    }
}
