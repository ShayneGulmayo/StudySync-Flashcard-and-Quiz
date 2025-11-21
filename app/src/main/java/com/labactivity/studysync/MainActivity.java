package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import android.text.TextUtils;
import android.view.View;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;
import com.labactivity.studysync.fragments.ChatFragment;
import com.labactivity.studysync.fragments.HomeFragment;
import com.labactivity.studysync.fragments.SetFragment;
import com.labactivity.studysync.fragments.UserProfileFragment;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private View mainContentLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        setContentView(R.layout.activity_main);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        mainContentLayout = findViewById(R.id.fragment_container);
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (mainContentLayout != null) {
            mainContentLayout.setVisibility(View.GONE);
        }
        if (bottomNavigationView != null) {
            bottomNavigationView.setVisibility(View.GONE);
        }

        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commit();
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
        }

        db.collection("users").document(currentUser.getUid()).get()
                .addOnSuccessListener(document -> {
                    String firstName = document.getString("firstName");
                    String lastName = document.getString("lastName");
                    String username = document.getString("username");

                    if (TextUtils.isEmpty(firstName) || TextUtils.isEmpty(lastName) || TextUtils.isEmpty(username)) {
                        startActivity(new Intent(MainActivity.this, UserSetUpProfileActivity.class));
                        finish();
                    } else {
                        loadMainActivityUI();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("MainActivity", "Error checking profile completion: " + e.getMessage());
                    startActivity(new Intent(MainActivity.this, UserSetUpProfileActivity.class));
                    finish();
                });
    }

    public void setBottomNavSelection(int itemId) {
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(itemId);
        }
    }

    private void loadMainActivityUI() {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        if (bottomNavigationView == null) {
            Log.e("MainActivity", "BottomNavigationView is null in loadMainActivityUI.");
            return;
        }

        if (mainContentLayout != null) {
            mainContentLayout.setVisibility(View.VISIBLE);
        }
        bottomNavigationView.setVisibility(View.VISIBLE);

        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };

        int[] colors = new int[]{
                Color.parseColor("#00BF63"),
                getResources().getColor(R.color.text_gray)
        };

        ColorStateList colorStateList = new ColorStateList(states, colors);
        bottomNavigationView.setItemIconTintList(colorStateList);
        bottomNavigationView.setItemTextColor(colorStateList);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int id = item.getItemId();

            if (id == R.id.nav_profile) {
                selectedFragment = new UserProfileFragment();
            } else if (id == R.id.nav_home) {
                selectedFragment = new HomeFragment();
            } else if (id == R.id.nav_sets) {
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

        bottomNavigationView.setSelectedItemId(R.id.nav_home);

        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w("FCM", "Fetching FCM registration token failed", task.getException());
                        return;
                    }

                    String token = task.getResult();
                    Log.d("FCM", "FCM Token: " + token);

                    FirebaseUser currentUser = mAuth.getCurrentUser();
                    if (currentUser != null) {
                        FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(currentUser.getUid())
                                .update("fcmToken", token)
                                .addOnSuccessListener(aVoid -> Log.d("FCM", "Token saved successfully"))
                                .addOnFailureListener(e -> Log.w("FCM", "Failed to save token", e));
                    }
                });
        if (getIntent().getExtras() != null) {
            String chatRoomId = getIntent().getStringExtra("chatRoomId");
            if (chatRoomId != null && !chatRoomId.isEmpty()) {
                Intent chatIntent = new Intent(this, ChatRoomActivity.class);
                chatIntent.putExtra("chatRoomId", chatRoomId);
                startActivity(chatIntent);
            }
        }
    }
}