package com.example.tripexpensemanager;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

public class ProfileActivity extends AppCompatActivity {

    private ImageView imgProfile;
    private TextView txtName, txtEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile); // Ensure this matches your filename

        // Initialize Views
        imgProfile = findViewById(R.id.img_profile_pic);
        txtName = findViewById(R.id.txt_profile_name);
        txtEmail = findViewById(R.id.txt_profile_email);

        loadProfileData();
    }
    @SuppressWarnings("deprecation")
    private void loadProfileData() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);

        if (account != null) {
            // Set Name and Email
            txtName.setText(account.getDisplayName());
            txtEmail.setText(account.getEmail());

            if (account.getPhotoUrl() != null) {
                // Fix low resolution by replacing s96-c with s400-c
                String originalUrl = account.getPhotoUrl().toString();
                String highResUrl = originalUrl.replace("s96-c", "s400-c");

                Glide.with(this)
                        .load(highResUrl)
                        .placeholder(R.drawable.ic_profile_placeholder) // Add a default drawable here
                        .circleCrop()
                        .into(imgProfile);
            }
        } else {
            // Debug: If this runs, it means no account is signed in
            android.util.Log.e("ProfileActivity", "No Google account found!");
            // Optional: finish(); // Remove this if you don't want the app to close
        }
    }
}