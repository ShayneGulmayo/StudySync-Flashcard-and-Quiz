package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import com.labactivity.studysync.adapters.NotificationsAdapter;
import com.labactivity.studysync.models.NotificationModel;

import java.util.ArrayList;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private NotificationsAdapter adapter;
    private List<NotificationModel> notificationList = new ArrayList<>();
    private FirebaseFirestore db;
    private String currentUserId;

    private ImageView backBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications); // Make sure this layout has a RecyclerView with id recyclerView

        recyclerView = findViewById(R.id.recyclerView);
        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        backBtn = findViewById(R.id.backButton);

        adapter = new NotificationsAdapter(this, notificationList, db, currentUserId);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        backBtn.setOnClickListener(view -> finish());


        loadNotifications();
    }

    private void loadNotifications() {
        db.collection("users").document(currentUserId)
                .collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Failed to load notifications", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    notificationList.clear();
                    if (value != null) {
                        for (DocumentSnapshot doc : value) {
                            NotificationModel notification = doc.toObject(NotificationModel.class);
                            notification.setNotificationId(doc.getId());
                            notificationList.add(notification);
                        }
                        adapter.notifyDataSetChanged();
                    }
                });
    }
}
