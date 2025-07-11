package com.labactivity.studysync;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

public class BrowseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browse);

        View rootView = findViewById(R.id.browse_layout);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            return insets;
        });

        MaterialButton toggleFiltersButton = findViewById(R.id.toggleFiltersButton);
        LinearLayout filterButtonsContainer = findViewById(R.id.filterButtonsContainer);

        // 👇 Hide filters initially
        filterButtonsContainer.setVisibility(View.GONE);
        toggleFiltersButton.setText(R.string.show_filter);
        toggleFiltersButton.setIconResource(R.drawable.drop_down_icon);

        toggleFiltersButton.setOnClickListener(v -> {
            if (filterButtonsContainer.getVisibility() == View.GONE) {
                filterButtonsContainer.setVisibility(View.VISIBLE);
                toggleFiltersButton.setText(R.string.hide_filter);
                toggleFiltersButton.setIconResource(R.drawable.arrow_up); // up icon
            } else {
                filterButtonsContainer.setVisibility(View.GONE);
                toggleFiltersButton.setText(R.string.show_filter);
                toggleFiltersButton.setIconResource(R.drawable.drop_down_icon);
            }
        });
    }
}