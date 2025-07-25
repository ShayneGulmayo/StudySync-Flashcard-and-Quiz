package com.labactivity.studysync;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.TextView;
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
    private TextView emptyTextView;
    private ImageView backBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        recyclerView = findViewById(R.id.recyclerView);
        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        backBtn = findViewById(R.id.backButton);

        adapter = new NotificationsAdapter(this, notificationList, db, currentUserId);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        emptyTextView = findViewById(R.id.emptyTextView);

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

                    if (value != null && !value.isEmpty()) {
                        for (DocumentSnapshot doc : value) {
                            NotificationModel notification = doc.toObject(NotificationModel.class);
                            if (notification != null) {
                                notification.setNotificationId(doc.getId());
                                notificationList.add(notification);
                            }
                        }
                    }

                    adapter.notifyDataSetChanged();

                    // Toggle visibility based on list state
                    if (notificationList.isEmpty()) {
                        recyclerView.setVisibility(View.GONE);
                        findViewById(R.id.emptyTextView).setVisibility(View.VISIBLE);
                    } else {
                        recyclerView.setVisibility(View.VISIBLE);
                        findViewById(R.id.emptyTextView).setVisibility(View.GONE);
                    }
                });
    }

}
