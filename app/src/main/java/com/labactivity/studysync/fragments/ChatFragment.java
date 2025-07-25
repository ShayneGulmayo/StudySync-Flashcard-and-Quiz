package com.labactivity.studysync.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;
import com.labactivity.studysync.models.ChatRoom;
import com.labactivity.studysync.adapters.ChatRoomAdapter;
import com.labactivity.studysync.CreateChatRoomActivity;
import com.labactivity.studysync.R;

import java.util.*;

public class ChatFragment extends Fragment {

    private ImageView addButton;

    private boolean hasLoadedOnce = false;

    private SearchView searchView;
    private RecyclerView chatRecyclerView;
    private ProgressBar progressBar;

    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private ChatRoomAdapter adapter;
    private final List<ChatRoom> chatRoomList = new ArrayList<>();
    private final Map<String, Date> lastOpenedMap = new HashMap<>();

    public ChatFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(com.labactivity.studysync.R.layout.fragment_chat, container, false);

        addButton = view.findViewById(com.labactivity.studysync.R.id.add_button);
        searchView = view.findViewById(com.labactivity.studysync.R.id.search_set);
        progressBar = view.findViewById(com.labactivity.studysync.R.id.progress_bar);
        chatRecyclerView = view.findViewById(R.id.recycler_flashcards);

        EditText searchEditText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
        View searchPlate = searchView.findViewById(androidx.appcompat.R.id.search_plate);
        searchEditText.setBackground(null);
        searchPlate.setBackground(null);

        chatRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ChatRoomAdapter(requireContext(), chatRoomList, getCurrentUserId());
        chatRecyclerView.setAdapter(adapter);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        addButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), CreateChatRoomActivity.class);
            startActivity(intent);
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) {
                adapter.filterByName(query);
                return true;
            }

            @Override public boolean onQueryTextChange(String newText) {
                adapter.filterByName(newText);
                return true;
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!hasLoadedOnce) {
            progressBar.setVisibility(View.VISIBLE);
            fetchUserChatTimestamps(() -> {
                loadChatRooms();
                hasLoadedOnce = true;
            });
        } else {
            fetchUserChatTimestamps(this::loadChatRooms);
        }
    }
    private void fetchUserChatTimestamps(Runnable onReady) {
        db.collection("user_chat_status")
                .document(getCurrentUserId())
                .get()
                .addOnSuccessListener(snapshot -> {
                    lastOpenedMap.clear();
                    if (snapshot.exists()) {
                        for (String roomId : snapshot.getData().keySet()) {
                            Timestamp ts = snapshot.getTimestamp(roomId);
                            if (ts != null) lastOpenedMap.put(roomId, ts.toDate());
                        }
                    }
                    onReady.run();
                });
    }

    private void loadChatRooms() {
        chatRoomList.clear();
        progressBar.setVisibility(View.VISIBLE);

        List<Task<QuerySnapshot>> tasks = new ArrayList<>();

        tasks.add(db.collection("chat_rooms")
                .whereArrayContains("members", getCurrentUserId())
                .get());

        Tasks.whenAllSuccess(tasks)
                .addOnSuccessListener(results -> {
                    Set<String> seenRoomIds = new HashSet<>();
                    List<ChatRoom> tempRooms = new ArrayList<>();
                    List<Task<QuerySnapshot>> messageTasks = new ArrayList<>();

                    for (Object result : results) {
                        if (result instanceof QuerySnapshot) {
                            for (DocumentSnapshot doc : ((QuerySnapshot) result).getDocuments()) {
                                ChatRoom room = doc.toObject(ChatRoom.class);
                                if (room == null) continue;

                                room.setId(doc.getId());
                                if (seenRoomIds.contains(room.getId())) continue;
                                seenRoomIds.add(room.getId());

                                tempRooms.add(room);

                                Task<QuerySnapshot> messageTask = doc.getReference()
                                        .collection("messages")
                                        .orderBy("timestamp", Query.Direction.DESCENDING)
                                        .limit(1)
                                        .get()
                                        .addOnSuccessListener(msgSnap -> {
                                            if (!msgSnap.isEmpty()) {
                                                DocumentSnapshot msg = msgSnap.getDocuments().get(0);
                                                room.setLastMessage(msg.getString("text"));
                                                room.setLastMessageSender(msg.getString("senderName"));
                                                room.setLastMessageSenderId(msg.getString("senderId"));
                                                Timestamp ts = msg.getTimestamp("timestamp");
                                                if (ts != null) room.setLastMessageTimestamp(ts.toDate());
                                            }
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e("ChatFragment", "Failed to load message: " + e.getMessage());
                                        });

                                messageTasks.add(messageTask);
                            }
                        }
                    }

                    Tasks.whenAllComplete(messageTasks)
                            .addOnCompleteListener(task -> {
                                for (ChatRoom room : tempRooms) {
                                    updateChatRoom(room);
                                }
                                sortAndDisplay();
                                progressBar.setVisibility(View.GONE);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e("ChatFragment", "Failed to load chat rooms: " + e.getMessage());
                    progressBar.setVisibility(View.GONE);
                });
    }

    private void checkIfAllRoomsProcessed(int processed, int total) {
        if (processed >= total) {
            sortAndDisplay();
            progressBar.setVisibility(View.GONE);
        }
    }

    private void updateChatRoom(ChatRoom updatedRoom) {
        Date lastOpened = lastOpenedMap.get(updatedRoom.getId());
        Date lastMsgTime = updatedRoom.getLastMessageTimestamp();

        boolean isUnread = lastMsgTime != null
                && (lastOpened == null || lastMsgTime.after(lastOpened))
                && !updatedRoom.getLastMessageSenderId().equals(getCurrentUserId());
        updatedRoom.setUnread(isUnread);

        boolean found = false;
        for (int i = 0; i < chatRoomList.size(); i++) {
            if (chatRoomList.get(i).getId().equals(updatedRoom.getId())) {
                chatRoomList.set(i, updatedRoom);
                found = true;
                break;
            }
        }

        if (!found) chatRoomList.add(updatedRoom);
    }

    private void sortAndDisplay() {
        chatRoomList.sort((a, b) -> {
            Date aTime = a.getLastMessageTimestamp() != null ? a.getLastMessageTimestamp() : new Date(0);
            Date bTime = b.getLastMessageTimestamp() != null ? b.getLastMessageTimestamp() : new Date(0);
            return bTime.compareTo(aTime);
        });

        adapter.setUserLastOpenedMap(lastOpenedMap);
        adapter.updateData(chatRoomList);
    }

    private String getCurrentUserId() {
        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }
}
