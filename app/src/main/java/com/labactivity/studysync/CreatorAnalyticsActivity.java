package com.labactivity.studysync;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public class CreatorAnalyticsActivity extends AppCompatActivity {

    private FirebaseFirestore db;

    private ImageView backBtn;
    private ProgressBar statsProgressBar;
    private TextView progressPercentage, tvTotalSessions;
    private TextView tvInitialAvg, tvLatestAvg, tvImprovementMsg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_creator_analytics);

        db = FirebaseFirestore.getInstance();

        // Initialize UI
        statsProgressBar = findViewById(R.id.stats_progressbar);
        progressPercentage = findViewById(R.id.progress_percentage);
        tvTotalSessions = findViewById(R.id.tv_total_sessions);
        tvInitialAvg = findViewById(R.id.tv_initial_avg);
        tvLatestAvg = findViewById(R.id.tv_latest_avg);
        tvImprovementMsg = findViewById(R.id.tv_improvement_msg);
        backBtn = findViewById(R.id.backBtn);

        backBtn.setOnClickListener(v -> finish());

        // 1. Load total global usage stats
        loadGlobalAppStats();
        // 2. Calculate learning improvement across the whole platform
        calculateGlobalEffectiveness();
    }

    /**
     * Aggregates stats from all creator_analytics docs to show total app usage.
     */
    private void loadGlobalAppStats() {
        db.collection("creator_analytics")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    long globalTotalSessions = 0;
                    long globalTotalSum = 0;

                    for (QueryDocumentSnapshot doc : snapshots) {
                        globalTotalSessions += doc.getLong("totalSessions") != null ? doc.getLong("totalSessions") : 0;
                        globalTotalSum += doc.getLong("totalProgressSum") != null ? doc.getLong("totalProgressSum") : 0;
                    }

                    if (globalTotalSessions > 0) {
                        int avgMastery = (int) (globalTotalSum / globalTotalSessions);
                        statsProgressBar.setProgress(avgMastery);
                        progressPercentage.setText(avgMastery + "%");
                        tvTotalSessions.setText("Total Global Sessions: " + globalTotalSessions);
                    }
                });
    }

    /**
     * Proves app effectiveness by comparing the first try vs last try
     * for every user on every set they have ever studied.
     */
    private void calculateGlobalEffectiveness() {
        // Query the entire collection group without any owner filters
        db.collectionGroup("performance_history")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {

                    // Key format: "userId_setId" to track progress on a specific set
                    Map<String, Integer> firstAttempts = new HashMap<>();
                    Map<String, Integer> latestAttempts = new HashMap<>();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        try {
                            int score = doc.getLong("progress").intValue();
                            String setId = doc.getString("setId");
                            String userId = doc.getReference().getParent().getParent().getId();

                            // Unique key for a user's journey through a specific set
                            String journeyKey = userId + "_" + setId;

                            // If this is the first time we see this user studying this set
                            if (!firstAttempts.containsKey(journeyKey)) {
                                firstAttempts.put(journeyKey, score);
                            }
                            // Keep updating the latest score for this user/set combo
                            latestAttempts.put(journeyKey, score);

                        } catch (Exception e) {
                            Log.e("Analytics", "Error: " + e.getMessage());
                        }
                    }
                    displayEffectivenessProof(firstAttempts, latestAttempts);
                })
                .addOnFailureListener(e -> {
                    Log.e("FIRESTORE_ERROR", e.getMessage());
                    Toast.makeText(this, "Check Logcat for Index Link", Toast.LENGTH_LONG).show();
                });
    }

    private void displayEffectivenessProof(Map<String, Integer> firsts, Map<String, Integer> lasts) {
        int totalJourneys = firsts.size(); // Total unique User+Set combinations
        if (totalJourneys == 0) return;

        double sumInitial = 0;
        double sumLatest = 0;

        for (String key : firsts.keySet()) {
            sumInitial += firsts.get(key);
            sumLatest += lasts.get(key);
        }

        int globalAvgStart = (int) (sumInitial / totalJourneys);
        int globalAvgNow = (int) (sumLatest / totalJourneys);
        int improvement = globalAvgNow - globalAvgStart;

        tvInitialAvg.setText(globalAvgStart + "%");
        tvLatestAvg.setText(globalAvgNow + "%");

        String proof = String.format("Across %d total study journeys, users have improved their scores by %d%% on average using this platform.",
                totalJourneys, improvement);
        tvImprovementMsg.setText(proof);
    }
}