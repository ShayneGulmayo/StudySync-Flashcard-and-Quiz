package com.labactivity.studysync;

import android.os.Bundle;
import android.graphics.Color;
import android.content.res.ColorStateList;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.labactivity.studysync.fragments.ChatFragment;
import com.labactivity.studysync.fragments.SetFragment;
import com.labactivity.studysync.fragments.UserProfileFragment;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        setContentView(R.layout.activity_main);

        bottomNavigationView = findViewById(R.id.bottom_navigation);


        int[][] states = new int[][] {
                new int[] { android.R.attr.state_checked },
                new int[] { -android.R.attr.state_checked }
        };

        int[] colors = new int[] {
                Color.parseColor("#00BF63"), // active
                getResources().getColor(R.color.text_gray) // inactive
        };

        ColorStateList colorStateList = new ColorStateList(states, colors);
        bottomNavigationView.setItemIconTintList(colorStateList);
        bottomNavigationView.setItemTextColor(colorStateList);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new SetFragment())
                    .commit();
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
        }

        bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int id = item.getItemId();

            if (id == R.id.nav_profile) {
                selectedFragment = new UserProfileFragment();
            } else if (id == R.id.nav_home) {
                selectedFragment = new SetFragment();
            } else if (id == R.id.nav_chat) {
                selectedFragment = new ChatFragment();
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
                return true;
            }
            return false;
        });
    }
}
