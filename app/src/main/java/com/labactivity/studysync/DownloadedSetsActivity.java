package com.labactivity.studysync;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.labactivity.studysync.adapters.DownloadedSetsAdapter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DownloadedSetsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView noSetsText;
    private ImageView backButton;
    private DownloadedSetsAdapter adapter;
    private List<Map<String, Object>> downloadedSets;

    private boolean wasOffline = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloaded_sets);

        recyclerView = findViewById(R.id.recyclerView);
        noSetsText = findViewById(R.id.noSetsText);
        backButton = findViewById(R.id.backButton);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        downloadedSets = loadDownloadedSets();

        if (downloadedSets.isEmpty()) {
            noSetsText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            noSetsText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            adapter = new DownloadedSetsAdapter(downloadedSets, this);
            recyclerView.setAdapter(adapter);
        }

        backButton.setOnClickListener(v -> onBackPressed());

        wasOffline = !isInternetAvailable();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean currentlyOnline = isInternetAvailable();

        if (wasOffline && currentlyOnline) {
            Toast.makeText(this, "You're back online!", Toast.LENGTH_SHORT).show();
        } else if (!wasOffline && !currentlyOnline) {
            Toast.makeText(this, "You're offline.", Toast.LENGTH_SHORT).show();
        }

        wasOffline = !currentlyOnline;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        Intent intent;

        if (user != null) {
            intent = new Intent(this, MainActivity.class);
        } else {
            intent = new Intent(this, LoginActivity.class);
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private boolean isInternetAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            return netInfo != null && netInfo.isConnected();
        }
        return false;
    }

    private List<Map<String, Object>> loadDownloadedSets() {
        List<Map<String, Object>> sets = new ArrayList<>();
        File dir = getFilesDir();
        File[] files = dir.listFiles((dir1, name) -> name.startsWith("set_") && name.endsWith(".json"));

        if (files != null) {
            for (File file : files) {
                try {
                    FileInputStream fis = new FileInputStream(file);
                    byte[] data = new byte[(int) file.length()];
                    fis.read(data);
                    fis.close();

                    String json = new String(data);
                    Type type = new TypeToken<Map<String, Object>>() {}.getType();
                    Map<String, Object> setMap = new Gson().fromJson(json, type);

                    setMap.put("fileName", file.getName());

                    if (!setMap.containsKey("type")) {
                        if (setMap.containsKey("questions")) {
                            setMap.put("type", "quiz");
                        } else if (setMap.containsKey("terms")) {
                            setMap.put("type", "flashcard");
                        }
                    }

                    sets.add(setMap);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return sets;
    }
}
