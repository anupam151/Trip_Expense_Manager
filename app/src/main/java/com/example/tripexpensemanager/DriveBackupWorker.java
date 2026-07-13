package com.example.tripexpensemanager;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;

import java.io.FileInputStream;
import java.util.Collections;
import androidx.annotation.Keep;
@Keep
@SuppressWarnings({"unused", "FieldCanBeLocal","deprecation"}) // This tells the IDE to ignore the unused warnings
public class DriveBackupWorker extends Worker {

    public DriveBackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();

        // --- IMPORTANT: Check if file exists to prevent crash ---
        java.io.File dbFile = context.getDatabasePath("TripManager.db");
        if (!dbFile.exists()) {
            Log.w("AutoSync", "Backup skipped: Database file does not exist.");
            return Result.success(); // Not a failure, just nothing to back up
        }

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        if (account == null) {
            Log.e("AutoSync", "Backup aborted: User not signed in.");
            return Result.failure();
        }

        try {
            // ... (Rest of your code remains the same) ...
            GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                    context, Collections.singletonList("https://www.googleapis.com/auth/drive.file"));
            credential.setSelectedAccount(account.getAccount());

            Drive driveService = new Drive.Builder(
                    new NetHttpTransport(),
                    new GsonFactory(),
                    credential)
                    .setApplicationName("TripExpenseManager")
                    .build();

            GoogleDriveService driveUploader = new GoogleDriveService(driveService);
            FileInputStream fis = new FileInputStream(dbFile);

            driveUploader.uploadDatabase(fis, "TripManager_Backup.db");

            Log.i("AutoSync", "Background backup completed successfully!");
            return Result.success();

        } catch (Exception e) {
            Log.e("AutoSync", "Background backup failed. Will retry.", e);
            return Result.retry();
        }
    }
}