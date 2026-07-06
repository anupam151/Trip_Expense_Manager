package com.example.tripexpensemanager;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LedgerExportManager {

    private final Context context;
    private final TripDatabaseHelper dbHelper;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public LedgerExportManager(Context context, TripDatabaseHelper dbHelper) {
        this.context = context;
        this.dbHelper = dbHelper;
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    // ==========================================
    // 1. EXPORT TO CSV (EXCEL) - ALL MEMBERS
    // ==========================================
    @SuppressWarnings("unused")
    public void exportAllMembersToCsv(Uri fileUri, String tripId, ArrayList<String> allMembers) {
        Toast.makeText(context, "Generating Excel file...", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            try (OutputStream outputStream = context.getContentResolver().openOutputStream(fileUri)) {
                if (outputStream == null) return;

                StringBuilder csvBuilder = new StringBuilder();
                csvBuilder.append("Trip Expense Report\n\n");

                for (String member : allMembers) {
                    csvBuilder.append("Member Ledger: ").append(member).append("\n");
                    csvBuilder.append("Date,Purpose,Type,Amount (Paid/Used)\n");

                    try (Cursor c = dbHelper.getReadableDatabase().rawQuery(
                            "SELECT date, purpose, type, amount FROM unified_ledger_view WHERE trip_id = ? AND member_name = ?",
                            new String[]{tripId, member})) {

                        while (c.moveToNext()) {
                            String date = c.getString(0);
                            String purpose = c.getString(1).replace(",", " ");
                            String type = c.getString(2);
                            double amount = c.getDouble(3);

                            csvBuilder.append(String.format(Locale.US, "%s,%s,%s,%.2f\n", date, purpose, type, amount));
                        }
                    }
                    csvBuilder.append("\n\n");
                }

                outputStream.write(csvBuilder.toString().getBytes());
                outputStream.flush();
                mainHandler.post(() -> Toast.makeText(context, "Excel Export Successful!", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(context, "Export Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    // ==========================================
    // 2. EXPORT TO PDF - ALL MEMBERS (PAGINATED)
    // ==========================================
    @SuppressWarnings("unused")
    public void exportAllMembersToPdf(Uri fileUri, String tripId, ArrayList<String> allMembers) {
        Toast.makeText(context, "Generating PDF...", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            try (OutputStream outputStream = context.getContentResolver().openOutputStream(fileUri)) {
                if (outputStream == null) return;

                PdfDocument document = new PdfDocument();
                Paint paint = new Paint();
                Paint titlePaint = new Paint();

                titlePaint.setTextSize(18f);
                titlePaint.setFakeBoldText(true);
                titlePaint.setColor(Color.parseColor("#85022E"));
                paint.setTextSize(12f);
                paint.setColor(Color.BLACK);

                int pageWidth = 595;
                int pageHeight = 842;
                int margin = 50;

                for (String member : allMembers) {
                    PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.getPages().size() + 1).create();
                    PdfDocument.Page page = document.startPage(pageInfo);
                    Canvas canvas = page.getCanvas();

                    int yPosition = margin;

                    canvas.drawText("Ledger Report: " + member, margin, yPosition, titlePaint);
                    yPosition += 30;

                    canvas.drawText("Date", margin, yPosition, titlePaint);
                    canvas.drawText("Purpose", margin + 100, yPosition, titlePaint);
                    canvas.drawText("Amount", pageWidth - 100, yPosition, titlePaint);
                    yPosition += 20;

                    try (Cursor c = dbHelper.getReadableDatabase().rawQuery(
                            "SELECT date, purpose, type, amount FROM unified_ledger_view WHERE trip_id = ? AND member_name = ?",
                            new String[]{tripId, member})) {

                        while (c.moveToNext()) {
                            if (yPosition > pageHeight - margin) {
                                document.finishPage(page);
                                pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.getPages().size() + 1).create();
                                page = document.startPage(pageInfo);
                                canvas = page.getCanvas();
                                yPosition = margin;
                            }

                            String date = c.getString(0) != null ? c.getString(0) : "N/A";
                            String purpose = c.getString(1) != null ? c.getString(1) : "-";
                            double amount = c.getDouble(3);

                            canvas.drawText(date, margin, yPosition, paint);
                            String shortPurpose = purpose.length() > 30 ? purpose.substring(0, 27) + "..." : purpose;
                            canvas.drawText(shortPurpose, margin + 100, yPosition, paint);
                            canvas.drawText(String.format(Locale.US, "%.2f", amount), pageWidth - 100, yPosition, paint);
                            yPosition += 20;
                        }
                    }
                    document.finishPage(page);
                }
                document.writeTo(outputStream);
                document.close();
                mainHandler.post(() -> Toast.makeText(context, "PDF Export Successful!", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(context, "Export Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    // ==========================================
    // 3. EXPORT TO CSV (EXCEL) - COMPLETE LEDGER
    // ==========================================

    public void exportCompleteLedgerToCsv(Uri fileUri, String tripId, ArrayList<String> members) {
        Toast.makeText(context, "Generating Excel file...", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            try (OutputStream outputStream = context.getContentResolver().openOutputStream(fileUri)) {
                if (outputStream == null) return;

                StringBuilder csv = new StringBuilder();

                // 1. BUILD HEADER ROW
                csv.append("Date,Purpose,Total Amount");
                for (String m : members) {
                    csv.append(",").append(m).append(" Credit");
                    csv.append(",").append(m).append(" Debit");
                }
                csv.append("\n");

                double[] totalPaid = new double[members.size()];
                double[] totalUsed = new double[members.size()];

                // 2. BUILD DATA ROWS
                try (Cursor cursor = dbHelper.getUnifiedLedger(tripId)) {
                    while (cursor.moveToNext()) {
                        String date = cursor.getString(cursor.getColumnIndexOrThrow("date"));
                        String purpose = cursor.getString(cursor.getColumnIndexOrThrow("purpose"));
                        double amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount"));
                        String paidBy = cursor.getString(cursor.getColumnIndexOrThrow("paid_by"));
                        String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                        String sharedWith = cursor.getString(cursor.getColumnIndexOrThrow("expense_shared_with"));
                        String[] sharedArray = (sharedWith != null && !sharedWith.isEmpty()) ? sharedWith.split(",") : new String[0];

                        String safePurpose = (purpose != null) ? purpose.replace(",", " ") : "";
                        String safeDate = (date != null) ? date : "N/A";

                        csv.append(safeDate).append(",")
                                .append(safePurpose).append(",")
                                .append(String.format(Locale.US, "%.2f", amount));

                        for (int i = 0; i < members.size(); i++) {
                            String m = members.get(i);
                            double paidVal = 0.0;
                            double usedVal = 0.0;

                            if ("Expense".equals(type)) {
                                double share = (sharedArray.length > 0) ? (amount / sharedArray.length) : 0.0;
                                if (m.equals(paidBy)) paidVal = amount;
                                if (isParticipant(m, sharedArray)) usedVal = share;
                            } else if ("Payment".equals(type) && m.equals(paidBy)) {
                                paidVal = amount;
                            }

                            totalPaid[i] += paidVal;
                            totalUsed[i] += usedVal;

                            csv.append(",").append(String.format(Locale.US, "%.2f", paidVal))
                                    .append(",").append(String.format(Locale.US, "%.2f", usedVal));
                        }
                        csv.append("\n");
                    }
                }

                // 3. BUILD TOTALS ROW
                csv.append("TOTALS,-,-");
                for (int i = 0; i < members.size(); i++) {
                    csv.append(",").append(String.format(Locale.US, "%.2f", totalPaid[i]))
                            .append(",").append(String.format(Locale.US, "%.2f", totalUsed[i]));
                }
                csv.append("\n");

                outputStream.write(csv.toString().getBytes());
                outputStream.flush();

                mainHandler.post(() -> Toast.makeText(context, "Excel Export Successful!", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(context, "Export Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private boolean isParticipant(String memberName, String[] sharedArray) {
        for (String s : sharedArray) {
            if (s.trim().equalsIgnoreCase(memberName.trim())) return true;
        }
        return false;
    }
}