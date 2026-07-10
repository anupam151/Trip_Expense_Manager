package com.example.tripexpensemanager;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.UUID;
import androidx.annotation.Keep;

@Keep // <--- Add this annotation
public class TripDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "TripManager.db";
    private static final int DATABASE_VERSION = 8; // Version updated

    public static final String TABLE_TRIPS = "trips";
    public static final String COLUMN_INTERNAL_ID = "id";
    public static final String COLUMN_TRIP_ID = "trip_unique_id";
    public static final String COLUMN_TRIP_NAME = "trip_name";
    public static final String COLUMN_DESTINATION = "destination";
    public static final String COLUMN_MEMBERS = "all_members_name";
    public static final String COLUMN_MEMBER_COUNT = "no_of_member";
    public static final String COLUMN_START_DATE = "journey_start_date";
    public static final String COLUMN_END_DATE = "journey_end_date";
    public static final String COLUMN_IS_PINNED = "is_pinned";

    public static final String TABLE_EXPENSES = "expenses";
    public static final String COLUMN_EXPENSE_ID = "expense_id";
    public static final String COLUMN_EXPENSE_TRIP_ID = "expense_trip_id";
    public static final String COLUMN_EXPENSE_PURPOSE = "expense_purpose";
    public static final String COLUMN_EXPENSE_AMOUNT = "expense_amount";
    public static final String COLUMN_EXPENSE_PAID_BY = "expense_paid_by";
    public static final String COLUMN_EXPENSE_SHARED_WITH = "expense_shared_with";
    public static final String COLUMN_EXPENSE_DATE = "expense_date";

    public static final String TABLE_PAYMENTS = "payments";
    public static final String COLUMN_PAYMENT_ID = "payment_id";
    public static final String COLUMN_PAYMENT_TRIP_ID = "payment_trip_id";
    public static final String COLUMN_PAYMENT_BY = "payment_by";
    public static final String COLUMN_PAYMENT_DATE = "payment_date";
    public static final String COLUMN_PAYMENT_AMOUNT = "payment_amount";

    public static final String TABLE_TRIP_MEMBERS = "trip_members";

    private static final String CREATE_TABLE_TRIPS = "CREATE TABLE " + TABLE_TRIPS + " ("
            + COLUMN_INTERNAL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COLUMN_TRIP_ID + " TEXT UNIQUE, "
            + COLUMN_TRIP_NAME + " TEXT, "
            + COLUMN_DESTINATION + " TEXT, "
            + COLUMN_MEMBERS + " TEXT, "
            + COLUMN_MEMBER_COUNT + " INTEGER, "
            + COLUMN_START_DATE + " TEXT, "
            + COLUMN_END_DATE + " TEXT, "
            + COLUMN_IS_PINNED + " INTEGER DEFAULT 0"
            + ");";

    private static final String CREATE_TABLE_EXPENSES = "CREATE TABLE " + TABLE_EXPENSES + " ("
            + COLUMN_EXPENSE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COLUMN_EXPENSE_TRIP_ID + " TEXT, "
            + COLUMN_EXPENSE_PURPOSE + " TEXT, "
            + COLUMN_EXPENSE_AMOUNT + " REAL, "
            + COLUMN_EXPENSE_PAID_BY + " TEXT, "
            + COLUMN_EXPENSE_SHARED_WITH + " TEXT, "
            + COLUMN_EXPENSE_DATE + " TEXT, "
            + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP"
            + ");";

    private static final String CREATE_TABLE_PAYMENTS = "CREATE TABLE " + TABLE_PAYMENTS + " ("
            + COLUMN_PAYMENT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COLUMN_PAYMENT_TRIP_ID + " TEXT, "
            + COLUMN_PAYMENT_BY + " TEXT, "
            + COLUMN_PAYMENT_DATE + " TEXT, "
            + COLUMN_PAYMENT_AMOUNT + " REAL, "
            + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP"
            + ");";

    private static final String CREATE_TABLE_MEMBERS = "CREATE TABLE " + TABLE_TRIP_MEMBERS + " ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "trip_id TEXT, "
            + "member_name TEXT, "
            + "status TEXT DEFAULT 'Active'"
            + ");";

    public TripDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_TRIPS);
        db.execSQL(CREATE_TABLE_EXPENSES);
        db.execSQL(CREATE_TABLE_PAYMENTS);
        db.execSQL(CREATE_TABLE_MEMBERS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRIPS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_EXPENSES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PAYMENTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRIP_MEMBERS);
        db.execSQL("ALTER TABLE expenses ADD COLUMN expense_created_at TEXT DEFAULT '01/01/2026 00:00:00'");
        db.execSQL("ALTER TABLE payments ADD COLUMN payment_created_at TEXT DEFAULT '01/01/2026 00:00:00'");
        onCreate(db);
    }

    public String insertTrip(String tripName, String destination, String members, int memberCount, String startDate, String endDate) {
        String uniqueToken = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
        String generatedTripId = "TRIP-" + uniqueToken;
        ContentValues values = new ContentValues();
        values.put(COLUMN_TRIP_ID, generatedTripId);
        values.put(COLUMN_TRIP_NAME, tripName);
        values.put(COLUMN_DESTINATION, destination);
        values.put(COLUMN_MEMBERS, members);
        values.put(COLUMN_MEMBER_COUNT, memberCount);
        values.put(COLUMN_START_DATE, startDate);
        values.put(COLUMN_END_DATE, endDate);
        SQLiteDatabase db = this.getWritableDatabase();
        long id = db.insert(TABLE_TRIPS, null, values);
        return (id != -1) ? generatedTripId : null;
    }

    public Cursor getAllTripsCursor() {
        return getReadableDatabase().rawQuery("SELECT * FROM " + TABLE_TRIPS + " ORDER BY " + COLUMN_INTERNAL_ID + " ASC", null);
    }

    public void deleteTrip(String tripId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_EXPENSES, COLUMN_EXPENSE_TRIP_ID + " = ?", new String[]{tripId});
        db.delete(TABLE_PAYMENTS, COLUMN_PAYMENT_TRIP_ID + " = ?", new String[]{tripId});
        db.delete(TABLE_TRIPS, COLUMN_TRIP_ID + " = ?", new String[]{tripId});
        db.delete(TABLE_TRIP_MEMBERS, "trip_id = ?", new String[]{tripId});
    }

    public boolean updateTrip(String tripId, String tripName, String destination, String members, int memberCount, String startDate, String endDate) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_TRIP_NAME, tripName);
        values.put(COLUMN_DESTINATION, destination);
        values.put(COLUMN_MEMBERS, members);
        values.put(COLUMN_MEMBER_COUNT, memberCount);
        values.put(COLUMN_START_DATE, startDate);
        values.put(COLUMN_END_DATE, endDate);
        return getWritableDatabase().update(TABLE_TRIPS, values, COLUMN_TRIP_ID + " = ?", new String[]{tripId}) > 0;
    }

    public int getPinnedTripsCount() {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + TABLE_TRIPS + " WHERE " + COLUMN_IS_PINNED + " = 1", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public int toggleTripPinStatus(String tripId) {
        int currentStatus = 0;
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT " + COLUMN_IS_PINNED + " FROM " + TABLE_TRIPS + " WHERE " + COLUMN_TRIP_ID + " = ?", new String[]{tripId})) {
            if (cursor.moveToFirst()) currentStatus = cursor.getInt(0);
        }
        ContentValues values = new ContentValues();
        if (currentStatus == 1) {
            values.put(COLUMN_IS_PINNED, 0);
            getWritableDatabase().update(TABLE_TRIPS, values, COLUMN_TRIP_ID + " = ?", new String[]{tripId});
            return 0;
        } else {
            if (getPinnedTripsCount() >= 1) return -1;
            values.put(COLUMN_IS_PINNED, 1);
            getWritableDatabase().update(TABLE_TRIPS, values, COLUMN_TRIP_ID + " = ?", new String[]{tripId});
            return 1;
        }
    }

    public Cursor getPinnedTripsCursor() {
        // Changed LIMIT 2 to LIMIT 1
        return getReadableDatabase().rawQuery("SELECT * FROM " + TABLE_TRIPS + " WHERE " + COLUMN_IS_PINNED + " = 1 LIMIT 1", null);
    }
    @SuppressWarnings("UnusedReturnValue")
    public long insertExpense(String tripId, String purpose, double amount, String paidBy, String sharedWith, String dateStr) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_EXPENSE_TRIP_ID, tripId);
        values.put(COLUMN_EXPENSE_PURPOSE, purpose);
        values.put(COLUMN_EXPENSE_AMOUNT, amount);
        values.put(COLUMN_EXPENSE_PAID_BY, paidBy);
        values.put(COLUMN_EXPENSE_SHARED_WITH, sharedWith);
        values.put(COLUMN_EXPENSE_DATE, dateStr);
        return getWritableDatabase().insert(TABLE_EXPENSES, null, values);
    }

    public Cursor getExpensesForTripCursor(String tripId) {
        return getReadableDatabase().rawQuery("SELECT * FROM " + TABLE_EXPENSES + " WHERE " + COLUMN_EXPENSE_TRIP_ID + " = ?", new String[]{tripId});
    }

    public double getTripTotalExpenses(String tripId) {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT SUM(" + COLUMN_EXPENSE_AMOUNT + ") FROM " + TABLE_EXPENSES + " WHERE " + COLUMN_EXPENSE_TRIP_ID + " = ?", new String[]{tripId})) {
            return cursor.moveToFirst() ? cursor.getDouble(0) : 0.0;
        }
    }

    public long insertPayment(String tripId, String paymentBy, String dateStr, double amount) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_PAYMENT_TRIP_ID, tripId);
        values.put(COLUMN_PAYMENT_BY, paymentBy);
        values.put(COLUMN_PAYMENT_DATE, dateStr);
        values.put(COLUMN_PAYMENT_AMOUNT, amount);
        return getWritableDatabase().insert(TABLE_PAYMENTS, null, values);
    }

    public Cursor getPaymentsForTripCursor(String tripId) {
        return getReadableDatabase().rawQuery("SELECT * FROM " + TABLE_PAYMENTS + " WHERE " + COLUMN_PAYMENT_TRIP_ID + " = ?", new String[]{tripId});
    }

    public double getTripTotalPaymentsReceived(String tripId) {
        double p = 0.0, e = 0.0;
        try (Cursor c1 = getReadableDatabase().rawQuery("SELECT SUM(" + COLUMN_PAYMENT_AMOUNT + ") FROM " + TABLE_PAYMENTS + " WHERE " + COLUMN_PAYMENT_TRIP_ID + " = ?", new String[]{tripId})) { if (c1.moveToFirst()) p = c1.getDouble(0); }
        try (Cursor c2 = getReadableDatabase().rawQuery("SELECT SUM(" + COLUMN_EXPENSE_AMOUNT + ") FROM " + TABLE_EXPENSES + " WHERE " + COLUMN_EXPENSE_TRIP_ID + " = ? AND " + COLUMN_EXPENSE_PAID_BY + " != 'Fund'", new String[]{tripId})) { if (c2.moveToFirst()) e = c2.getDouble(0); }
        return p + e;
    }

    public double getFundBalance(String tripId) {
        double p = 0.0, fe = 0.0;
        try (Cursor c1 = getReadableDatabase().rawQuery("SELECT SUM(" + COLUMN_PAYMENT_AMOUNT + ") FROM " + TABLE_PAYMENTS + " WHERE " + COLUMN_PAYMENT_TRIP_ID + " = ?", new String[]{tripId})) { if (c1.moveToFirst()) p = c1.getDouble(0); }
        try (Cursor c2 = getReadableDatabase().rawQuery("SELECT SUM(" + COLUMN_EXPENSE_AMOUNT + ") FROM " + TABLE_EXPENSES + " WHERE " + COLUMN_EXPENSE_TRIP_ID + " = ? AND " + COLUMN_EXPENSE_PAID_BY + " = 'Fund'", new String[]{tripId})) { if (c2.moveToFirst()) fe = c2.getDouble(0); }
        return p - fe;
    }

    // --- NEW EDIT & DELETE METHODS ---

    public void deleteExpense(int expenseId) {
        getWritableDatabase().delete(TABLE_EXPENSES, COLUMN_EXPENSE_ID + " = ?", new String[]{String.valueOf(expenseId)});
    }

    public void deletePayment(int paymentId) {
        getWritableDatabase().delete(TABLE_PAYMENTS, COLUMN_PAYMENT_ID + " = ?", new String[]{String.valueOf(paymentId)});
    }
    @SuppressWarnings("UnusedReturnValue")
    public boolean updateExpense(int expenseId, String purpose, double amount, String paidBy, String sharedWith, String dateStr) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_EXPENSE_PURPOSE, purpose);
        values.put(COLUMN_EXPENSE_AMOUNT, amount);
        values.put(COLUMN_EXPENSE_PAID_BY, paidBy);
        values.put(COLUMN_EXPENSE_SHARED_WITH, sharedWith);
        values.put(COLUMN_EXPENSE_DATE, dateStr);
        return getWritableDatabase().update(TABLE_EXPENSES, values, COLUMN_EXPENSE_ID + " = ?", new String[]{String.valueOf(expenseId)}) > 0;
    }

    public boolean updatePayment(int paymentId, String paymentBy, String dateStr, double amount) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_PAYMENT_BY, paymentBy);
        values.put(COLUMN_PAYMENT_DATE, dateStr);
        values.put(COLUMN_PAYMENT_AMOUNT, amount);
        return getWritableDatabase().update(TABLE_PAYMENTS, values, COLUMN_PAYMENT_ID + " = ?", new String[]{String.valueOf(paymentId)}) > 0;
    }

    public Cursor getExpenseById(int id) {
        return getReadableDatabase().rawQuery("SELECT * FROM " + TABLE_EXPENSES + " WHERE " + COLUMN_EXPENSE_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public Cursor getPaymentById(int id) {
        return getReadableDatabase().rawQuery("SELECT * FROM " + TABLE_PAYMENTS + " WHERE " + COLUMN_PAYMENT_ID + " = ?", new String[]{String.valueOf(id)});
    }

    // --- UPDATED QUERY FOR LEDGER (NOW FETCHES TRANSACTION IDs) ---
    public Cursor getUnifiedLedger(String tripId) {
        String query = "SELECT expense_id as trans_id, " + COLUMN_EXPENSE_DATE + " as date, " + COLUMN_EXPENSE_PURPOSE + " as purpose, " + COLUMN_EXPENSE_AMOUNT + " as amount, " + COLUMN_EXPENSE_PAID_BY + " as paid_by, " + COLUMN_EXPENSE_SHARED_WITH + " as expense_shared_with, 'Expense' as type, created_at FROM " + TABLE_EXPENSES + " WHERE " + COLUMN_EXPENSE_TRIP_ID + " = ? "
                + "UNION ALL SELECT payment_id as trans_id, " + COLUMN_PAYMENT_DATE + " as date, 'Payment' as purpose, " + COLUMN_PAYMENT_AMOUNT + " as amount, " + COLUMN_PAYMENT_BY + " as paid_by, '' as expense_shared_with, 'Payment' as type, created_at FROM " + TABLE_PAYMENTS + " WHERE " + COLUMN_PAYMENT_TRIP_ID + " = ? ORDER BY created_at ASC";
        return getReadableDatabase().rawQuery(query, new String[]{tripId, tripId});
    }
}