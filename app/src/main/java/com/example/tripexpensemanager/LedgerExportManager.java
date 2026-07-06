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

                // --- 2. SETUP PDF & PAINTS ---
                PdfDocument document = new PdfDocument();
                int pageWidth = 595, pageHeight = 842, margin = 40;

                Paint paintMainTitle = new Paint(); paintMainTitle.setTextSize(22f); paintMainTitle.setFakeBoldText(true); paintMainTitle.setTextAlign(Paint.Align.CENTER);
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

                // --- 3. DRAW HEADER (Page 1 Only) ---
                int yPos = margin + 20;
                canvas.drawText("TRIP EXPENSE MANAGER", pageWidth / 2f, yPos, paintMainTitle);
                yPos += 20;
                canvas.drawText("— Individual Member Ledger —", pageWidth / 2f, yPos, paintSubTitle);

                yPos += 30;
                canvas.drawText("Trip Name : " + tripName, margin, yPos, paintTextNormal);
                canvas.drawText("Generated On : " + generatedOn, pageWidth - margin, yPos, paintTextRight);
                yPos += 15;
                canvas.drawText("Member      : " + memberName, margin, yPos, paintTextNormal);
                canvas.drawText("Report Date   : " + reportDate, pageWidth - margin, yPos, paintTextRight);

                // --- 4. DRAW SUMMARY CARDS ---
                yPos += 25;
                int cardTop = yPos;
                int cardBottom = yPos + 70;
                canvas.drawRoundRect(new RectF(margin, cardTop, pageWidth - margin, cardBottom), 5, 5, paintBorder);

                // Dividers
                float colWidth = (pageWidth - (margin * 2)) / 4f;
                for (int i = 1; i <= 3; i++) {
                    canvas.drawLine(margin + (colWidth * i), cardTop + 10, margin + (colWidth * i), cardBottom - 10, paintLine);
                }

                // Card Contents
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
                canvas.drawText(String.valueOf(transactionCount), center4, cardTop + 45, paintMainTitle); // Just using big text for count

                // --- 5. DRAW TABLE HEADER ---
                yPos = cardBottom + 30;

                // Table Column X Positions
                int xDate = margin + 5;
                int xDesc = margin + 80;
                int xDebit = pageWidth - margin - 150;
                int xCredit = pageWidth - margin - 80;
                int xBal = pageWidth - margin - 5;

                canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintTableBg);
                canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintBorder);

                canvas.drawText("Date", xDate, yPos + 17, paintTextBold);
                canvas.drawText("Description", xDesc, yPos + 17, paintTextBold);
                canvas.drawText("Debit (₹)", xDebit, yPos + 17, paintTextRight);
                canvas.drawText("Credit (₹)", xCredit, yPos + 17, paintTextRight);
                canvas.drawText("Balance (₹)", xBal, yPos + 17, paintTextRight);
                yPos += 25;

                // --- 6. DRAW TABLE ROWS ---
                double runningBalance = 0.0;

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
                            runningBalance = runningBalance + credit - debit;

                            // Pagination Check
                            if (yPos > pageHeight - 150) {
                                // Draw Footer before breaking page
                                drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight);
                                document.finishPage(page);
                                pageNumber++;
                                pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                                page = document.startPage(pageInfo);
                                canvas = page.getCanvas();

                                // Re-draw Table Header on new page
                                yPos = margin + 20;
                                canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintTableBg);
                                canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintBorder);
                                canvas.drawText("Date", xDate, yPos + 17, paintTextBold);
                                canvas.drawText("Description", xDesc, yPos + 17, paintTextBold);
                                canvas.drawText("Debit (₹)", xDebit, yPos + 17, paintTextRight);
                                canvas.drawText("Credit (₹)", xCredit, yPos + 17, paintTextRight);
                                canvas.drawText("Balance (₹)", xBal, yPos + 17, paintTextRight);
                                yPos += 25;
                            }

                            // Clean Data
                            String safeDate = (date != null) ? date : "N/A";
                            String safePurpose = (purpose != null) ? purpose : "";
                            if (safePurpose.length() > 35) safePurpose = safePurpose.substring(0, 32) + "...";

                            String debitStr = debit > 0 ? String.format(Locale.US, "%,.2f", debit) : "";
                            String creditStr = credit > 0 ? String.format(Locale.US, "%,.2f", credit) : "";
                            String balStr = String.format(Locale.US, "%,.2f", runningBalance);

                            yPos += 20;
                            canvas.drawText(safeDate, xDate, yPos, paintTextNormal);
                            canvas.drawText(safePurpose, xDesc, yPos, paintTextNormal);
                            canvas.drawText(debitStr, xDebit, yPos, paintTextRight);
                            canvas.drawText(creditStr, xCredit, yPos, paintTextRight);

                            // Color balance based on positive/negative
                            Paint balPaint = new Paint(paintTextRight);
                            balPaint.setColor(runningBalance < 0 ? Color.parseColor("#C62828") : Color.parseColor("#2E7D32"));
                            canvas.drawText(balStr, xBal, yPos, balPaint);

                            // Draw dotted divider
                            yPos += 10;
                            canvas.drawLine(margin, yPos, pageWidth - margin, yPos, paintDottedLine);
                        }
                    }
                }

                // --- 7. DRAW TOTALS ROW ---
                yPos += 5;
                canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintBorder); // Border around totals
                canvas.drawText("TOTALS", xDesc, yPos + 17, paintTextBold);
                canvas.drawText(String.format(Locale.US, "₹%,.2f", totalDebit), xDebit, yPos + 17, paintTextRight);
                canvas.drawText(String.format(Locale.US, "₹%,.2f", totalCredit), xCredit, yPos + 17, paintTextRight);
                canvas.drawText(String.format(Locale.US, "₹%,.2f", finalBalance), xBal, yPos + 17, paintTextRight);

                // --- 8. DRAW REMARKS BOX ---
                yPos += 45;
                if (yPos > pageHeight - 120) { // Push to next page if out of room
                    drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight);
                    document.finishPage(page);
                    pageNumber++;
                    pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                    page = document.startPage(pageInfo);
                    canvas = page.getCanvas();
                    yPos = margin + 20;
                }

                canvas.drawRoundRect(new RectF(margin, yPos, pageWidth - margin, yPos + 55), 5, 5, paintBorder);
                canvas.drawText("Remarks :", margin + 15, yPos + 20, paintTextBold);
                canvas.drawText("• Positive balance (Green) means refundable to the member.", margin + 25, yPos + 35, paintTextNormal);
                canvas.drawText("• Negative balance (Red) means amount payable by the member.", margin + 25, yPos + 48, paintTextNormal);

                // Draw final footer
                drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight);

                document.finishPage(page);
                document.writeTo(outputStream);
                document.close();

                mainHandler.post(() -> Toast.makeText(context, "Premium PDF Export Successful!", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(context, "PDF Export Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
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