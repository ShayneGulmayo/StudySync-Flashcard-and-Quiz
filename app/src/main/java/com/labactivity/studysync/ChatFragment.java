package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SearchView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.labactivity.studysync.CreateChatRoomActivity;
import com.labactivity.studysync.R;

public class ChatFragment extends Fragment {

    private ImageView addButton;
    private SearchView searchView;
    private RecyclerView chatRecyclerView;

    public ChatFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        // Initialize UI elements
        addButton = view.findViewById(R.id.add_button);
        searchView = view.findViewById(R.id.search_set);
        chatRecyclerView = view.findViewById(R.id.recycler_flashcards);

        // Setup RecyclerView
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        // You can later set an adapter here for chat rooms

        // Set onClick for Add Button
        addButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), CreateChatRoomActivity.class);
            startActivity(intent);
        });

        return view;
    }
}
