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
import android.graphics.DashPathEffect;
import android.graphics.RectF;
import java.text.SimpleDateFormat;
import java.util.Date;

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
    // ==========================================
    // 4. EXPORT TO CSV (EXCEL) - INDIVIDUAL MEMBER
    // ==========================================
    public void exportIndividualMemberToCsv(Uri fileUri, String tripId, String memberName) {
        Toast.makeText(context, "Generating Excel file...", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            try (OutputStream outputStream = context.getContentResolver().openOutputStream(fileUri)) {
                if (outputStream == null) return;

                StringBuilder csv = new StringBuilder();

                // 1. BUILD HEADER ROW
                csv.append("Ledger Report: ").append(memberName).append("\n\n");
                csv.append("Date,Purpose,Debit (Used),Credit (Paid)\n");

                double totalDebit = 0.0;
                double totalCredit = 0.0;

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

                        double debit = 0.0;
                        double credit = 0.0;
                        boolean involvesMember = false;

                        if ("Expense".equals(type)) {
                            double share = (sharedArray.length > 0) ? (amount / sharedArray.length) : 0.0;
                            if (memberName.equals(paidBy)) {
                                credit = amount;
                                involvesMember = true;
                            }
                            if (isParticipant(memberName, sharedArray)) {
                                debit = share;
                                involvesMember = true;
                            }
                        } else if ("Payment".equals(type) && memberName.equals(paidBy)) {
                            credit = amount;
                            involvesMember = true;
                        }

                        // Only write the row if this specific member was involved
                        if (involvesMember) {
                            totalDebit += debit;
                            totalCredit += credit;

                            String safePurpose = (purpose != null) ? purpose.replace(",", " ") : "";
                            String safeDate = (date != null) ? date : "N/A";

                            // Use "-" if the value is 0, just like your UI
                            String debitStr = debit > 0 ? String.format(Locale.US, "%.2f", debit) : "-";
                            String creditStr = credit > 0 ? String.format(Locale.US, "%.2f", credit) : "-";

                            csv.append(safeDate).append(",")
                                    .append(safePurpose).append(",")
                                    .append(debitStr).append(",")
                                    .append(creditStr).append("\n");
                        }
                    }
                }

                // 3. BUILD TOTALS ROW
                csv.append("TOTALS,-,")
                        .append(String.format(Locale.US, "%.2f", totalDebit)).append(",")
                        .append(String.format(Locale.US, "%.2f", totalCredit)).append("\n");

                outputStream.write(csv.toString().getBytes());
                outputStream.flush();

                mainHandler.post(() -> Toast.makeText(context, "Excel Export Successful!", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(context, "Export Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }
    // ==========================================
    // 5. EXPORT TO PDF - PREMIUM INDIVIDUAL LEDGER
    // ==========================================

    // ==========================================
    // 6. EXPORT TO PDF - ALL MEMBERS IN ONE PDF
    // ==========================================
    public void exportAllMembersToSinglePdf(Uri fileUri, String tripId) {
        Toast.makeText(context, "Generating Master PDF...", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            try (OutputStream outputStream = context.getContentResolver().openOutputStream(fileUri)) {
                if (outputStream == null) return;

                // --- 1. FETCH TRIP NAME & ALL MEMBERS (ACTIVE + INACTIVE) ---
                String tripName = "Trip Ledger";
                try (Cursor c = dbHelper.getReadableDatabase().rawQuery(
                        "SELECT " + TripDatabaseHelper.COLUMN_TRIP_NAME + " FROM " + TripDatabaseHelper.TABLE_TRIPS + " WHERE " + TripDatabaseHelper.COLUMN_TRIP_ID + " = ?",
                        new String[]{tripId})) {
                    if (c.moveToFirst()) tripName = c.getString(0);
                }

                ArrayList<String> allMembers = new ArrayList<>();

                // A. Get current active members
                try (Cursor c = dbHelper.getReadableDatabase().rawQuery(
                        "SELECT " + TripDatabaseHelper.COLUMN_MEMBERS + " FROM " + TripDatabaseHelper.TABLE_TRIPS + " WHERE " + TripDatabaseHelper.COLUMN_TRIP_ID + " = ?",
                        new String[]{tripId})) {
                    if (c.moveToFirst()) {
                        String membersStr = c.getString(0);
                        if (membersStr != null && !membersStr.isEmpty()) {
                            for (String m : membersStr.split(",")) {
                                String clean = m.trim();
                                if (!clean.isEmpty() && !allMembers.contains(clean)) allMembers.add(clean);
                            }
                        }
                    }
                }

                // B. Get historical/inactive members who PAID for something
                String queryHist = "SELECT DISTINCT expense_paid_by FROM expenses WHERE expense_trip_id = ? UNION SELECT DISTINCT payment_by FROM payments WHERE payment_trip_id = ?";
                try (Cursor c = dbHelper.getReadableDatabase().rawQuery(queryHist, new String[]{tripId})) {
                    while (c.moveToNext()) {
                        String name = c.getString(0).trim();
                        if (!"Fund".equalsIgnoreCase(name) && !name.isEmpty() && !allMembers.contains(name)) allMembers.add(name);
                    }
                }

                // C. Get historical/inactive members who SHARED an expense
                try (Cursor c = dbHelper.getReadableDatabase().rawQuery("SELECT expense_shared_with FROM expenses WHERE expense_trip_id = ?", new String[]{tripId})) {
                    while (c.moveToNext()) {
                        String sharedStr = c.getString(0);
                        if (sharedStr != null && !sharedStr.isEmpty()) {
                            for (String s : sharedStr.split(",")) {
                                String cleanName = s.trim();
                                if (!cleanName.isEmpty() && !allMembers.contains(cleanName)) allMembers.add(cleanName);
                            }
                        }
                    }
                }

                // Also check trip_members table to ensure we catch inactive members
                try (Cursor c = dbHelper.getReadableDatabase().rawQuery("SELECT DISTINCT member_name FROM " + TripDatabaseHelper.TABLE_TRIP_MEMBERS + " WHERE trip_id = ?", new String[]{tripId})) {
                    while (c.moveToNext()) {
                        String m = c.getString(0).trim();
                        if (!m.isEmpty() && !allMembers.contains(m)) allMembers.add(m);
                    }
                }

                // --- 2. SETUP PDF & PAINTS (A4 Size) ---
                PdfDocument document = new PdfDocument();
                int pageWidth = 595, pageHeight = 842, margin = 40;

                Paint paintMainTitle = new Paint(); paintMainTitle.setTextSize(22f); paintMainTitle.setFakeBoldText(true); paintMainTitle.setTextAlign(Paint.Align.CENTER); paintMainTitle.setColor(Color.parseColor("#85022E"));
                Paint paintSubTitle = new Paint(); paintSubTitle.setTextSize(14f); paintSubTitle.setTextAlign(Paint.Align.CENTER); paintSubTitle.setColor(Color.DKGRAY);
                Paint paintTextBold = new Paint(); paintTextBold.setTextSize(10f); paintTextBold.setFakeBoldText(true);
                Paint paintTextNormal = new Paint(); paintTextNormal.setTextSize(10f);
                Paint paintTextRight = new Paint(); paintTextRight.setTextSize(10f); paintTextRight.setTextAlign(Paint.Align.RIGHT);

                Paint paintGreen = new Paint(); paintGreen.setTextSize(16f); paintGreen.setFakeBoldText(true); paintGreen.setColor(Color.parseColor("#2E7D32")); paintGreen.setTextAlign(Paint.Align.CENTER);
                Paint paintRed = new Paint(); paintRed.setTextSize(16f); paintRed.setFakeBoldText(true); paintRed.setColor(Color.parseColor("#C62828")); paintRed.setTextAlign(Paint.Align.CENTER);
                Paint paintBoxTitle = new Paint(); paintBoxTitle.setTextSize(9f); paintBoxTitle.setFakeBoldText(true); paintBoxTitle.setColor(Color.DKGRAY); paintBoxTitle.setTextAlign(Paint.Align.CENTER);

                Paint paintLine = new Paint(); paintLine.setColor(Color.LTGRAY); paintLine.setStrokeWidth(1f);
                Paint paintDottedLine = new Paint(); paintDottedLine.setColor(Color.LTGRAY); paintDottedLine.setStrokeWidth(1f); paintDottedLine.setPathEffect(new DashPathEffect(new float[]{5, 5}, 0));
                Paint paintTableBg = new Paint(); paintTableBg.setColor(Color.parseColor("#F5F5F5"));
                Paint paintBorder = new Paint(); paintBorder.setStyle(Paint.Style.STROKE); paintBorder.setColor(Color.DKGRAY); paintBorder.setStrokeWidth(1f);

                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.US);
                SimpleDateFormat dateOnly = new SimpleDateFormat("dd MMM yyyy", Locale.US);
                String generatedOn = sdf.format(new Date());
                String reportDate = dateOnly.format(new Date());

                int pageNumber = 1;
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                PdfDocument.Page page = document.startPage(pageInfo);
                Canvas canvas = page.getCanvas();

                // --- 3. LOOP THROUGH EVERY MEMBER ---
                for (int i = 0; i < allMembers.size(); i++) {
                    String memberName = allMembers.get(i);

                    // Force a new page if this isn't the first member!
                    if (i > 0) {
                        drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight);
                        document.finishPage(page);
                        pageNumber++;
                        pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                        page = document.startPage(pageInfo);
                        canvas = page.getCanvas();
                    }

                    double totalDebit = 0.0; double totalCredit = 0.0; int transactionCount = 0;

                    // Calculate totals for THIS member
                    try (Cursor cursor = dbHelper.getUnifiedLedger(tripId)) {
                        while (cursor.moveToNext()) {
                            String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                            double amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount"));
                            String paidBy = cursor.getString(cursor.getColumnIndexOrThrow("paid_by"));
                            String sharedWith = cursor.getString(cursor.getColumnIndexOrThrow("expense_shared_with"));
                            String[] sharedArray = (sharedWith != null && !sharedWith.isEmpty()) ? sharedWith.split(",") : new String[0];

                            if ("Expense".equals(type)) {
                                if (memberName.equals(paidBy)) { totalCredit += amount; transactionCount++; }
                                else if (isParticipant(memberName, sharedArray)) { totalDebit += (amount / sharedArray.length); transactionCount++; }
                            } else if ("Payment".equals(type) && memberName.equals(paidBy)) {
                                totalCredit += amount; transactionCount++;
                            }
                        }
                    }
                    double finalBalance = totalCredit - totalDebit;

                    // Draw Header & Summary Cards
                    int yPos = drawPageHeader(canvas, pageWidth, margin, tripName, memberName, generatedOn, reportDate, paintMainTitle, paintSubTitle, paintTextBold, paintTextNormal, paintTextRight);
                    int cardTop = yPos;
                    int cardBottom = yPos + 70;
                    canvas.drawRoundRect(new RectF(margin, cardTop, pageWidth - margin, cardBottom), 5, 5, paintBorder);

                    float colWidth = (pageWidth - (margin * 2)) / 4f;
                    for (int div = 1; div <= 3; div++) {
                        canvas.drawLine(margin + (colWidth * div), cardTop + 10, margin + (colWidth * div), cardBottom - 10, paintLine);
                    }

                    float center1 = margin + (colWidth * 0.5f); float center2 = margin + (colWidth * 1.5f);
                    float center3 = margin + (colWidth * 2.5f); float center4 = margin + (colWidth * 3.5f);

                    canvas.drawText("TOTAL PAID", center1, cardTop + 20, paintBoxTitle);
                    canvas.drawText(String.format(Locale.US, "₹%,.2f", totalCredit), center1, cardTop + 45, paintGreen);
                    canvas.drawText("TOTAL EXPENSE", center2, cardTop + 20, paintBoxTitle);
                    canvas.drawText(String.format(Locale.US, "₹%,.2f", totalDebit), center2, cardTop + 45, paintRed);
                    canvas.drawText("CURRENT BALANCE", center3, cardTop + 20, paintBoxTitle);
                    canvas.drawText(String.format(Locale.US, "₹%,.2f", Math.abs(finalBalance)), center3, cardTop + 40, finalBalance >= 0 ? paintGreen : paintRed);
                    canvas.drawText(finalBalance >= 0 ? "(CR - Refundable)" : "(DR - Payable)", center3, cardTop + 55, paintBoxTitle);
                    canvas.drawText("TRANSACTIONS", center4, cardTop + 20, paintBoxTitle);
                    canvas.drawText(String.valueOf(transactionCount), center4, cardTop + 45, paintMainTitle);

                    // Draw Table Header
                    yPos = cardBottom + 30;
                    int xDate = margin + 5, xDesc = margin + 110, xDebit = pageWidth - margin - 150, xCredit = pageWidth - margin - 15;

                    canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintTableBg);
                    canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintBorder);
                    canvas.drawText("Date", xDate, yPos + 17, paintTextBold);
                    canvas.drawText("Description", xDesc, yPos + 17, paintTextBold);
                    canvas.drawText("Debit (₹)", xDebit, yPos + 17, paintTextRight);
                    canvas.drawText("Credit (₹)", xCredit, yPos + 17, paintTextRight);
                    yPos += 25;

                    // Draw Table Rows
                    try (Cursor cursor = dbHelper.getUnifiedLedger(tripId)) {
                        while (cursor.moveToNext()) {
                            String date = cursor.getString(cursor.getColumnIndexOrThrow("date"));
                            String purpose = cursor.getString(cursor.getColumnIndexOrThrow("purpose"));
                            double amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount"));
                            String paidBy = cursor.getString(cursor.getColumnIndexOrThrow("paid_by"));
                            String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                            String sharedWith = cursor.getString(cursor.getColumnIndexOrThrow("expense_shared_with"));
                            String[] sharedArray = (sharedWith != null && !sharedWith.isEmpty()) ? sharedWith.split(",") : new String[0];

                            double debit = 0.0, credit = 0.0; boolean involvesMember = false;

                            if ("Expense".equals(type)) {
                                if (memberName.equals(paidBy)) { credit = amount; involvesMember = true; }
                                if (isParticipant(memberName, sharedArray)) { debit = amount / sharedArray.length; involvesMember = true; }
                            } else if ("Payment".equals(type) && memberName.equals(paidBy)) {
                                credit = amount; involvesMember = true;
                            }

                            if (involvesMember) {
                                // Pagination Check mid-table
                                if (yPos > pageHeight - 120) {
                                    drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight);
                                    document.finishPage(page);
                                    pageNumber++;
                                    pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                                    page = document.startPage(pageInfo);
                                    canvas = page.getCanvas();
                                    yPos = drawPageHeader(canvas, pageWidth, margin, tripName, memberName, generatedOn, reportDate, paintMainTitle, paintSubTitle, paintTextBold, paintTextNormal, paintTextRight);

                                    canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintTableBg);
                                    canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintBorder);
                                    canvas.drawText("Date", xDate, yPos + 17, paintTextBold); canvas.drawText("Description", xDesc, yPos + 17, paintTextBold);
                                    canvas.drawText("Debit (₹)", xDebit, yPos + 17, paintTextRight); canvas.drawText("Credit (₹)", xCredit, yPos + 17, paintTextRight);
                                    yPos += 25;
                                }

                                String safeDate = (date != null) ? date : "N/A";
                                String safePurpose = (purpose != null) ? purpose : "";
                                if (safePurpose.length() > 40) safePurpose = safePurpose.substring(0, 37) + "...";

                                String debitStr = debit > 0 ? String.format(Locale.US, "%,.2f", debit) : "";
                                String creditStr = credit > 0 ? String.format(Locale.US, "%,.2f", credit) : "";

                                yPos += 20;
                                canvas.drawText(safeDate, xDate, yPos, paintTextNormal); canvas.drawText(safePurpose, xDesc, yPos, paintTextNormal);
                                canvas.drawText(debitStr, xDebit, yPos, paintTextRight); canvas.drawText(creditStr, xCredit, yPos, paintTextRight);
                                yPos += 10;
                                canvas.drawLine(margin, yPos, pageWidth - margin, yPos, paintDottedLine);
                            }
                        }
                    }

                    // Draw Totals Row
                    yPos += 5;
                    canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintBorder);
                    canvas.drawText("TOTALS", xDesc, yPos + 17, paintTextBold);
                    canvas.drawText(String.format(Locale.US, "₹%,.2f", totalDebit), xDebit, yPos + 17, paintTextRight);
                    canvas.drawText(String.format(Locale.US, "₹%,.2f", totalCredit), xCredit, yPos + 17, paintTextRight);

                    // Draw Remarks Box
                    yPos += 45;
                    if (yPos > pageHeight - 140) {
                        drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight);
                        document.finishPage(page);
                        pageNumber++;
                        pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                        page = document.startPage(pageInfo);
                        canvas = page.getCanvas();
                        yPos = drawPageHeader(canvas, pageWidth, margin, tripName, memberName, generatedOn, reportDate, paintMainTitle, paintSubTitle, paintTextBold, paintTextNormal, paintTextRight);
                    }

                    canvas.drawRoundRect(new RectF(margin, yPos, pageWidth - margin, yPos + 55), 5, 5, paintBorder);
                    canvas.drawText("Remarks :", margin + 15, yPos + 20, paintTextBold);
                    canvas.drawText("• Positive balance (Green) means refundable to the member.", margin + 25, yPos + 35, paintTextNormal);
                    canvas.drawText("• Negative balance (Red) means amount payable by the member.", margin + 25, yPos + 48, paintTextNormal);
                }

                // --- 4. END OF MASTER PDF MARKER ---
                int finalYPos = page.getCanvas().getHeight() - margin - 60; // Print just above the final footer
                Paint endPaint = new Paint(); endPaint.setTextSize(11f); endPaint.setFakeBoldText(true); endPaint.setColor(Color.parseColor("#85022E")); endPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("End of the master pdf. Total pages: " + pageNumber, pageWidth / 2f, finalYPos, endPaint);

                // Draw final footer and close document
                drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight);
                document.finishPage(page);
                document.writeTo(outputStream);
                document.close();

                mainHandler.post(() -> Toast.makeText(context, "Master PDF Export Successful!", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(context, "PDF Export Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    // ==========================================
    // 7. SHARE PDF - INDIVIDUAL MEMBER (WHATSAPP/GMAIL)
    // ==========================================
    // ==========================================
    // 7. SHARE PDF - INDIVIDUAL MEMBER (WHATSAPP/GMAIL)
    // ==========================================
    public void shareIndividualMemberPdf(String tripId, String memberName) {
        Toast.makeText(context, "Preparing PDF for sharing...", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            try {
                // --- A. CREATE TEMPORARY CACHE FILE ---
                java.io.File pdfFolder = new java.io.File(context.getCacheDir(), "pdfs");
                if (!pdfFolder.exists() && !pdfFolder.mkdirs()) {
                    mainHandler.post(() -> Toast.makeText(context, "Failed to create cache folder", Toast.LENGTH_SHORT).show());
                    return; // Stop the process safely
                }

                String safeName = memberName.replaceAll("[^a-zA-Z0-9]", "_");
                java.io.File pdfFile = new java.io.File(pdfFolder, safeName + "_Ledger.pdf");

                // --- B. DRAW THE PDF TO THE FILE ---
                try (java.io.FileOutputStream outputStream = new java.io.FileOutputStream(pdfFile)) {

                    String tripName = "Trip Ledger";
                    String query = "SELECT " + TripDatabaseHelper.COLUMN_TRIP_NAME + " FROM " + TripDatabaseHelper.TABLE_TRIPS + " WHERE " + TripDatabaseHelper.COLUMN_TRIP_ID + " = ?";
                    try (Cursor c = dbHelper.getReadableDatabase().rawQuery(query, new String[]{tripId})) {
                        if (c.moveToFirst()) tripName = c.getString(0);
                    }

                    double totalDebit = 0.0; double totalCredit = 0.0; int transactionCount = 0;
                    try (Cursor cursor = dbHelper.getUnifiedLedger(tripId)) {
                        while (cursor.moveToNext()) {
                            String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                            double amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount"));
                            String paidBy = cursor.getString(cursor.getColumnIndexOrThrow("paid_by"));
                            String sharedWith = cursor.getString(cursor.getColumnIndexOrThrow("expense_shared_with"));
                            String[] sharedArray = (sharedWith != null && !sharedWith.isEmpty()) ? sharedWith.split(",") : new String[0];

                            if ("Expense".equals(type)) {
                                if (memberName.equals(paidBy)) { totalCredit += amount; transactionCount++; }
                                else if (isParticipant(memberName, sharedArray)) { totalDebit += (amount / sharedArray.length); transactionCount++; }
                            } else if ("Payment".equals(type) && memberName.equals(paidBy)) {
                                totalCredit += amount; transactionCount++;
                            }
                        }
                    }
                    double finalBalance = totalCredit - totalDebit;

                    PdfDocument document = new PdfDocument();
                    int pageWidth = 595, pageHeight = 842, margin = 40;

                    Paint paintMainTitle = new Paint(); paintMainTitle.setTextSize(22f); paintMainTitle.setFakeBoldText(true); paintMainTitle.setTextAlign(Paint.Align.CENTER); paintMainTitle.setColor(Color.parseColor("#85022E"));
                    Paint paintSubTitle = new Paint(); paintSubTitle.setTextSize(14f); paintSubTitle.setTextAlign(Paint.Align.CENTER); paintSubTitle.setColor(Color.DKGRAY);
                    Paint paintTextBold = new Paint(); paintTextBold.setTextSize(10f); paintTextBold.setFakeBoldText(true);
                    Paint paintTextNormal = new Paint(); paintTextNormal.setTextSize(10f);
                    Paint paintTextRight = new Paint(); paintTextRight.setTextSize(10f); paintTextRight.setTextAlign(Paint.Align.RIGHT);

                    Paint paintGreen = new Paint(); paintGreen.setTextSize(16f); paintGreen.setFakeBoldText(true); paintGreen.setColor(Color.parseColor("#2E7D32")); paintGreen.setTextAlign(Paint.Align.CENTER);
                    Paint paintRed = new Paint(); paintRed.setTextSize(16f); paintRed.setFakeBoldText(true); paintRed.setColor(Color.parseColor("#C62828")); paintRed.setTextAlign(Paint.Align.CENTER);
                    Paint paintBoxTitle = new Paint(); paintBoxTitle.setTextSize(9f); paintBoxTitle.setFakeBoldText(true); paintBoxTitle.setColor(Color.DKGRAY); paintBoxTitle.setTextAlign(Paint.Align.CENTER);

                    Paint paintLine = new Paint(); paintLine.setColor(Color.LTGRAY); paintLine.setStrokeWidth(1f);
                    Paint paintDottedLine = new Paint(); paintDottedLine.setColor(Color.LTGRAY); paintDottedLine.setStrokeWidth(1f); paintDottedLine.setPathEffect(new android.graphics.DashPathEffect(new float[]{5, 5}, 0));
                    Paint paintTableBg = new Paint(); paintTableBg.setColor(Color.parseColor("#F5F5F5"));
                    Paint paintBorder = new Paint(); paintBorder.setStyle(Paint.Style.STROKE); paintBorder.setColor(Color.DKGRAY); paintBorder.setStrokeWidth(1f);

                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.US);
                    java.text.SimpleDateFormat dateOnly = new java.text.SimpleDateFormat("dd MMM yyyy", Locale.US);
                    String generatedOn = sdf.format(new java.util.Date());
                    String reportDate = dateOnly.format(new java.util.Date());

                    int pageNumber = 1;
                    PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                    PdfDocument.Page page = document.startPage(pageInfo);
                    Canvas canvas = page.getCanvas();

                    int yPos = drawPageHeader(canvas, pageWidth, margin, tripName, memberName, generatedOn, reportDate, paintMainTitle, paintSubTitle, paintTextBold, paintTextNormal, paintTextRight);
                    int cardTop = yPos; int cardBottom = yPos + 70;
                    canvas.drawRoundRect(new android.graphics.RectF(margin, cardTop, pageWidth - margin, cardBottom), 5, 5, paintBorder);

                    float colWidth = (pageWidth - (margin * 2)) / 4f;
                    for (int i = 1; i <= 3; i++) canvas.drawLine(margin + (colWidth * i), cardTop + 10, margin + (colWidth * i), cardBottom - 10, paintLine);

                    float center1 = margin + (colWidth * 0.5f); float center2 = margin + (colWidth * 1.5f);
                    float center3 = margin + (colWidth * 2.5f); float center4 = margin + (colWidth * 3.5f);

                    canvas.drawText("TOTAL PAID", center1, cardTop + 20, paintBoxTitle); canvas.drawText(String.format(Locale.US, "₹%,.2f", totalCredit), center1, cardTop + 45, paintGreen);
                    canvas.drawText("TOTAL EXPENSE", center2, cardTop + 20, paintBoxTitle); canvas.drawText(String.format(Locale.US, "₹%,.2f", totalDebit), center2, cardTop + 45, paintRed);
                    canvas.drawText("CURRENT BALANCE", center3, cardTop + 20, paintBoxTitle); canvas.drawText(String.format(Locale.US, "₹%,.2f", Math.abs(finalBalance)), center3, cardTop + 40, finalBalance >= 0 ? paintGreen : paintRed);
                    canvas.drawText(finalBalance >= 0 ? "(CR - Refundable)" : "(DR - Payable)", center3, cardTop + 55, paintBoxTitle);
                    canvas.drawText("TRANSACTIONS", center4, cardTop + 20, paintBoxTitle); canvas.drawText(String.valueOf(transactionCount), center4, cardTop + 45, paintMainTitle);

                    yPos = cardBottom + 30;
                    int xDate = margin + 5, xDesc = margin + 110, xDebit = pageWidth - margin - 150, xCredit = pageWidth - margin - 15;
                    canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintTableBg); canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintBorder);
                    canvas.drawText("Date", xDate, yPos + 17, paintTextBold); canvas.drawText("Description", xDesc, yPos + 17, paintTextBold);
                    canvas.drawText("Debit (₹)", xDebit, yPos + 17, paintTextRight); canvas.drawText("Credit (₹)", xCredit, yPos + 17, paintTextRight);
                    yPos += 25;

                    try (Cursor cursor = dbHelper.getUnifiedLedger(tripId)) {
                        while (cursor.moveToNext()) {
                            String date = cursor.getString(cursor.getColumnIndexOrThrow("date"));
                            String purpose = cursor.getString(cursor.getColumnIndexOrThrow("purpose"));
                            double amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount"));
                            String paidBy = cursor.getString(cursor.getColumnIndexOrThrow("paid_by"));
                            String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                            String sharedWith = cursor.getString(cursor.getColumnIndexOrThrow("expense_shared_with"));
                            String[] sharedArray = (sharedWith != null && !sharedWith.isEmpty()) ? sharedWith.split(",") : new String[0];

                            double debit = 0.0, credit = 0.0; boolean involvesMember = false;
                            if ("Expense".equals(type)) {
                                if (memberName.equals(paidBy)) { credit = amount; involvesMember = true; }
                                if (isParticipant(memberName, sharedArray)) { debit = amount / sharedArray.length; involvesMember = true; }
                            } else if ("Payment".equals(type) && memberName.equals(paidBy)) { credit = amount; involvesMember = true; }

                            if (involvesMember) {
                                if (yPos > pageHeight - 120) {
                                    drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight);
                                    document.finishPage(page);
                                    pageNumber++; pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                                    page = document.startPage(pageInfo); canvas = page.getCanvas();
                                    yPos = drawPageHeader(canvas, pageWidth, margin, tripName, memberName, generatedOn, reportDate, paintMainTitle, paintSubTitle, paintTextBold, paintTextNormal, paintTextRight);
                                    canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintTableBg); canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintBorder);
                                    canvas.drawText("Date", xDate, yPos + 17, paintTextBold); canvas.drawText("Description", xDesc, yPos + 17, paintTextBold);
                                    canvas.drawText("Debit (₹)", xDebit, yPos + 17, paintTextRight); canvas.drawText("Credit (₹)", xCredit, yPos + 17, paintTextRight);
                                    yPos += 25;
                                }
                                String safeDate = (date != null) ? date : "N/A";
                                String safePurpose = (purpose != null) ? purpose : ""; if (safePurpose.length() > 40) safePurpose = safePurpose.substring(0, 37) + "...";
                                String debitStr = debit > 0 ? String.format(Locale.US, "%,.2f", debit) : ""; String creditStr = credit > 0 ? String.format(Locale.US, "%,.2f", credit) : "";
                                yPos += 20; canvas.drawText(safeDate, xDate, yPos, paintTextNormal); canvas.drawText(safePurpose, xDesc, yPos, paintTextNormal);
                                canvas.drawText(debitStr, xDebit, yPos, paintTextRight); canvas.drawText(creditStr, xCredit, yPos, paintTextRight);
                                yPos += 10; canvas.drawLine(margin, yPos, pageWidth - margin, yPos, paintDottedLine);
                            }
                        }
                    }
                    yPos += 5; canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintBorder);
                    canvas.drawText("TOTALS", xDesc, yPos + 17, paintTextBold); canvas.drawText(String.format(Locale.US, "₹%,.2f", totalDebit), xDebit, yPos + 17, paintTextRight); canvas.drawText(String.format(Locale.US, "₹%,.2f", totalCredit), xCredit, yPos + 17, paintTextRight);
                    yPos += 45;
                    if (yPos > pageHeight - 140) {
                        drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight); document.finishPage(page);
                        pageNumber++; pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                        page = document.startPage(pageInfo); canvas = page.getCanvas();
                        yPos = drawPageHeader(canvas, pageWidth, margin, tripName, memberName, generatedOn, reportDate, paintMainTitle, paintSubTitle, paintTextBold, paintTextNormal, paintTextRight);
                    }
                    canvas.drawRoundRect(new android.graphics.RectF(margin, yPos, pageWidth - margin, yPos + 55), 5, 5, paintBorder);
                    canvas.drawText("Remarks :", margin + 15, yPos + 20, paintTextBold);
                    canvas.drawText("• Positive balance (Green) means refundable to the member.", margin + 25, yPos + 35, paintTextNormal);
                    canvas.drawText("• Negative balance (Red) means amount payable by the member.", margin + 25, yPos + 48, paintTextNormal);

                    yPos += 85;
                    Paint endPaint = new Paint(); endPaint.setTextSize(11f); endPaint.setFakeBoldText(true); endPaint.setColor(Color.parseColor("#85022E")); endPaint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText("End of the pdf. Total page: " + pageNumber + " page", pageWidth / 2f, yPos, endPaint);

                    drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight);
                    document.finishPage(page);
                    document.writeTo(outputStream);
                    document.close();
                }

                // --- C. LAUNCH THE SHARE SHEET USING FILEPROVIDER ---
                mainHandler.post(() -> {
                    try {
                        android.net.Uri pdfUri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                context.getApplicationContext().getPackageName() + ".fileprovider",
                                pdfFile);

                        android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                        shareIntent.setType("application/pdf");
                        shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, pdfUri);
                        shareIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Ledger PDF"));
                    } catch (Exception e) {
                        Toast.makeText(context, "Error opening Share menu: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(context, "Share Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }
    // ==========================================
    // 8. SHARE PDF - MASTER (ALL MEMBERS)
    // ==========================================
    public void shareMasterPdf(String tripId) {
        Toast.makeText(context, "Preparing Master PDF for sharing...", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            try {
                // --- A. CREATE TEMPORARY CACHE FILE ---
                java.io.File pdfFolder = new java.io.File(context.getCacheDir(), "pdfs");
                if (!pdfFolder.exists() && !pdfFolder.mkdirs()) {
                    mainHandler.post(() -> Toast.makeText(context, "Failed to create cache folder", Toast.LENGTH_SHORT).show());
                    return;
                }

                String tripName = "Trip Ledger";
                String query = "SELECT " + TripDatabaseHelper.COLUMN_TRIP_NAME + " FROM " + TripDatabaseHelper.TABLE_TRIPS + " WHERE " + TripDatabaseHelper.COLUMN_TRIP_ID + " = ?";
                try (Cursor c = dbHelper.getReadableDatabase().rawQuery(query, new String[]{tripId})) {
                    if (c.moveToFirst()) tripName = c.getString(0);
                }

                String safeName = tripName.replaceAll("[^a-zA-Z0-9]", "_");
                java.io.File pdfFile = new java.io.File(pdfFolder, safeName + "_Master_Ledger.pdf");

                // --- B. DRAW THE MASTER PDF TO CACHE ---
                try (java.io.FileOutputStream outputStream = new java.io.FileOutputStream(pdfFile)) {

                    // 1. Fetch All Members (Active + Inactive)
                    ArrayList<String> allMembers = new ArrayList<>();
                    try (Cursor c = dbHelper.getReadableDatabase().rawQuery("SELECT " + TripDatabaseHelper.COLUMN_MEMBERS + " FROM " + TripDatabaseHelper.TABLE_TRIPS + " WHERE " + TripDatabaseHelper.COLUMN_TRIP_ID + " = ?", new String[]{tripId})) {
                        if (c.moveToFirst()) {
                            String membersStr = c.getString(0);
                            if (membersStr != null && !membersStr.isEmpty()) {
                                for (String m : membersStr.split(",")) {
                                    String clean = m.trim();
                                    if (!clean.isEmpty() && !allMembers.contains(clean)) allMembers.add(clean);
                                }
                            }
                        }
                    }
                    String queryHist = "SELECT DISTINCT expense_paid_by FROM expenses WHERE expense_trip_id = ? UNION SELECT DISTINCT payment_by FROM payments WHERE payment_trip_id = ?";
                    try (Cursor c = dbHelper.getReadableDatabase().rawQuery(queryHist, new String[]{tripId})) {
                        while (c.moveToNext()) {
                            String name = c.getString(0).trim();
                            if (!"Fund".equalsIgnoreCase(name) && !name.isEmpty() && !allMembers.contains(name)) allMembers.add(name);
                        }
                    }
                    try (Cursor c = dbHelper.getReadableDatabase().rawQuery("SELECT expense_shared_with FROM expenses WHERE expense_trip_id = ?", new String[]{tripId})) {
                        while (c.moveToNext()) {
                            String sharedStr = c.getString(0);
                            if (sharedStr != null && !sharedStr.isEmpty()) {
                                for (String s : sharedStr.split(",")) {
                                    String cleanName = s.trim();
                                    if (!cleanName.isEmpty() && !allMembers.contains(cleanName)) allMembers.add(cleanName);
                                }
                            }
                        }
                    }

                    // 2. Setup PDF & Paints
                    android.graphics.pdf.PdfDocument document = new android.graphics.pdf.PdfDocument();
                    int pageWidth = 595, pageHeight = 842, margin = 40;

                    Paint paintMainTitle = new Paint(); paintMainTitle.setTextSize(22f); paintMainTitle.setFakeBoldText(true); paintMainTitle.setTextAlign(Paint.Align.CENTER); paintMainTitle.setColor(Color.parseColor("#85022E"));
                    Paint paintSubTitle = new Paint(); paintSubTitle.setTextSize(14f); paintSubTitle.setTextAlign(Paint.Align.CENTER); paintSubTitle.setColor(Color.DKGRAY);
                    Paint paintTextBold = new Paint(); paintTextBold.setTextSize(10f); paintTextBold.setFakeBoldText(true);
                    Paint paintTextNormal = new Paint(); paintTextNormal.setTextSize(10f);
                    Paint paintTextRight = new Paint(); paintTextRight.setTextSize(10f); paintTextRight.setTextAlign(Paint.Align.RIGHT);
                    Paint paintGreen = new Paint(); paintGreen.setTextSize(16f); paintGreen.setFakeBoldText(true); paintGreen.setColor(Color.parseColor("#2E7D32")); paintGreen.setTextAlign(Paint.Align.CENTER);
                    Paint paintRed = new Paint(); paintRed.setTextSize(16f); paintRed.setFakeBoldText(true); paintRed.setColor(Color.parseColor("#C62828")); paintRed.setTextAlign(Paint.Align.CENTER);
                    Paint paintBoxTitle = new Paint(); paintBoxTitle.setTextSize(9f); paintBoxTitle.setFakeBoldText(true); paintBoxTitle.setColor(Color.DKGRAY); paintBoxTitle.setTextAlign(Paint.Align.CENTER);
                    Paint paintLine = new Paint(); paintLine.setColor(Color.LTGRAY); paintLine.setStrokeWidth(1f);
                    Paint paintDottedLine = new Paint(); paintDottedLine.setColor(Color.LTGRAY); paintDottedLine.setStrokeWidth(1f); paintDottedLine.setPathEffect(new android.graphics.DashPathEffect(new float[]{5, 5}, 0));
                    Paint paintTableBg = new Paint(); paintTableBg.setColor(Color.parseColor("#F5F5F5"));
                    Paint paintBorder = new Paint(); paintBorder.setStyle(Paint.Style.STROKE); paintBorder.setColor(Color.DKGRAY); paintBorder.setStrokeWidth(1f);

                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.US);
                    java.text.SimpleDateFormat dateOnly = new java.text.SimpleDateFormat("dd MMM yyyy", Locale.US);
                    String generatedOn = sdf.format(new java.util.Date());
                    String reportDate = dateOnly.format(new java.util.Date());

                    int pageNumber = 1;
                    android.graphics.pdf.PdfDocument.PageInfo pageInfo = new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                    android.graphics.pdf.PdfDocument.Page page = document.startPage(pageInfo);
                    Canvas canvas = page.getCanvas();

                    // 3. Loop Members
                    for (int i = 0; i < allMembers.size(); i++) {
                        String memberName = allMembers.get(i);
                        if (i > 0) {
                            drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight);
                            document.finishPage(page);
                            pageNumber++;
                            pageInfo = new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                            page = document.startPage(pageInfo);
                            canvas = page.getCanvas();
                        }

                        double totalDebit = 0.0; double totalCredit = 0.0; int transactionCount = 0;
                        try (Cursor cursor = dbHelper.getUnifiedLedger(tripId)) {
                            while (cursor.moveToNext()) {
                                String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                                double amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount"));
                                String paidBy = cursor.getString(cursor.getColumnIndexOrThrow("paid_by"));
                                String sharedWith = cursor.getString(cursor.getColumnIndexOrThrow("expense_shared_with"));
                                String[] sharedArray = (sharedWith != null && !sharedWith.isEmpty()) ? sharedWith.split(",") : new String[0];

                                if ("Expense".equals(type)) {
                                    if (memberName.equals(paidBy)) { totalCredit += amount; transactionCount++; }
                                    else if (isParticipant(memberName, sharedArray)) { totalDebit += (amount / sharedArray.length); transactionCount++; }
                                } else if ("Payment".equals(type) && memberName.equals(paidBy)) {
                                    totalCredit += amount; transactionCount++;
                                }
                            }
                        }
                        double finalBalance = totalCredit - totalDebit;

                        int yPos = drawPageHeader(canvas, pageWidth, margin, tripName, memberName, generatedOn, reportDate, paintMainTitle, paintSubTitle, paintTextBold, paintTextNormal, paintTextRight);
                        int cardTop = yPos; int cardBottom = yPos + 70;
                        canvas.drawRoundRect(new android.graphics.RectF(margin, cardTop, pageWidth - margin, cardBottom), 5, 5, paintBorder);

                        float colWidth = (pageWidth - (margin * 2)) / 4f;
                        for (int div = 1; div <= 3; div++) canvas.drawLine(margin + (colWidth * div), cardTop + 10, margin + (colWidth * div), cardBottom - 10, paintLine);

                        float center1 = margin + (colWidth * 0.5f); float center2 = margin + (colWidth * 1.5f);
                        float center3 = margin + (colWidth * 2.5f); float center4 = margin + (colWidth * 3.5f);

                        canvas.drawText("TOTAL PAID", center1, cardTop + 20, paintBoxTitle); canvas.drawText(String.format(Locale.US, "₹%,.2f", totalCredit), center1, cardTop + 45, paintGreen);
                        canvas.drawText("TOTAL EXPENSE", center2, cardTop + 20, paintBoxTitle); canvas.drawText(String.format(Locale.US, "₹%,.2f", totalDebit), center2, cardTop + 45, paintRed);
                        canvas.drawText("CURRENT BALANCE", center3, cardTop + 20, paintBoxTitle); canvas.drawText(String.format(Locale.US, "₹%,.2f", Math.abs(finalBalance)), center3, cardTop + 40, finalBalance >= 0 ? paintGreen : paintRed);
                        canvas.drawText(finalBalance >= 0 ? "(CR - Refundable)" : "(DR - Payable)", center3, cardTop + 55, paintBoxTitle);
                        canvas.drawText("TRANSACTIONS", center4, cardTop + 20, paintBoxTitle); canvas.drawText(String.valueOf(transactionCount), center4, cardTop + 45, paintMainTitle);

                        yPos = cardBottom + 30;
                        int xDate = margin + 5, xDesc = margin + 110, xDebit = pageWidth - margin - 150, xCredit = pageWidth - margin - 15;
                        canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintTableBg); canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintBorder);
                        canvas.drawText("Date", xDate, yPos + 17, paintTextBold); canvas.drawText("Description", xDesc, yPos + 17, paintTextBold);
                        canvas.drawText("Debit (₹)", xDebit, yPos + 17, paintTextRight); canvas.drawText("Credit (₹)", xCredit, yPos + 17, paintTextRight);
                        yPos += 25;

                        try (Cursor cursor = dbHelper.getUnifiedLedger(tripId)) {
                            while (cursor.moveToNext()) {
                                String date = cursor.getString(cursor.getColumnIndexOrThrow("date"));
                                String purpose = cursor.getString(cursor.getColumnIndexOrThrow("purpose"));
                                double amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount"));
                                String paidBy = cursor.getString(cursor.getColumnIndexOrThrow("paid_by"));
                                String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                                String sharedWith = cursor.getString(cursor.getColumnIndexOrThrow("expense_shared_with"));
                                String[] sharedArray = (sharedWith != null && !sharedWith.isEmpty()) ? sharedWith.split(",") : new String[0];

                                double debit = 0.0, credit = 0.0; boolean involvesMember = false;
                                if ("Expense".equals(type)) {
                                    if (memberName.equals(paidBy)) { credit = amount; involvesMember = true; }
                                    if (isParticipant(memberName, sharedArray)) { debit = amount / sharedArray.length; involvesMember = true; }
                                } else if ("Payment".equals(type) && memberName.equals(paidBy)) { credit = amount; involvesMember = true; }

                                if (involvesMember) {
                                    if (yPos > pageHeight - 120) {
                                        drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight);
                                        document.finishPage(page);
                                        pageNumber++;
                                        pageInfo = new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                                        page = document.startPage(pageInfo); canvas = page.getCanvas();
                                        yPos = drawPageHeader(canvas, pageWidth, margin, tripName, memberName, generatedOn, reportDate, paintMainTitle, paintSubTitle, paintTextBold, paintTextNormal, paintTextRight);
                                        canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintTableBg); canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintBorder);
                                        canvas.drawText("Date", xDate, yPos + 17, paintTextBold); canvas.drawText("Description", xDesc, yPos + 17, paintTextBold);
                                        canvas.drawText("Debit (₹)", xDebit, yPos + 17, paintTextRight); canvas.drawText("Credit (₹)", xCredit, yPos + 17, paintTextRight);
                                        yPos += 25;
                                    }
                                    String safeDate = (date != null) ? date : "N/A";
                                    String safePurpose = (purpose != null) ? purpose : ""; if (safePurpose.length() > 40) safePurpose = safePurpose.substring(0, 37) + "...";
                                    String debitStr = debit > 0 ? String.format(Locale.US, "%,.2f", debit) : ""; String creditStr = credit > 0 ? String.format(Locale.US, "%,.2f", credit) : "";
                                    yPos += 20; canvas.drawText(safeDate, xDate, yPos, paintTextNormal); canvas.drawText(safePurpose, xDesc, yPos, paintTextNormal);
                                    canvas.drawText(debitStr, xDebit, yPos, paintTextRight); canvas.drawText(creditStr, xCredit, yPos, paintTextRight);
                                    yPos += 10; canvas.drawLine(margin, yPos, pageWidth - margin, yPos, paintDottedLine);
                                }
                            }
                        }
                        yPos += 5; canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintBorder);
                        canvas.drawText("TOTALS", xDesc, yPos + 17, paintTextBold); canvas.drawText(String.format(Locale.US, "₹%,.2f", totalDebit), xDebit, yPos + 17, paintTextRight); canvas.drawText(String.format(Locale.US, "₹%,.2f", totalCredit), xCredit, yPos + 17, paintTextRight);

                        yPos += 45;
                        if (yPos > pageHeight - 140) {
                            drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight); document.finishPage(page);
                            pageNumber++;
                            pageInfo = new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                            page = document.startPage(pageInfo); canvas = page.getCanvas();
                            yPos = drawPageHeader(canvas, pageWidth, margin, tripName, memberName, generatedOn, reportDate, paintMainTitle, paintSubTitle, paintTextBold, paintTextNormal, paintTextRight);
                        }
                        canvas.drawRoundRect(new android.graphics.RectF(margin, yPos, pageWidth - margin, yPos + 55), 5, 5, paintBorder);
                        canvas.drawText("Remarks :", margin + 15, yPos + 20, paintTextBold);
                        canvas.drawText("• Positive balance (Green) means refundable to the member.", margin + 25, yPos + 35, paintTextNormal);
                        canvas.drawText("• Negative balance (Red) means amount payable by the member.", margin + 25, yPos + 48, paintTextNormal);
                    }

                    int finalYPos = page.getCanvas().getHeight() - margin - 60;
                    Paint endPaint = new Paint(); endPaint.setTextSize(11f); endPaint.setFakeBoldText(true); endPaint.setColor(Color.parseColor("#85022E")); endPaint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText("End of the master pdf. Total pages: " + pageNumber, pageWidth / 2f, finalYPos, endPaint);

                    drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight);
                    document.finishPage(page);
                    document.writeTo(outputStream);
                    document.close();
                }

                // --- C. LAUNCH THE SHARE SHEET USING FILEPROVIDER ---
                mainHandler.post(() -> {
                    try {
                        android.net.Uri pdfUri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                context.getApplicationContext().getPackageName() + ".fileprovider",
                                pdfFile);

                        android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                        shareIntent.setType("application/pdf");
                        shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, pdfUri);
                        shareIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Master Ledger PDF"));
                    } catch (Exception e) {
                        Toast.makeText(context, "Error opening Share menu: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(context, "Share Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }
    public void exportIndividualMemberToPdf(Uri fileUri, String tripId, String memberName) {
        Toast.makeText(context, "Generating Premium PDF...", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            try (OutputStream outputStream = context.getContentResolver().openOutputStream(fileUri)) {
                if (outputStream == null) return;

                // --- 1. PRE-FETCH TRIP DATA & CALCULATE TOTALS ---
                String tripName = "Trip Ledger";
                String query = "SELECT " + TripDatabaseHelper.COLUMN_TRIP_NAME +
                        " FROM " + TripDatabaseHelper.TABLE_TRIPS +
                        " WHERE " + TripDatabaseHelper.COLUMN_TRIP_ID + " = ?";
                try (Cursor c = dbHelper.getReadableDatabase().rawQuery(query, new String[]{tripId})) {
                    if (c.moveToFirst()) tripName = c.getString(0);
                }

                double totalDebit = 0.0;
                double totalCredit = 0.0;
                int transactionCount = 0;

                try (Cursor cursor = dbHelper.getUnifiedLedger(tripId)) {
                    while (cursor.moveToNext()) {
                        String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                        double amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount"));
                        String paidBy = cursor.getString(cursor.getColumnIndexOrThrow("paid_by"));
                        String sharedWith = cursor.getString(cursor.getColumnIndexOrThrow("expense_shared_with"));
                        String[] sharedArray = (sharedWith != null && !sharedWith.isEmpty()) ? sharedWith.split(",") : new String[0];

                        if ("Expense".equals(type)) {
                            if (memberName.equals(paidBy)) { totalCredit += amount; transactionCount++; }
                            else if (isParticipant(memberName, sharedArray)) { totalDebit += (amount / sharedArray.length); transactionCount++; }
                        } else if ("Payment".equals(type) && memberName.equals(paidBy)) {
                            totalCredit += amount; transactionCount++;
                        }
                    }
                }
                double finalBalance = totalCredit - totalDebit;

                // --- 2. SETUP PDF & PAINTS (A4 Size) ---
                PdfDocument document = new PdfDocument();
                int pageWidth = 595, pageHeight = 842, margin = 40;

                Paint paintMainTitle = new Paint(); paintMainTitle.setTextSize(22f); paintMainTitle.setFakeBoldText(true); paintMainTitle.setTextAlign(Paint.Align.CENTER); paintMainTitle.setColor(Color.parseColor("#85022E"));
                Paint paintSubTitle = new Paint(); paintSubTitle.setTextSize(14f); paintSubTitle.setTextAlign(Paint.Align.CENTER); paintSubTitle.setColor(Color.DKGRAY);
                Paint paintTextBold = new Paint(); paintTextBold.setTextSize(10f); paintTextBold.setFakeBoldText(true);
                Paint paintTextNormal = new Paint(); paintTextNormal.setTextSize(10f);
                Paint paintTextRight = new Paint(); paintTextRight.setTextSize(10f); paintTextRight.setTextAlign(Paint.Align.RIGHT);

                Paint paintGreen = new Paint(); paintGreen.setTextSize(16f); paintGreen.setFakeBoldText(true); paintGreen.setColor(Color.parseColor("#2E7D32")); paintGreen.setTextAlign(Paint.Align.CENTER);
                Paint paintRed = new Paint(); paintRed.setTextSize(16f); paintRed.setFakeBoldText(true); paintRed.setColor(Color.parseColor("#C62828")); paintRed.setTextAlign(Paint.Align.CENTER);
                Paint paintBoxTitle = new Paint(); paintBoxTitle.setTextSize(9f); paintBoxTitle.setFakeBoldText(true); paintBoxTitle.setColor(Color.DKGRAY); paintBoxTitle.setTextAlign(Paint.Align.CENTER);

                Paint paintLine = new Paint(); paintLine.setColor(Color.LTGRAY); paintLine.setStrokeWidth(1f);
                Paint paintDottedLine = new Paint(); paintDottedLine.setColor(Color.LTGRAY); paintDottedLine.setStrokeWidth(1f); paintDottedLine.setPathEffect(new DashPathEffect(new float[]{5, 5}, 0));
                Paint paintTableBg = new Paint(); paintTableBg.setColor(Color.parseColor("#F5F5F5"));
                Paint paintBorder = new Paint(); paintBorder.setStyle(Paint.Style.STROKE); paintBorder.setColor(Color.DKGRAY); paintBorder.setStrokeWidth(1f);

                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.US);
                SimpleDateFormat dateOnly = new SimpleDateFormat("dd MMM yyyy", Locale.US);
                String generatedOn = sdf.format(new Date());
                String reportDate = dateOnly.format(new Date());

                int pageNumber = 1;
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                PdfDocument.Page page = document.startPage(pageInfo);
                Canvas canvas = page.getCanvas();

                // --- 3. DRAW HEADER & SUMMARY CARDS (Page 1) ---
                int yPos = drawPageHeader(canvas, pageWidth, margin, tripName, memberName, generatedOn, reportDate, paintMainTitle, paintSubTitle, paintTextBold, paintTextNormal, paintTextRight);

                int cardTop = yPos;
                int cardBottom = yPos + 70;
                canvas.drawRoundRect(new RectF(margin, cardTop, pageWidth - margin, cardBottom), 5, 5, paintBorder);

                float colWidth = (pageWidth - (margin * 2)) / 4f;
                for (int i = 1; i <= 3; i++) {
                    canvas.drawLine(margin + (colWidth * i), cardTop + 10, margin + (colWidth * i), cardBottom - 10, paintLine);
                }

                float center1 = margin + (colWidth * 0.5f);
                float center2 = margin + (colWidth * 1.5f);
                float center3 = margin + (colWidth * 2.5f);
                float center4 = margin + (colWidth * 3.5f);

                canvas.drawText("TOTAL PAID", center1, cardTop + 20, paintBoxTitle);
                canvas.drawText(String.format(Locale.US, "₹%,.2f", totalCredit), center1, cardTop + 45, paintGreen);

                canvas.drawText("TOTAL EXPENSE", center2, cardTop + 20, paintBoxTitle);
                canvas.drawText(String.format(Locale.US, "₹%,.2f", totalDebit), center2, cardTop + 45, paintRed);

                canvas.drawText("CURRENT BALANCE", center3, cardTop + 20, paintBoxTitle);
                canvas.drawText(String.format(Locale.US, "₹%,.2f", Math.abs(finalBalance)), center3, cardTop + 40, finalBalance >= 0 ? paintGreen : paintRed);
                canvas.drawText(finalBalance >= 0 ? "(CR - Refundable)" : "(DR - Payable)", center3, cardTop + 55, paintBoxTitle);

                canvas.drawText("TRANSACTIONS", center4, cardTop + 20, paintBoxTitle);
                canvas.drawText(String.valueOf(transactionCount), center4, cardTop + 45, paintMainTitle);

                // --- 4. DRAW TABLE HEADER ---
                yPos = cardBottom + 30;

                int xDate = margin + 5;
                int xDesc = margin + 110;
                int xDebit = pageWidth - margin - 150;
                int xCredit = pageWidth - margin - 15;

                canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintTableBg);
                canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintBorder);

                canvas.drawText("Date", xDate, yPos + 17, paintTextBold);
                canvas.drawText("Description", xDesc, yPos + 17, paintTextBold);
                canvas.drawText("Debit (₹)", xDebit, yPos + 17, paintTextRight);
                canvas.drawText("Credit (₹)", xCredit, yPos + 17, paintTextRight);
                yPos += 25;

                // --- 5. DRAW TABLE ROWS ---
                try (Cursor cursor = dbHelper.getUnifiedLedger(tripId)) {
                    while (cursor.moveToNext()) {
                        String date = cursor.getString(cursor.getColumnIndexOrThrow("date"));
                        String purpose = cursor.getString(cursor.getColumnIndexOrThrow("purpose"));
                        double amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount"));
                        String paidBy = cursor.getString(cursor.getColumnIndexOrThrow("paid_by"));
                        String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                        String sharedWith = cursor.getString(cursor.getColumnIndexOrThrow("expense_shared_with"));
                        String[] sharedArray = (sharedWith != null && !sharedWith.isEmpty()) ? sharedWith.split(",") : new String[0];

                        double debit = 0.0, credit = 0.0;
                        boolean involvesMember = false;

                        if ("Expense".equals(type)) {
                            if (memberName.equals(paidBy)) { credit = amount; involvesMember = true; }
                            if (isParticipant(memberName, sharedArray)) { debit = amount / sharedArray.length; involvesMember = true; }
                        } else if ("Payment".equals(type) && memberName.equals(paidBy)) {
                            credit = amount; involvesMember = true;
                        }

                        if (involvesMember) {
                            // Pagination Check
                            if (yPos > pageHeight - 120) {
                                drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight);
                                document.finishPage(page);
                                pageNumber++;
                                pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                                page = document.startPage(pageInfo);
                                canvas = page.getCanvas();

                                // Re-draw Global Header & Table Header on new page
                                yPos = drawPageHeader(canvas, pageWidth, margin, tripName, memberName, generatedOn, reportDate, paintMainTitle, paintSubTitle, paintTextBold, paintTextNormal, paintTextRight);

                                canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintTableBg);
                                canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintBorder);
                                canvas.drawText("Date", xDate, yPos + 17, paintTextBold);
                                canvas.drawText("Description", xDesc, yPos + 17, paintTextBold);
                                canvas.drawText("Debit (₹)", xDebit, yPos + 17, paintTextRight);
                                canvas.drawText("Credit (₹)", xCredit, yPos + 17, paintTextRight);
                                yPos += 25;
                            }

                            String safeDate = (date != null) ? date : "N/A";
                            String safePurpose = (purpose != null) ? purpose : "";
                            if (safePurpose.length() > 40) safePurpose = safePurpose.substring(0, 37) + "...";

                            String debitStr = debit > 0 ? String.format(Locale.US, "%,.2f", debit) : "";
                            String creditStr = credit > 0 ? String.format(Locale.US, "%,.2f", credit) : "";

                            yPos += 20;
                            canvas.drawText(safeDate, xDate, yPos, paintTextNormal);
                            canvas.drawText(safePurpose, xDesc, yPos, paintTextNormal);
                            canvas.drawText(debitStr, xDebit, yPos, paintTextRight);
                            canvas.drawText(creditStr, xCredit, yPos, paintTextRight);

                            yPos += 10;
                            canvas.drawLine(margin, yPos, pageWidth - margin, yPos, paintDottedLine);
                        }
                    }
                }

                // --- 6. DRAW TOTALS ROW ---
                yPos += 5;
                canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintBorder);
                canvas.drawText("TOTALS", xDesc, yPos + 17, paintTextBold);
                canvas.drawText(String.format(Locale.US, "₹%,.2f", totalDebit), xDebit, yPos + 17, paintTextRight);
                canvas.drawText(String.format(Locale.US, "₹%,.2f", totalCredit), xCredit, yPos + 17, paintTextRight);

                // --- 7. DRAW REMARKS BOX ---
                yPos += 45;
                if (yPos > pageHeight - 140) {
                    drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight);
                    document.finishPage(page);
                    pageNumber++;
                    pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                    page = document.startPage(pageInfo);
                    canvas = page.getCanvas();
                    yPos = drawPageHeader(canvas, pageWidth, margin, tripName, memberName, generatedOn, reportDate, paintMainTitle, paintSubTitle, paintTextBold, paintTextNormal, paintTextRight);
                }

                canvas.drawRoundRect(new RectF(margin, yPos, pageWidth - margin, yPos + 55), 5, 5, paintBorder);
                canvas.drawText("Remarks :", margin + 15, yPos + 20, paintTextBold);
                canvas.drawText("• Positive balance (Green) means refundable to the member.", margin + 25, yPos + 35, paintTextNormal);
                canvas.drawText("• Negative balance (Red) means amount payable by the member.", margin + 25, yPos + 48, paintTextNormal);

                // --- 8. END OF PDF MARKER ---
                yPos += 85;
                Paint endPaint = new Paint(); endPaint.setTextSize(11f); endPaint.setFakeBoldText(true); endPaint.setColor(Color.parseColor("#85022E")); endPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("End of PDF. Total Pages: " + pageNumber + " page", pageWidth / 2f, yPos, endPaint);

                // Draw final footer
                drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight);

                document.finishPage(page);
                document.writeTo(outputStream);
                document.close();

                mainHandler.post(() -> Toast.makeText(context, "PDF Export Successful!", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(context, "PDF Export Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    // --- NEW HELPER: Draws the Repeating Header ---
    private int drawPageHeader(Canvas canvas, int pageWidth, int margin, String tripName, String memberName, String generatedOn, String reportDate, Paint paintMainTitle, Paint paintSubTitle, Paint paintTextBold, Paint paintTextNormal, Paint paintTextRight) {
        int yPos = margin + 20;

        canvas.drawText("TRIP EXPENSE MANAGER", pageWidth / 2f, yPos, paintMainTitle);
        yPos += 20;
        canvas.drawText("— Individual Member Ledger —", pageWidth / 2f, yPos, paintSubTitle);

        yPos += 35;

        // Measure text so we can bold ONLY the dynamic values
        canvas.drawText("Trip Name : ", margin, yPos, paintTextNormal);
        float tripLabelWidth = paintTextNormal.measureText("Trip Name : ");
        canvas.drawText(tripName, margin + tripLabelWidth, yPos, paintTextBold);
        canvas.drawText("Generated On : " + generatedOn, pageWidth - margin, yPos, paintTextRight);

        yPos += 15;
        canvas.drawText("Member      : ", margin, yPos, paintTextNormal);
        float memberLabelWidth = paintTextNormal.measureText("Member      : ");
        canvas.drawText(memberName, margin + memberLabelWidth, yPos, paintTextBold);
        canvas.drawText("Report Date   : " + reportDate, pageWidth - margin, yPos, paintTextRight);

        return yPos + 25; // Returns the exact Y coordinate where the next element should start drawing
    }

    // Helper method to draw the footer on every page
    private void drawFooter(Canvas canvas, int pageWidth, int pageHeight, int margin, int pageNumber, Paint alignLeft, Paint alignRight) {
        Paint linePaint = new Paint(); linePaint.setColor(Color.parseColor("#85022E")); linePaint.setStrokeWidth(2f);
        int footerY = pageHeight - margin - 20;

        canvas.drawLine(margin, footerY, pageWidth - margin, footerY, linePaint);

        Paint brandPaint = new Paint(alignLeft); brandPaint.setColor(Color.parseColor("#85022E")); brandPaint.setFakeBoldText(true);
        canvas.drawText("Trip Expense Manager", margin, footerY + 15, brandPaint);
        canvas.drawText("Designed & Developed by Anupam Das", margin, footerY + 27, alignLeft);

        canvas.drawText("Page " + pageNumber, pageWidth - margin, footerY + 20, alignRight);
    }
}