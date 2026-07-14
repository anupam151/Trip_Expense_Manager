package com.example.tripexpensemanager;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.graphics.DashPathEffect;
import android.graphics.RectF;
import java.text.SimpleDateFormat;

public class LedgerExportManager {

    private final Context context;
    private final TripDatabaseHelper dbHelper; // Kept so other activities don't break
    private final ExecutorService executor;
    private final Handler mainHandler;

    public LedgerExportManager(Context context, TripDatabaseHelper dbHelper) {
        this.context = context;
        this.dbHelper = dbHelper;
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    // ==========================================
    // --- NEW: UNIFIED DATA FETCHING ENGINE ---
    // ==========================================
    // This uses your proven LedgerDataService so data is 100% accurate and never blank!
    private interface DataFetchCallback {
        void onDataFetched(DocumentSnapshot tripDoc, List<LedgerEntry> entries);
        void onError(Exception e);
    }

    private void fetchExportData(String tripId, DataFetchCallback callback) {
        FirebaseFirestore.getInstance().collection("Trips").document(tripId)
                .get(Source.DEFAULT)
                .addOnSuccessListener(tripDoc -> {
                    new LedgerDataService().fetchUnifiedLedger(tripId, new LedgerDataService.LedgerCallback() {
                        @Override
                        public void onResult(List<LedgerEntry> entries) {
                            callback.onDataFetched(tripDoc, entries);
                        }

                        @Override
                        public void onError(Exception e) {
                            callback.onError(e);
                        }
                    });
                })
                .addOnFailureListener(callback::onError);
    }

    private boolean isParticipant(String memberName, String[] sharedArray) {
        for (String s : sharedArray) {
            if (s.trim().equalsIgnoreCase(memberName.trim())) return true;
        }
        return false;
    }


    // ==========================================
    // 1. EXPORT TO CSV (EXCEL) - ALL MEMBERS
    // ==========================================
    @SuppressWarnings("unused")
    public void exportAllMembersToCsv(Uri fileUri, String tripId, ArrayList<String> allMembers) {
        Toast.makeText(context, "Preparing Excel file...", Toast.LENGTH_SHORT).show();

        fetchExportData(tripId, new DataFetchCallback() {
            @Override
            public void onDataFetched(DocumentSnapshot tripDoc, List<LedgerEntry> entries) {
                executor.execute(() -> {
                    try (OutputStream outputStream = context.getContentResolver().openOutputStream(fileUri)) {
                        if (outputStream == null) return;

                        StringBuilder csvBuilder = new StringBuilder();
                        csvBuilder.append("Trip Expense Report\n\n");

                        for (String member : allMembers) {
                            csvBuilder.append("Member Ledger: ").append(member).append("\n");
                            csvBuilder.append("Date,Purpose,Type,Amount (Paid/Used)\n");

                            for (LedgerEntry entry : entries) {
                                String sharedWith = entry.getSharedWith();
                                String[] sharedArray = (sharedWith != null && !sharedWith.isEmpty()) ? sharedWith.split(",\\s*") : new String[0];

                                boolean involvesMember = false;
                                double displayAmount = 0.0;

                                if (entry.isExpense()) {
                                    if (member.equals(entry.getPaidBy())) { involvesMember = true; displayAmount = entry.getAmount(); }
                                    else if (isParticipant(member, sharedArray)) { involvesMember = true; displayAmount = entry.getAmount() / sharedArray.length; }
                                } else if (member.equals(entry.getPaidBy())) {
                                    involvesMember = true; displayAmount = entry.getAmount();
                                }

                                if (involvesMember) {
                                    String safePurpose = (entry.getPurpose() != null) ? entry.getPurpose().replace(",", " ") : "";
                                    csvBuilder.append(String.format(Locale.US, "%s,%s,%s,%.2f\n", entry.getDate(), safePurpose, entry.getType(), displayAmount));
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

            @Override
            public void onError(Exception e) {
                Toast.makeText(context, "Failed to load data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ==========================================
    // 2. EXPORT TO PDF - ALL MEMBERS (PAGINATED)
    // ==========================================
    @SuppressWarnings("unused")
    public void exportAllMembersToPdf(Uri fileUri, String tripId, ArrayList<String> allMembers) {
        Toast.makeText(context, "Preparing PDF...", Toast.LENGTH_SHORT).show();

        fetchExportData(tripId, new DataFetchCallback() {
            @Override
            public void onDataFetched(DocumentSnapshot tripDoc, List<LedgerEntry> entries) {
                executor.execute(() -> {
                    try (OutputStream outputStream = context.getContentResolver().openOutputStream(fileUri)) {
                        if (outputStream == null) return;

                        PdfDocument document = new PdfDocument();
                        Paint paint = new Paint(); Paint titlePaint = new Paint();
                        titlePaint.setTextSize(18f); titlePaint.setFakeBoldText(true); titlePaint.setColor(Color.parseColor("#85022E"));
                        paint.setTextSize(12f); paint.setColor(Color.BLACK);

                        int pageWidth = 595, pageHeight = 842, margin = 30;

                        for (String member : allMembers) {
                            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.getPages().size() + 1).create();
                            PdfDocument.Page page = document.startPage(pageInfo);
                            Canvas canvas = page.getCanvas();
                            int yPosition = margin;

                            canvas.drawText("Ledger Report: " + member, margin, yPosition, titlePaint); yPosition += 30;
                            canvas.drawText("Date", margin, yPosition, titlePaint); canvas.drawText("Purpose", margin + 100, yPosition, titlePaint);
                            canvas.drawText("Amount", pageWidth - 100, yPosition, titlePaint); yPosition += 20;

                            for (LedgerEntry entry : entries) {
                                String sharedWith = entry.getSharedWith();
                                String[] sharedArray = (sharedWith != null && !sharedWith.isEmpty()) ? sharedWith.split(",\\s*") : new String[0];

                                boolean involvesMember = false;
                                double displayAmount = 0.0;

                                if (entry.isExpense()) {
                                    if (member.equals(entry.getPaidBy())) { involvesMember = true; displayAmount = entry.getAmount(); }
                                    else if (isParticipant(member, sharedArray)) { involvesMember = true; displayAmount = entry.getAmount() / sharedArray.length; }
                                } else if (member.equals(entry.getPaidBy())) {
                                    involvesMember = true; displayAmount = entry.getAmount();
                                }

                                if (involvesMember) {
                                    if (yPosition > pageHeight - margin) {
                                        document.finishPage(page);
                                        pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.getPages().size() + 1).create();
                                        page = document.startPage(pageInfo); canvas = page.getCanvas(); yPosition = margin;
                                    }
                                    String date = entry.getDate() != null ? entry.getDate() : "N/A";
                                    String purpose = entry.getPurpose() != null ? entry.getPurpose() : "-";
                                    String shortPurpose = purpose.length() > 30 ? purpose.substring(0, 27) + "..." : purpose;

                                    canvas.drawText(date, margin, yPosition, paint);
                                    canvas.drawText(shortPurpose, margin + 100, yPosition, paint);
                                    canvas.drawText(String.format(Locale.US, "%.2f", displayAmount), pageWidth - 100, yPosition, paint);
                                    yPosition += 20;
                                }
                            }
                            document.finishPage(page);
                        }
                        document.writeTo(outputStream); document.close();
                        mainHandler.post(() -> Toast.makeText(context, "PDF Export Successful!", Toast.LENGTH_LONG).show());
                    } catch (Exception e) {
                        mainHandler.post(() -> Toast.makeText(context, "Export Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(context, "Failed to load data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ==========================================
    // 3. EXPORT TO CSV (EXCEL) - COMPLETE LEDGER
    // ==========================================
    public void exportCompleteLedgerToCsv(Uri fileUri, String tripId, ArrayList<String> members) {
        Toast.makeText(context, "Preparing Excel file...", Toast.LENGTH_SHORT).show();

        fetchExportData(tripId, new DataFetchCallback() {
            @Override
            public void onDataFetched(DocumentSnapshot tripDoc, List<LedgerEntry> entries) {
                executor.execute(() -> {
                    try (OutputStream outputStream = context.getContentResolver().openOutputStream(fileUri)) {
                        if (outputStream == null) return;

                        StringBuilder csv = new StringBuilder();

                        // 1. ADD MEMBERS AND FUND TO THE HEADER
                        csv.append("Date,Purpose,Total Amount");
                        for (String m : members) {
                            csv.append(",").append(m).append(" Credit");
                            csv.append(",").append(m).append(" Debit");
                        }
                        csv.append(",Fund Credit,Fund Debit\n");

                        double[] totalPaid = new double[members.size()];
                        double[] totalUsed = new double[members.size()];
                        double totalFundCredit = 0.0;
                        double totalFundDebit = 0.0;

                        // 2. PROCESS EVERY ROW
                        for (LedgerEntry entry : entries) {
                            String[] sharedArray = (entry.getSharedWith() != null && !entry.getSharedWith().isEmpty()) ? entry.getSharedWith().split(",\\s*") : new String[0];
                            String safePurpose = (entry.getPurpose() != null) ? entry.getPurpose().replace(",", " ") : "";
                            String safeDate = (entry.getDate() != null) ? entry.getDate() : "N/A";

                            csv.append(safeDate).append(",").append(safePurpose).append(",").append(String.format(Locale.US, "%.2f", entry.getAmount()));

                            // Loop Members
                            for (int i = 0; i < members.size(); i++) {
                                String m = members.get(i);
                                double paidVal = 0.0, usedVal = 0.0;

                                if (entry.isExpense()) {
                                    double share = (sharedArray.length > 0) ? (entry.getAmount() / sharedArray.length) : 0.0;
                                    if (m.equals(entry.getPaidBy())) paidVal = entry.getAmount();
                                    if (isParticipant(m, sharedArray)) usedVal = share;
                                } else if (!entry.isExpense() && m.equals(entry.getPaidBy())) {
                                    paidVal = entry.getAmount();
                                }

                                totalPaid[i] += paidVal; totalUsed[i] += usedVal;
                                csv.append(",").append(String.format(Locale.US, "%.2f", paidVal)).append(",").append(String.format(Locale.US, "%.2f", usedVal));
                            }

                            // Process Fund Logic exactly like your screen!
                            double rowFundCredit = 0.0;
                            double rowFundDebit = 0.0;
                            if (entry.isExpense() && "Fund".equalsIgnoreCase(entry.getPaidBy())) {
                                rowFundDebit = entry.getAmount();
                            } else if (!entry.isExpense()) {
                                rowFundCredit = entry.getAmount();
                            }
                            totalFundCredit += rowFundCredit;
                            totalFundDebit += rowFundDebit;

                            csv.append(",").append(String.format(Locale.US, "%.2f", rowFundCredit))
                                    .append(",").append(String.format(Locale.US, "%.2f", rowFundDebit))
                                    .append("\n");
                        }

                        // 3. ADD TOTALS ROW AT BOTTOM
                        csv.append("TOTALS,-,-");
                        for (int i = 0; i < members.size(); i++) {
                            csv.append(",").append(String.format(Locale.US, "%.2f", totalPaid[i])).append(",").append(String.format(Locale.US, "%.2f", totalUsed[i]));
                        }
                        csv.append(",").append(String.format(Locale.US, "%.2f", totalFundCredit)).append(",").append(String.format(Locale.US, "%.2f", totalFundDebit)).append("\n");

                        outputStream.write(csv.toString().getBytes()); outputStream.flush();
                        mainHandler.post(() -> Toast.makeText(context, "Excel Export Successful!", Toast.LENGTH_LONG).show());
                    } catch (Exception e) {
                        mainHandler.post(() -> Toast.makeText(context, "Export Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(context, "Failed to load data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ==========================================
    // 4. EXPORT TO CSV (EXCEL) - INDIVIDUAL MEMBER
    // ==========================================
    public void exportIndividualMemberToCsv(Uri fileUri, String tripId, String memberName) {
        Toast.makeText(context, "Preparing Excel file...", Toast.LENGTH_SHORT).show();

        fetchExportData(tripId, new DataFetchCallback() {
            @Override
            public void onDataFetched(DocumentSnapshot tripDoc, List<LedgerEntry> entries) {
                executor.execute(() -> {
                    try (OutputStream outputStream = context.getContentResolver().openOutputStream(fileUri)) {
                        if (outputStream == null) return;

                        StringBuilder csv = new StringBuilder();
                        csv.append("Ledger Report: ").append(memberName).append("\n\n");
                        csv.append("Date,Purpose,Debit (Used),Credit (Paid)\n");

                        double totalDebit = 0.0, totalCredit = 0.0;

                        for (LedgerEntry entry : entries) {
                            String[] sharedArray = (entry.getSharedWith() != null && !entry.getSharedWith().isEmpty()) ? entry.getSharedWith().split(",\\s*") : new String[0];
                            double debit = 0.0, credit = 0.0; boolean involvesMember = false;

                            if (entry.isExpense()) {
                                double share = (sharedArray.length > 0) ? (entry.getAmount() / sharedArray.length) : 0.0;
                                if (memberName.equals(entry.getPaidBy())) { credit = entry.getAmount(); involvesMember = true; }
                                if (isParticipant(memberName, sharedArray)) { debit = share; involvesMember = true; }
                            } else if (!entry.isExpense() && memberName.equals(entry.getPaidBy())) {
                                credit = entry.getAmount(); involvesMember = true;
                            }

                            if (involvesMember) {
                                totalDebit += debit; totalCredit += credit;
                                String safePurpose = (entry.getPurpose() != null) ? entry.getPurpose().replace(",", " ") : "";
                                String safeDate = (entry.getDate() != null) ? entry.getDate() : "N/A";
                                String debitStr = debit > 0 ? String.format(Locale.US, "%.2f", debit) : "-";
                                String creditStr = credit > 0 ? String.format(Locale.US, "%.2f", credit) : "-";
                                csv.append(safeDate).append(",").append(safePurpose).append(",").append(debitStr).append(",").append(creditStr).append("\n");
                            }
                        }
                        csv.append("TOTALS,-,").append(String.format(Locale.US, "%.2f", totalDebit)).append(",").append(String.format(Locale.US, "%.2f", totalCredit)).append("\n");

                        outputStream.write(csv.toString().getBytes()); outputStream.flush();
                        mainHandler.post(() -> Toast.makeText(context, "Excel Export Successful!", Toast.LENGTH_LONG).show());
                    } catch (Exception e) {
                        mainHandler.post(() -> Toast.makeText(context, "Export Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                });
            }
            @Override
            public void onError(Exception e) {
                Toast.makeText(context, "Failed to load data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ==========================================
    // 6. EXPORT TO PDF - ALL MEMBERS IN ONE PDF
    // ==========================================
    public void exportAllMembersToSinglePdf(Uri fileUri, String tripId) {
        Toast.makeText(context, "Preparing Master PDF...", Toast.LENGTH_SHORT).show();

        fetchExportData(tripId, new DataFetchCallback() {
            @Override
            public void onDataFetched(DocumentSnapshot tripDoc, List<LedgerEntry> entries) {
                executor.execute(() -> {
                    try (OutputStream outputStream = context.getContentResolver().openOutputStream(fileUri)) {
                        if (outputStream == null) return;

                        String tripName = tripDoc.contains("tripName") ? tripDoc.getString("tripName") : "Trip Ledger";
                        ArrayList<String> allMembers = new ArrayList<>();

                        String activeRaw = tripDoc.getString("members");
                        String inactiveRaw = tripDoc.getString("inactiveMembers");
                        if(activeRaw != null) for(String m : activeRaw.split(",")) if(!m.trim().isEmpty() && !allMembers.contains(m.trim())) allMembers.add(m.trim());
                        if(inactiveRaw != null) for(String m : inactiveRaw.split(",")) if(!m.trim().isEmpty() && !allMembers.contains(m.trim())) allMembers.add(m.trim());

                        for (LedgerEntry e : entries) {
                            if(e.getPaidBy() != null && !e.getPaidBy().equalsIgnoreCase("Fund") && !allMembers.contains(e.getPaidBy())) allMembers.add(e.getPaidBy());
                            if(e.getSharedWith() != null) for(String m : e.getSharedWith().split(",\\s*")) if(!m.trim().isEmpty() && !allMembers.contains(m.trim())) allMembers.add(m.trim());
                        }

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
                        String generatedOn = sdf.format(new Date()); String reportDate = dateOnly.format(new Date());
                        int pageNumber = 1;
                        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                        PdfDocument.Page page = document.startPage(pageInfo); Canvas canvas = page.getCanvas();

                        for (int i = 0; i < allMembers.size(); i++) {
                            String memberName = allMembers.get(i);
                            if (i > 0) {
                                drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight);
                                document.finishPage(page); pageNumber++;
                                pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                                page = document.startPage(pageInfo); canvas = page.getCanvas();
                            }

                            double totalDebit = 0.0; double totalCredit = 0.0; int transactionCount = 0;
                            for(LedgerEntry entry : entries) {
                                String[] sharedArray = (entry.getSharedWith() != null && !entry.getSharedWith().isEmpty()) ? entry.getSharedWith().split(",\\s*") : new String[0];
                                if (entry.isExpense()) {
                                    if (memberName.equals(entry.getPaidBy())) { totalCredit += entry.getAmount(); transactionCount++; }
                                    else if (isParticipant(memberName, sharedArray)) { totalDebit += (entry.getAmount() / sharedArray.length); transactionCount++; }
                                } else if (memberName.equals(entry.getPaidBy())) {
                                    totalCredit += entry.getAmount(); transactionCount++;
                                }
                            }
                            double finalBalance = totalCredit - totalDebit;

                            int yPos = drawPageHeader(canvas, pageWidth, margin, tripName, memberName, generatedOn, reportDate, paintMainTitle, paintSubTitle, paintTextBold, paintTextNormal, paintTextRight);
                            int cardTop = yPos; int cardBottom = yPos + 70;
                            canvas.drawRoundRect(new RectF(margin, cardTop, pageWidth - margin, cardBottom), 5, 5, paintBorder);
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

                            for (LedgerEntry entry : entries) {
                                String[] sharedArray = (entry.getSharedWith() != null && !entry.getSharedWith().isEmpty()) ? entry.getSharedWith().split(",\\s*") : new String[0];
                                double debit = 0.0, credit = 0.0; boolean involvesMember = false;

                                if (entry.isExpense()) {
                                    if (memberName.equals(entry.getPaidBy())) { credit = entry.getAmount(); involvesMember = true; }
                                    if (isParticipant(memberName, sharedArray)) { debit = entry.getAmount() / sharedArray.length; involvesMember = true; }
                                } else if (memberName.equals(entry.getPaidBy())) { credit = entry.getAmount(); involvesMember = true; }

                                if (involvesMember) {
                                    if (yPos > pageHeight - 120) {
                                        drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight);
                                        document.finishPage(page); pageNumber++;
                                        pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                                        page = document.startPage(pageInfo); canvas = page.getCanvas();
                                        yPos = drawPageHeader(canvas, pageWidth, margin, tripName, memberName, generatedOn, reportDate, paintMainTitle, paintSubTitle, paintTextBold, paintTextNormal, paintTextRight);
                                        canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintTableBg); canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintBorder);
                                        canvas.drawText("Date", xDate, yPos + 17, paintTextBold); canvas.drawText("Description", xDesc, yPos + 17, paintTextBold);
                                        canvas.drawText("Debit (₹)", xDebit, yPos + 17, paintTextRight); canvas.drawText("Credit (₹)", xCredit, yPos + 17, paintTextRight);
                                        yPos += 25;
                                    }
                                    String safeDate = (entry.getDate() != null) ? entry.getDate() : "N/A";
                                    String safePurpose = (entry.getPurpose() != null) ? entry.getPurpose() : ""; if (safePurpose.length() > 40) safePurpose = safePurpose.substring(0, 37) + "...";
                                    String debitStr = debit > 0 ? String.format(Locale.US, "%,.2f", debit) : ""; String creditStr = credit > 0 ? String.format(Locale.US, "%,.2f", credit) : "";
                                    yPos += 20; canvas.drawText(safeDate, xDate, yPos, paintTextNormal); canvas.drawText(safePurpose, xDesc, yPos, paintTextNormal);
                                    canvas.drawText(debitStr, xDebit, yPos, paintTextRight); canvas.drawText(creditStr, xCredit, yPos, paintTextRight);
                                    yPos += 10; canvas.drawLine(margin, yPos, pageWidth - margin, yPos, paintDottedLine);
                                }
                            }
                            yPos += 5; canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintBorder);
                            canvas.drawText("TOTALS", xDesc, yPos + 17, paintTextBold); canvas.drawText(String.format(Locale.US, "₹%,.2f", totalDebit), xDebit, yPos + 17, paintTextRight); canvas.drawText(String.format(Locale.US, "₹%,.2f", totalCredit), xCredit, yPos + 17, paintTextRight);

                            yPos += 45;
                            if (yPos > pageHeight - 140) {
                                drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight); document.finishPage(page); pageNumber++;
                                pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                                page = document.startPage(pageInfo); canvas = page.getCanvas();
                                yPos = drawPageHeader(canvas, pageWidth, margin, tripName, memberName, generatedOn, reportDate, paintMainTitle, paintSubTitle, paintTextBold, paintTextNormal, paintTextRight);
                            }
                            canvas.drawRoundRect(new RectF(margin, yPos, pageWidth - margin, yPos + 55), 5, 5, paintBorder);
                            canvas.drawText("Remarks :", margin + 15, yPos + 20, paintTextBold);
                            canvas.drawText("• Positive balance (Green) means refundable to the member.", margin + 25, yPos + 35, paintTextNormal);
                            canvas.drawText("• Negative balance (Red) means amount payable by the member.", margin + 25, yPos + 48, paintTextNormal);
                        }

                        int finalYPos = page.getCanvas().getHeight() - margin - 60;
                        Paint endPaint = new Paint(); endPaint.setTextSize(11f); endPaint.setFakeBoldText(true); endPaint.setColor(Color.parseColor("#85022E")); endPaint.setTextAlign(Paint.Align.CENTER);
                        canvas.drawText("End of the master pdf. Total pages: " + pageNumber, pageWidth / 2f, finalYPos, endPaint);

                        drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight);
                        document.finishPage(page); document.writeTo(outputStream); document.close();

                        mainHandler.post(() -> Toast.makeText(context, "Master PDF Export Successful!", Toast.LENGTH_LONG).show());
                    } catch (Exception e) {
                        mainHandler.post(() -> Toast.makeText(context, "PDF Export Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                });
            }
            @Override
            public void onError(Exception e) {
                Toast.makeText(context, "Failed to load data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void exportIndividualMemberToPdf(Uri fileUri, String tripId, String memberName) {
        Toast.makeText(context, "Preparing Premium PDF...", Toast.LENGTH_SHORT).show();

        fetchExportData(tripId, new DataFetchCallback() {
            @Override
            public void onDataFetched(DocumentSnapshot tripDoc, List<LedgerEntry> entries) {
                executor.execute(() -> {
                    try (OutputStream outputStream = context.getContentResolver().openOutputStream(fileUri)) {
                        if (outputStream == null) return;

                        String tripName = tripDoc.contains("tripName") ? tripDoc.getString("tripName") : "Trip Ledger";
                        double totalDebit = 0.0, totalCredit = 0.0; int transactionCount = 0;

                        for (LedgerEntry entry : entries) {
                            String[] sharedArray = (entry.getSharedWith() != null && !entry.getSharedWith().isEmpty()) ? entry.getSharedWith().split(",\\s*") : new String[0];
                            if (entry.isExpense()) {
                                if (memberName.equals(entry.getPaidBy())) { totalCredit += entry.getAmount(); transactionCount++; }
                                else if (isParticipant(memberName, sharedArray)) { totalDebit += (entry.getAmount() / sharedArray.length); transactionCount++; }
                            } else if (memberName.equals(entry.getPaidBy())) { totalCredit += entry.getAmount(); transactionCount++; }
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
                        Paint paintDottedLine = new Paint(); paintDottedLine.setColor(Color.LTGRAY); paintDottedLine.setStrokeWidth(1f); paintDottedLine.setPathEffect(new DashPathEffect(new float[]{5, 5}, 0));
                        Paint paintTableBg = new Paint(); paintTableBg.setColor(Color.parseColor("#F5F5F5"));
                        Paint paintBorder = new Paint(); paintBorder.setStyle(Paint.Style.STROKE); paintBorder.setColor(Color.DKGRAY); paintBorder.setStrokeWidth(1f);

                        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.US); SimpleDateFormat dateOnly = new SimpleDateFormat("dd MMM yyyy", Locale.US);
                        String generatedOn = sdf.format(new Date()); String reportDate = dateOnly.format(new Date());

                        int pageNumber = 1;
                        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                        PdfDocument.Page page = document.startPage(pageInfo); Canvas canvas = page.getCanvas();

                        int yPos = drawPageHeader(canvas, pageWidth, margin, tripName, memberName, generatedOn, reportDate, paintMainTitle, paintSubTitle, paintTextBold, paintTextNormal, paintTextRight);
                        int cardTop = yPos; int cardBottom = yPos + 70;
                        canvas.drawRoundRect(new RectF(margin, cardTop, pageWidth - margin, cardBottom), 5, 5, paintBorder);

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

                        for (LedgerEntry entry : entries) {
                            String[] sharedArray = (entry.getSharedWith() != null && !entry.getSharedWith().isEmpty()) ? entry.getSharedWith().split(",\\s*") : new String[0];
                            double debit = 0.0, credit = 0.0; boolean involvesMember = false;

                            if (entry.isExpense()) {
                                if (memberName.equals(entry.getPaidBy())) { credit = entry.getAmount(); involvesMember = true; }
                                if (isParticipant(memberName, sharedArray)) { debit = entry.getAmount() / sharedArray.length; involvesMember = true; }
                            } else if (memberName.equals(entry.getPaidBy())) { credit = entry.getAmount(); involvesMember = true; }

                            if (involvesMember) {
                                if (yPos > pageHeight - 120) {
                                    drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight);
                                    document.finishPage(page); pageNumber++; pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                                    page = document.startPage(pageInfo); canvas = page.getCanvas();
                                    yPos = drawPageHeader(canvas, pageWidth, margin, tripName, memberName, generatedOn, reportDate, paintMainTitle, paintSubTitle, paintTextBold, paintTextNormal, paintTextRight);

                                    canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintTableBg); canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintBorder);
                                    canvas.drawText("Date", xDate, yPos + 17, paintTextBold); canvas.drawText("Description", xDesc, yPos + 17, paintTextBold);
                                    canvas.drawText("Debit (₹)", xDebit, yPos + 17, paintTextRight); canvas.drawText("Credit (₹)", xCredit, yPos + 17, paintTextRight);
                                    yPos += 25;
                                }

                                String safeDate = (entry.getDate() != null) ? entry.getDate() : "N/A";
                                String safePurpose = (entry.getPurpose() != null) ? entry.getPurpose() : ""; if (safePurpose.length() > 40) safePurpose = safePurpose.substring(0, 37) + "...";
                                String debitStr = debit > 0 ? String.format(Locale.US, "%,.2f", debit) : ""; String creditStr = credit > 0 ? String.format(Locale.US, "%,.2f", credit) : "";

                                yPos += 20;
                                canvas.drawText(safeDate, xDate, yPos, paintTextNormal); canvas.drawText(safePurpose, xDesc, yPos, paintTextNormal);
                                canvas.drawText(debitStr, xDebit, yPos, paintTextRight); canvas.drawText(creditStr, xCredit, yPos, paintTextRight);
                                yPos += 10; canvas.drawLine(margin, yPos, pageWidth - margin, yPos, paintDottedLine);
                            }
                        }

                        yPos += 5; canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintBorder);
                        canvas.drawText("TOTALS", xDesc, yPos + 17, paintTextBold); canvas.drawText(String.format(Locale.US, "₹%,.2f", totalDebit), xDebit, yPos + 17, paintTextRight); canvas.drawText(String.format(Locale.US, "₹%,.2f", totalCredit), xCredit, yPos + 17, paintTextRight);

                        yPos += 45;
                        if (yPos > pageHeight - 140) {
                            drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight); document.finishPage(page); pageNumber++;
                            pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                            page = document.startPage(pageInfo); canvas = page.getCanvas();
                            yPos = drawPageHeader(canvas, pageWidth, margin, tripName, memberName, generatedOn, reportDate, paintMainTitle, paintSubTitle, paintTextBold, paintTextNormal, paintTextRight);
                        }

                        canvas.drawRoundRect(new RectF(margin, yPos, pageWidth - margin, yPos + 55), 5, 5, paintBorder);
                        canvas.drawText("Remarks :", margin + 15, yPos + 20, paintTextBold);
                        canvas.drawText("• Positive balance (Green) means refundable to the member.", margin + 25, yPos + 35, paintTextNormal);
                        canvas.drawText("• Negative balance (Red) means amount payable by the member.", margin + 25, yPos + 48, paintTextNormal);

                        yPos += 85; Paint endPaint = new Paint(); endPaint.setTextSize(11f); endPaint.setFakeBoldText(true); endPaint.setColor(Color.parseColor("#85022E")); endPaint.setTextAlign(Paint.Align.CENTER);
                        canvas.drawText("End of PDF. Total Pages: " + pageNumber + " page", pageWidth / 2f, yPos, endPaint);
                        drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight);

                        document.finishPage(page); document.writeTo(outputStream); document.close();
                        mainHandler.post(() -> Toast.makeText(context, "PDF Export Successful!", Toast.LENGTH_LONG).show());
                    } catch (Exception e) {
                        mainHandler.post(() -> Toast.makeText(context, "PDF Export Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                });
            }
            @Override
            public void onError(Exception e) {
                Toast.makeText(context, "Failed to load data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ==========================================
    // EXPORT ALL TRANSACTIONS (A4 PORTRAIT)
    // ==========================================
    public void exportAllTransactionsToPdf(Uri uri, String tripId) {
        Toast.makeText(context, "Preparing All Transactions PDF...", Toast.LENGTH_SHORT).show();

        fetchExportData(tripId, new DataFetchCallback() {
            @Override
            public void onDataFetched(DocumentSnapshot tripDoc, List<LedgerEntry> entries) {
                executor.execute(() -> {
                    try {
                        android.graphics.pdf.PdfDocument pdfDocument = new android.graphics.pdf.PdfDocument();

                        // STANDARD A4 PORTRAIT DIMENSIONS
                        int PAGE_WIDTH = 595, PAGE_HEIGHT = 842, MARGIN = 30;

                        Paint paint = new Paint(); Paint titlePaint = new Paint(); Paint headerPaint = new Paint(); Paint linePaint = new Paint();
                        titlePaint.setTextSize(18f); // Adjusted for portrait
                        titlePaint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)); titlePaint.setColor(Color.parseColor("#85022E"));
                        headerPaint.setTextSize(10f); // Adjusted to fit 6 columns
                        headerPaint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)); headerPaint.setColor(Color.WHITE);
                        paint.setTextSize(9.5f); // Row text size
                        paint.setColor(Color.BLACK); linePaint.setColor(Color.LTGRAY); linePaint.setStrokeWidth(1f);

                        String tripName = tripDoc.contains("tripName") ? tripDoc.getString("tripName") : "Unknown Trip";
                        String destination = tripDoc.contains("destination") ? tripDoc.getString("destination") : "-";
                        String startDate = tripDoc.contains("startDate") ? tripDoc.getString("startDate") : "-";
                        String endDate = tripDoc.contains("endDate") ? tripDoc.getString("endDate") : "-";
                        String members = tripDoc.contains("members") ? tripDoc.getString("members") : "-";

                        double totalExpense = 0.0, totalPayment = 0.0;

                        android.graphics.pdf.PdfDocument.PageInfo pageInfo = new android.graphics.pdf.PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create();
                        android.graphics.pdf.PdfDocument.Page page = pdfDocument.startPage(pageInfo);
                        Canvas canvas = page.getCanvas();

                        int yPosition = MARGIN + 10;
                        canvas.drawText("COMPLETE TRIP LEDGER: " + tripName.toUpperCase(), MARGIN, yPosition, titlePaint); yPosition += 25;

                        paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
                        canvas.drawText("Destination: " + destination, MARGIN, yPosition, paint);
                        canvas.drawText("Journey Dates: " + startDate + " to " + endDate, PAGE_WIDTH / 2f, yPosition, paint); yPosition += 20;
                        canvas.drawText("Members: " + members, MARGIN, yPosition, paint); yPosition += 30;

                        // NEW COLUMN PLACEMENT FOR PORTRAIT WIDTH (595px)
                        int[] columnX = {MARGIN, MARGIN + 60, MARGIN + 115, MARGIN + 190, MARGIN + 320, MARGIN + 450};

                        Paint bgPaint = new Paint(); bgPaint.setColor(Color.parseColor("#85022E"));
                        canvas.drawRect(MARGIN, yPosition - 15, PAGE_WIDTH - MARGIN, yPosition + 10, bgPaint);
                        canvas.drawText("Date", columnX[0], yPosition, headerPaint); canvas.drawText("Type", columnX[1], yPosition, headerPaint);
                        canvas.drawText("Member", columnX[2], yPosition, headerPaint); canvas.drawText("Purpose", columnX[3], yPosition, headerPaint);
                        canvas.drawText("Shared With", columnX[4], yPosition, headerPaint); canvas.drawText("Amount", columnX[5], yPosition, headerPaint);
                        yPosition += 25; paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL));

                        for (LedgerEntry entry : entries) {
                            if (yPosition > PAGE_HEIGHT - MARGIN - 30) {
                                pdfDocument.finishPage(page);
                                pageInfo = new android.graphics.pdf.PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pdfDocument.getPages().size() + 1).create();
                                page = pdfDocument.startPage(pageInfo); canvas = page.getCanvas(); yPosition = MARGIN + 20;

                                canvas.drawRect(MARGIN, yPosition - 15, PAGE_WIDTH - MARGIN, yPosition + 10, bgPaint);
                                canvas.drawText("Date", columnX[0], yPosition, headerPaint); canvas.drawText("Type", columnX[1], yPosition, headerPaint);
                                canvas.drawText("Member", columnX[2], yPosition, headerPaint); canvas.drawText("Purpose", columnX[3], yPosition, headerPaint);
                                canvas.drawText("Shared With", columnX[4], yPosition, headerPaint); canvas.drawText("Amount", columnX[5], yPosition, headerPaint);
                                yPosition += 25;
                            }

                            if (entry.isExpense()) totalExpense += entry.getAmount(); else totalPayment += entry.getAmount();

                            canvas.drawText(entry.getDate() != null ? entry.getDate() : "-", columnX[0], yPosition, paint);
                            Paint typePaint = new Paint(paint); typePaint.setColor(entry.isExpense() ? Color.RED : Color.parseColor("#2E7D32"));

                            // Truncate Type to fit tighter space
                            String displayType = entry.isExpense() ? "Exp." : "Pay.";
                            canvas.drawText(displayType, columnX[1], yPosition, typePaint);

                            // Truncate Member
                            String tMember = entry.getPaidBy() != null ? entry.getPaidBy() : "-";
                            String displayMember = tMember.length() > 10 ? tMember.substring(0, 8) + ".." : tMember;
                            canvas.drawText(displayMember, columnX[2], yPosition, paint);

                            // Truncate Purpose
                            String displayPurpose = (entry.getPurpose() != null && entry.getPurpose().length() > 22) ? entry.getPurpose().substring(0, 19) + "..." : (entry.getPurpose() != null ? entry.getPurpose() : "-");
                            canvas.drawText(displayPurpose, columnX[3], yPosition, paint);

                            // Truncate Shared
                            String tShared = entry.getSharedWith();
                            String displayShared = (tShared != null && tShared.length() > 22) ? tShared.substring(0, 19) + "..." : (tShared != null && !tShared.isEmpty() ? tShared : "-");
                            canvas.drawText(displayShared, columnX[4], yPosition, paint);

                            canvas.drawText(String.format(Locale.US, "₹%.2f", entry.getAmount()), columnX[5], yPosition, paint);

                            canvas.drawLine(MARGIN, yPosition + 8, PAGE_WIDTH - MARGIN, yPosition + 8, linePaint);
                            yPosition += 22;
                        }

                        yPosition += 10;
                        if (yPosition > PAGE_HEIGHT - MARGIN - 40) {
                            pdfDocument.finishPage(page);
                            page = pdfDocument.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pdfDocument.getPages().size() + 1).create());
                            canvas = page.getCanvas(); yPosition = MARGIN + 20;
                        }

                        paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)); paint.setTextSize(12f);
                        canvas.drawText(String.format(Locale.US, "TOTAL EXPENSE: ₹%.2f", totalExpense), MARGIN, yPosition, paint);
                        canvas.drawText(String.format(Locale.US, "TOTAL PAYMENT/RECEIVED: ₹%.2f", totalPayment), MARGIN + 250, yPosition, paint);
                        pdfDocument.finishPage(page);

                        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
                            if (outputStream != null) {
                                pdfDocument.writeTo(outputStream);
                                mainHandler.post(() -> Toast.makeText(context, "All Transactions PDF saved successfully!", Toast.LENGTH_LONG).show());
                            }
                        }
                        pdfDocument.close();
                    } catch (Exception e) {
                        mainHandler.post(() -> Toast.makeText(context, "Export Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(context, "Failed to load data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ==========================================
    // 7. SHARE PDF - INDIVIDUAL MEMBER (WHATSAPP/GMAIL)
    // ==========================================
    public void shareIndividualMemberPdf(String tripId, String memberName) {
        Toast.makeText(context, "Preparing PDF for sharing...", Toast.LENGTH_SHORT).show();

        fetchExportData(tripId, new DataFetchCallback() {
            @Override
            public void onDataFetched(DocumentSnapshot tripDoc, List<LedgerEntry> entries) {
                executor.execute(() -> {
                    try {
                        java.io.File pdfFolder = new java.io.File(context.getCacheDir(), "pdfs");
                        if (!pdfFolder.exists() && !pdfFolder.mkdirs()) {
                            mainHandler.post(() -> Toast.makeText(context, "Failed to create cache folder", Toast.LENGTH_SHORT).show());
                            return;
                        }

                        String safeName = memberName.replaceAll("[^a-zA-Z0-9]", "_");
                        java.io.File pdfFile = new java.io.File(pdfFolder, safeName + "_Ledger.pdf");

                        try (java.io.FileOutputStream outputStream = new java.io.FileOutputStream(pdfFile)) {
                            String tripName = tripDoc.contains("tripName") ? tripDoc.getString("tripName") : "Trip Ledger";

                            double totalDebit = 0.0; double totalCredit = 0.0; int transactionCount = 0;

                            for (LedgerEntry entry : entries) {
                                String[] sharedArray = (entry.getSharedWith() != null && !entry.getSharedWith().isEmpty()) ? entry.getSharedWith().split(",\\s*") : new String[0];
                                if (entry.isExpense()) {
                                    if (memberName.equals(entry.getPaidBy())) { totalCredit += entry.getAmount(); transactionCount++; }
                                    else if (isParticipant(memberName, sharedArray)) { totalDebit += (entry.getAmount() / sharedArray.length); transactionCount++; }
                                } else if (memberName.equals(entry.getPaidBy())) { totalCredit += entry.getAmount(); transactionCount++; }
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
                            String generatedOn = sdf.format(new java.util.Date()); String reportDate = dateOnly.format(new java.util.Date());

                            int pageNumber = 1;
                            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                            PdfDocument.Page page = document.startPage(pageInfo); Canvas canvas = page.getCanvas();

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

                            for (LedgerEntry entry : entries) {
                                String[] sharedArray = (entry.getSharedWith() != null && !entry.getSharedWith().isEmpty()) ? entry.getSharedWith().split(",\\s*") : new String[0];
                                double debit = 0.0, credit = 0.0; boolean involvesMember = false;

                                if (entry.isExpense()) {
                                    if (memberName.equals(entry.getPaidBy())) { credit = entry.getAmount(); involvesMember = true; }
                                    if (isParticipant(memberName, sharedArray)) { debit = entry.getAmount() / sharedArray.length; involvesMember = true; }
                                } else if (memberName.equals(entry.getPaidBy())) { credit = entry.getAmount(); involvesMember = true; }

                                if (involvesMember) {
                                    if (yPos > pageHeight - 120) {
                                        drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight);
                                        document.finishPage(page); pageNumber++; pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                                        page = document.startPage(pageInfo); canvas = page.getCanvas();
                                        yPos = drawPageHeader(canvas, pageWidth, margin, tripName, memberName, generatedOn, reportDate, paintMainTitle, paintSubTitle, paintTextBold, paintTextNormal, paintTextRight);
                                        canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintTableBg); canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintBorder);
                                        canvas.drawText("Date", xDate, yPos + 17, paintTextBold); canvas.drawText("Description", xDesc, yPos + 17, paintTextBold);
                                        canvas.drawText("Debit (₹)", xDebit, yPos + 17, paintTextRight); canvas.drawText("Credit (₹)", xCredit, yPos + 17, paintTextRight);
                                        yPos += 25;
                                    }
                                    String safeDate = (entry.getDate() != null) ? entry.getDate() : "N/A";
                                    String safePurpose = (entry.getPurpose() != null) ? entry.getPurpose() : ""; if (safePurpose.length() > 40) safePurpose = safePurpose.substring(0, 37) + "...";
                                    String debitStr = debit > 0 ? String.format(Locale.US, "%,.2f", debit) : ""; String creditStr = credit > 0 ? String.format(Locale.US, "%,.2f", credit) : "";
                                    yPos += 20; canvas.drawText(safeDate, xDate, yPos, paintTextNormal); canvas.drawText(safePurpose, xDesc, yPos, paintTextNormal);
                                    canvas.drawText(debitStr, xDebit, yPos, paintTextRight); canvas.drawText(creditStr, xCredit, yPos, paintTextRight);
                                    yPos += 10; canvas.drawLine(margin, yPos, pageWidth - margin, yPos, paintDottedLine);
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

                            yPos += 85; Paint endPaint = new Paint(); endPaint.setTextSize(11f); endPaint.setFakeBoldText(true); endPaint.setColor(Color.parseColor("#85022E")); endPaint.setTextAlign(Paint.Align.CENTER);
                            canvas.drawText("End of the pdf. Total page: " + pageNumber + " page", pageWidth / 2f, yPos, endPaint);

                            drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight);
                            document.finishPage(page); document.writeTo(outputStream); document.close();
                        }

                        mainHandler.post(() -> {
                            try {
                                android.net.Uri pdfUri = androidx.core.content.FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName() + ".fileprovider", pdfFile);
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
            @Override
            public void onError(Exception e) {
                Toast.makeText(context, "Failed to load data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ==========================================
    // 8. SHARE PDF - MASTER (ALL MEMBERS)
    // ==========================================
    public void shareMasterPdf(String tripId) {
        Toast.makeText(context, "Preparing Master PDF for sharing...", Toast.LENGTH_SHORT).show();

        fetchExportData(tripId, new DataFetchCallback() {
            @Override
            public void onDataFetched(DocumentSnapshot tripDoc, List<LedgerEntry> entries) {
                executor.execute(() -> {
                    try {
                        java.io.File pdfFolder = new java.io.File(context.getCacheDir(), "pdfs");
                        if (!pdfFolder.exists() && !pdfFolder.mkdirs()) {
                            mainHandler.post(() -> Toast.makeText(context, "Failed to create cache folder", Toast.LENGTH_SHORT).show());
                            return;
                        }

                        String tripName = tripDoc.contains("tripName") ? tripDoc.getString("tripName") : "Trip Ledger";
                        String safeName = tripName.replaceAll("[^a-zA-Z0-9]", "_");
                        java.io.File pdfFile = new java.io.File(pdfFolder, safeName + "_Master_Ledger.pdf");

                        try (java.io.FileOutputStream outputStream = new java.io.FileOutputStream(pdfFile)) {
                            ArrayList<String> allMembers = new ArrayList<>();

                            String activeRaw = tripDoc.getString("members");
                            String inactiveRaw = tripDoc.getString("inactiveMembers");
                            if(activeRaw != null) for(String m : activeRaw.split(",")) if(!m.trim().isEmpty() && !allMembers.contains(m.trim())) allMembers.add(m.trim());
                            if(inactiveRaw != null) for(String m : inactiveRaw.split(",")) if(!m.trim().isEmpty() && !allMembers.contains(m.trim())) allMembers.add(m.trim());
                            for (LedgerEntry e : entries) {
                                if(e.getPaidBy() != null && !e.getPaidBy().equalsIgnoreCase("Fund") && !allMembers.contains(e.getPaidBy())) allMembers.add(e.getPaidBy());
                                if(e.getSharedWith() != null) for(String m : e.getSharedWith().split(",\\s*")) if(!m.trim().isEmpty() && !allMembers.contains(m.trim())) allMembers.add(m.trim());
                            }

                            PdfDocument document = new android.graphics.pdf.PdfDocument();
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
                            String generatedOn = sdf.format(new java.util.Date()); String reportDate = dateOnly.format(new java.util.Date());
                            int pageNumber = 1;
                            android.graphics.pdf.PdfDocument.PageInfo pageInfo = new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                            android.graphics.pdf.PdfDocument.Page page = document.startPage(pageInfo); Canvas canvas = page.getCanvas();

                            for (int i = 0; i < allMembers.size(); i++) {
                                String memberName = allMembers.get(i);
                                if (i > 0) {
                                    drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight);
                                    document.finishPage(page); pageNumber++;
                                    pageInfo = new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                                    page = document.startPage(pageInfo); canvas = page.getCanvas();
                                }

                                double totalDebit = 0.0; double totalCredit = 0.0; int transactionCount = 0;
                                for(LedgerEntry entry : entries) {
                                    String[] sharedArray = (entry.getSharedWith() != null && !entry.getSharedWith().isEmpty()) ? entry.getSharedWith().split(",\\s*") : new String[0];
                                    if (entry.isExpense()) {
                                        if (memberName.equals(entry.getPaidBy())) { totalCredit += entry.getAmount(); transactionCount++; }
                                        else if (isParticipant(memberName, sharedArray)) { totalDebit += (entry.getAmount() / sharedArray.length); transactionCount++; }
                                    } else if (memberName.equals(entry.getPaidBy())) { totalCredit += entry.getAmount(); transactionCount++; }
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

                                for (LedgerEntry entry : entries) {
                                    String[] sharedArray = (entry.getSharedWith() != null && !entry.getSharedWith().isEmpty()) ? entry.getSharedWith().split(",\\s*") : new String[0];
                                    double debit = 0.0, credit = 0.0; boolean involvesMember = false;

                                    if (entry.isExpense()) {
                                        if (memberName.equals(entry.getPaidBy())) { credit = entry.getAmount(); involvesMember = true; }
                                        if (isParticipant(memberName, sharedArray)) { debit = entry.getAmount() / sharedArray.length; involvesMember = true; }
                                    } else if (memberName.equals(entry.getPaidBy())) { credit = entry.getAmount(); involvesMember = true; }

                                    if (involvesMember) {
                                        if (yPos > pageHeight - 120) {
                                            drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight);
                                            document.finishPage(page); pageNumber++;
                                            pageInfo = new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                                            page = document.startPage(pageInfo); canvas = page.getCanvas();
                                            yPos = drawPageHeader(canvas, pageWidth, margin, tripName, memberName, generatedOn, reportDate, paintMainTitle, paintSubTitle, paintTextBold, paintTextNormal, paintTextRight);
                                            canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintTableBg); canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintBorder);
                                            canvas.drawText("Date", xDate, yPos + 17, paintTextBold); canvas.drawText("Description", xDesc, yPos + 17, paintTextBold);
                                            canvas.drawText("Debit (₹)", xDebit, yPos + 17, paintTextRight); canvas.drawText("Credit (₹)", xCredit, yPos + 17, paintTextRight);
                                            yPos += 25;
                                        }
                                        String safeDate = (entry.getDate() != null) ? entry.getDate() : "N/A";
                                        String safePurpose = (entry.getPurpose() != null) ? entry.getPurpose() : ""; if (safePurpose.length() > 40) safePurpose = safePurpose.substring(0, 37) + "...";
                                        String debitStr = debit > 0 ? String.format(Locale.US, "%,.2f", debit) : ""; String creditStr = credit > 0 ? String.format(Locale.US, "%,.2f", credit) : "";
                                        yPos += 20; canvas.drawText(safeDate, xDate, yPos, paintTextNormal); canvas.drawText(safePurpose, xDesc, yPos, paintTextNormal);
                                        canvas.drawText(debitStr, xDebit, yPos, paintTextRight); canvas.drawText(creditStr, xCredit, yPos, paintTextRight);
                                        yPos += 10; canvas.drawLine(margin, yPos, pageWidth - margin, yPos, paintDottedLine);
                                    }
                                }
                                yPos += 5; canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 25, paintBorder);
                                canvas.drawText("TOTALS", xDesc, yPos + 17, paintTextBold); canvas.drawText(String.format(Locale.US, "₹%,.2f", totalDebit), xDebit, yPos + 17, paintTextRight); canvas.drawText(String.format(Locale.US, "₹%,.2f", totalCredit), xCredit, yPos + 17, paintTextRight);

                                yPos += 45;
                                if (yPos > pageHeight - 140) {
                                    drawFooter(canvas, pageWidth, pageHeight, margin, pageNumber, paintTextNormal, paintTextRight); document.finishPage(page); pageNumber++;
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
                            document.finishPage(page); document.writeTo(outputStream); document.close();
                        }

                        mainHandler.post(() -> {
                            try {
                                android.net.Uri pdfUri = androidx.core.content.FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName() + ".fileprovider", pdfFile);
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

            @Override
            public void onError(Exception e) {
                Toast.makeText(context, "Failed to load data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- HELPER: Draws the Repeating Header ---
    private int drawPageHeader(Canvas canvas, int pageWidth, int margin, String tripName, String memberName, String generatedOn, String reportDate, Paint paintMainTitle, Paint paintSubTitle, Paint paintTextBold, Paint paintTextNormal, Paint paintTextRight) {
        int yPos = margin + 20;

        canvas.drawText("TRIP EXPENSE MANAGER", pageWidth / 2f, yPos, paintMainTitle);
        yPos += 20;
        canvas.drawText("— Individual Member Ledger —", pageWidth / 2f, yPos, paintSubTitle);

        yPos += 35;

        canvas.drawText("Trip Name : ", margin, yPos, paintTextNormal);
        float tripLabelWidth = paintTextNormal.measureText("Trip Name : ");
        canvas.drawText(tripName, margin + tripLabelWidth, yPos, paintTextBold);
        canvas.drawText("Generated On : " + generatedOn, pageWidth - margin, yPos, paintTextRight);

        yPos += 15;
        canvas.drawText("Member      : ", margin, yPos, paintTextNormal);
        float memberLabelWidth = paintTextNormal.measureText("Member      : ");
        canvas.drawText(memberName, margin + memberLabelWidth, yPos, paintTextBold);
        canvas.drawText("Report Date   : " + reportDate, pageWidth - margin, yPos, paintTextRight);

        return yPos + 25;
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