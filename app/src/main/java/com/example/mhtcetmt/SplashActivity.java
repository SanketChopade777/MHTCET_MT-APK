package com.example.mhtcetmt;


import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView gifView = findViewById(R.id.splashGif);

        // Load GIF properly
        Glide.with(this)
                .asGif()
                .load(R.drawable.splash)
                .into(gifView);

        // Wait for GIF to play then go to MainActivity
        new Handler().postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        }, 3000); // 3 seconds splash
    }
}