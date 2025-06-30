package com.labactivity.studysync;

import android.os.Bundle;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.github.chrisbanes.photoview.PhotoView;

public class ImageViewerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        String imageUrl = getIntent().getStringExtra("imageUrl");

        PhotoView photoView = findViewById(R.id.fullscreenImage);
        ImageButton closeButton = findViewById(R.id.closeButton);

        Glide.with(this)
                .load(imageUrl)
                .into(photoView);

        closeButton.setOnClickListener(v -> finish());
    }
}
