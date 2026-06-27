package com.example.tripexpensemanager;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.UUID;

public class TripDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "TripManager.db";
    private static final int DATABASE_VERSION = 6;

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
            + COLUMN_EXPENSE_DATE + " TEXT"
            + ");";

    private static final String CREATE_TABLE_PAYMENTS = "CREATE TABLE " + TABLE_PAYMENTS + " ("
            + COLUMN_PAYMENT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COLUMN_PAYMENT_TRIP_ID + " TEXT, "
            + COLUMN_PAYMENT_BY + " TEXT, "
            + COLUMN_PAYMENT_DATE + " TEXT, "
            + COLUMN_PAYMENT_AMOUNT + " REAL"
            + ");";

    public TripDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_TRIPS);
        db.execSQL(CREATE_TABLE_EXPENSES);
        db.execSQL(CREATE_TABLE_PAYMENTS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE " + TABLE_TRIPS + " ADD COLUMN " + COLUMN_IS_PINNED + " INTEGER DEFAULT 0");
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE " + TABLE_EXPENSES + " ADD COLUMN " + COLUMN_EXPENSE_DATE + " TEXT");
        }
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
        values.put(COLUMN_IS_PINNED, 0);

        SQLiteDatabase db = this.getWritableDatabase();
        long id = db.insert(TABLE_TRIPS, null, values);
        return (id != -1) ? generatedTripId : null;
    }

    public Cursor getAllTripsCursor() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_TRIPS + " ORDER BY " + COLUMN_INTERNAL_ID + " ASC", null);
    }

    public void deleteTrip(String tripId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_EXPENSES, COLUMN_EXPENSE_TRIP_ID + " = ?", new String[]{tripId});
        db.delete(TABLE_PAYMENTS, COLUMN_PAYMENT_TRIP_ID + " = ?", new String[]{tripId});
        db.delete(TABLE_TRIPS, COLUMN_TRIP_ID + " = ?", new String[]{tripId});
    }

    public boolean updateTrip(String tripId, String tripName, String destination, String members, int memberCount, String startDate, String endDate) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_TRIP_NAME, tripName);
        values.put(COLUMN_DESTINATION, destination);
        values.put(COLUMN_MEMBERS, members);
        values.put(COLUMN_MEMBER_COUNT, memberCount);
        values.put(COLUMN_START_DATE, startDate);
        values.put(COLUMN_END_DATE, endDate);

        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.update(TABLE_TRIPS, values, COLUMN_TRIP_ID + " = ?", new String[]{tripId});
        return result > 0;
    }

    public int getPinnedTripsCount() {
        int count = 0;
        String query = "SELECT COUNT(*) FROM " + TABLE_TRIPS + " WHERE " + COLUMN_IS_PINNED + " = 1";
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(query, null)) {
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
        }
        return count;
    }

    // FIXED: Safely manages connection states without auto-closing the parent DB mid-operation
    public int toggleTripPinStatus(String tripId) {
        int currentStatus = 0;
        String statusQuery = "SELECT " + COLUMN_IS_PINNED + " FROM " + TABLE_TRIPS + " WHERE " + COLUMN_TRIP_ID + " = ?";

        SQLiteDatabase db = this.getWritableDatabase();
        try (Cursor cursor = db.rawQuery(statusQuery, new String[]{tripId})) {
            if (cursor.moveToFirst()) {
                currentStatus = cursor.getInt(0);
            }
        }

        ContentValues values = new ContentValues();
        if (currentStatus == 1) {
            values.put(COLUMN_IS_PINNED, 0);
            db.update(TABLE_TRIPS, values, COLUMN_TRIP_ID + " = ?", new String[]{tripId});
            return 0; // Unpinned
        } else {
            if (getPinnedTripsCount() >= 2) {
                return -1; // Limit hit, block pin action
            }
            values.put(COLUMN_IS_PINNED, 1);
            db.update(TABLE_TRIPS, values, COLUMN_TRIP_ID + " = ?", new String[]{tripId});
            return 1; // Pinned
        }
    }

    public Cursor getPinnedTripsCursor() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_TRIPS + " WHERE " + COLUMN_IS_PINNED + " = 1 LIMIT 2", null);
    }

    public long insertExpense(String tripId, String purpose, double amount, String paidBy, String sharedWith, String dateStr) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_EXPENSE_TRIP_ID, tripId);
        values.put(COLUMN_EXPENSE_PURPOSE, purpose);
        values.put(COLUMN_EXPENSE_AMOUNT, amount);
        values.put(COLUMN_EXPENSE_PAID_BY, paidBy);
        values.put(COLUMN_EXPENSE_SHARED_WITH, sharedWith);
        values.put(COLUMN_EXPENSE_DATE, dateStr);

        SQLiteDatabase db = this.getWritableDatabase();
        return db.insert(TABLE_EXPENSES, null, values);
    }

    public Cursor getExpensesForTripCursor(String tripId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_EXPENSES + " WHERE " + COLUMN_EXPENSE_TRIP_ID + " = ?", new String[]{tripId});
    }

    public double getTripTotalExpenses(String tripId) {
        double total = 0.0;
        String query = "SELECT SUM(" + COLUMN_EXPENSE_AMOUNT + ") FROM " + TABLE_EXPENSES + " WHERE " + COLUMN_EXPENSE_TRIP_ID + " = ?";
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(query, new String[]{tripId})) {
            if (cursor.moveToFirst()) {
                total = cursor.getDouble(0);
            }
        }
        return total;
    }

    public long insertPayment(String tripId, String paymentBy, String dateStr, double amount) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_PAYMENT_TRIP_ID, tripId);
        values.put(COLUMN_PAYMENT_BY, paymentBy);
        values.put(COLUMN_PAYMENT_DATE, dateStr);
        values.put(COLUMN_PAYMENT_AMOUNT, amount);

        SQLiteDatabase db = this.getWritableDatabase();
        return db.insert(TABLE_PAYMENTS, null, values);
    }

    public Cursor getPaymentsForTripCursor(String tripId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_PAYMENTS + " WHERE " + COLUMN_PAYMENT_TRIP_ID + " = ?", new String[]{tripId});
    }

    public double getTripTotalPaymentsReceived(String tripId) {
        double directPaymentsSum = 0.0;
        double expenseOutlaysSum = 0.0;

        // 1. Sum of direct payments made into the pool
        String directQuery = "SELECT SUM(" + COLUMN_PAYMENT_AMOUNT + ") FROM " + TABLE_PAYMENTS +
                " WHERE " + COLUMN_PAYMENT_TRIP_ID + " = ?";

        // 2. Sum of expenses paid out-of-pocket by members (EXCLUDING the "Fund")
        // FIXED: Added the condition to ignore 'Fund'
        String expenseQuery = "SELECT SUM(" + COLUMN_EXPENSE_AMOUNT + ") FROM " + TABLE_EXPENSES +
                " WHERE " + COLUMN_EXPENSE_TRIP_ID + " = ? AND " + COLUMN_EXPENSE_PAID_BY + " != 'Fund'";

        SQLiteDatabase db = this.getReadableDatabase();

        try (Cursor cursor = db.rawQuery(directQuery, new String[]{tripId})) {
            if (cursor.moveToFirst()) {
                directPaymentsSum = cursor.getDouble(0);
            }
        }

        try (Cursor cursor = db.rawQuery(expenseQuery, new String[]{tripId})) {
            if (cursor.moveToFirst()) {
                expenseOutlaysSum = cursor.getDouble(0);
            }
        }

        return directPaymentsSum + expenseOutlaysSum;
    }

    // --- NEW METHOD TO CALCULATE REAL-TIME FUND BALANCE ---
    public double getFundBalance(String tripId) {
        double totalPayments = 0.0;
        double totalFundExpenses = 0.0;

        SQLiteDatabase db = this.getReadableDatabase();

        // 1. Get the sum of ALL payments made by anyone for this trip
        String paymentsQuery = "SELECT SUM(" + COLUMN_PAYMENT_AMOUNT + ") FROM " + TABLE_PAYMENTS +
                " WHERE " + COLUMN_PAYMENT_TRIP_ID + " = ?";
        try (Cursor cursor = db.rawQuery(paymentsQuery, new String[]{tripId})) {
            if (cursor.moveToFirst()) {
                totalPayments = cursor.getDouble(0);
            }
        }

        // 2. Get the sum of ALL expenses that were explicitly paid by the "Fund"
        String fundExpensesQuery = "SELECT SUM(" + COLUMN_EXPENSE_AMOUNT + ") FROM " + TABLE_EXPENSES +
                " WHERE " + COLUMN_EXPENSE_TRIP_ID + " = ? AND " + COLUMN_EXPENSE_PAID_BY + " = 'Fund'";
        try (Cursor cursor = db.rawQuery(fundExpensesQuery, new String[]{tripId})) {
            if (cursor.moveToFirst()) {
                totalFundExpenses = cursor.getDouble(0);
            }
        }

        // 3. The balance is total cash in minus total cash out of the fund
        return totalPayments - totalFundExpenses;
    }
}