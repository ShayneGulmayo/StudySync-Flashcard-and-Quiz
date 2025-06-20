package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;

import java.util.*;

public class ChatFragment extends Fragment {

    private ImageView addButton;
    private SearchView searchView;
    private RecyclerView chatRecyclerView;

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
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        addButton = view.findViewById(R.id.add_button);
        searchView = view.findViewById(R.id.search_set);
        chatRecyclerView = view.findViewById(R.id.recycler_flashcards);

        chatRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        adapter = new ChatRoomAdapter(requireContext(), chatRoomList, currentUser.getUid());
        chatRecyclerView.setAdapter(adapter);

        addButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), CreateChatRoomActivity.class);
            startActivity(intent);
        });

        fetchUserChatTimestamps(this::loadChatRooms);

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
        fetchUserChatTimestamps(this::loadChatRooms);
    }

    private void fetchUserChatTimestamps(Runnable onReady) {
        db.collection("user_chat_status")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        for (String roomId : documentSnapshot.getData().keySet()) {
                            Timestamp ts = documentSnapshot.getTimestamp(roomId);
                            if (ts != null) {
                                lastOpenedMap.put(roomId, ts.toDate());
                            }
                        }
                    }
                    onReady.run();
                });
    }

    private void loadChatRooms() {
        db.collection("chat_rooms")
                .whereArrayContains("members", currentUser.getUid())
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;
                    chatRoomList.clear();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        ChatRoom room = doc.toObject(ChatRoom.class);
                        if (room != null) {
                            room.setId(doc.getId());

                            doc.getReference()
                                    .collection("messages")
                                    .orderBy("timestamp", Query.Direction.DESCENDING)
                                    .limit(1)
                                    .addSnapshotListener((msgSnap, msgErr) -> {
                                        if (msgErr != null || msgSnap == null || msgSnap.isEmpty()) return;

                                        DocumentSnapshot lastMsg = msgSnap.getDocuments().get(0);
                                        room.setLastMessage(lastMsg.getString("text"));
                                        room.setLastMessageSender(lastMsg.getString("senderName"));
                                        room.setLastMessageSenderId(lastMsg.getString("senderId"));
                                        Timestamp ts = lastMsg.getTimestamp("timestamp");
                                        if (ts != null) room.setLastMessageTimestamp(ts.toDate());

                                        updateChatRoom(room);
                                    });
                        }
                    }
                });
    }

    private void updateChatRoom(ChatRoom updatedRoom) {
        Date lastOpened = lastOpenedMap.get(updatedRoom.getId());
        Date lastMsgTime = updatedRoom.getLastMessageTimestamp();

        boolean isUnread = lastMsgTime != null && (lastOpened == null || lastMsgTime.after(lastOpened));
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

        sortAndDisplay();
    }

    private void sortAndDisplay() {
        Collections.sort(chatRoomList, (a, b) -> {
            Date aTime = a.getLastMessageTimestamp() != null ? a.getLastMessageTimestamp() : new Date(0);
            Date bTime = b.getLastMessageTimestamp() != null ? b.getLastMessageTimestamp() : new Date(0);
            return bTime.compareTo(aTime);
        });

        adapter.setUserLastOpenedMap(lastOpenedMap);
        adapter.updateData(chatRoomList);
    }
}
