package com.labactivity.studysync.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.labactivity.studysync.BrowseActivity;
import com.labactivity.studysync.MainActivity;
import com.labactivity.studysync.R;
import com.labactivity.studysync.adapters.SetAdapter;
import com.labactivity.studysync.models.Flashcard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HomeFragment extends Fragment {

    private TextView userGreetingTxt;
    private ImageView profileImage;
    private View flashcardsCard, quizzesCard, chatRoomsCard, browseCard;

    private EditText searchView;
    private RecyclerView continueRecyclerView;
    private SetAdapter setAdapter;
    private final List<Flashcard> recentSets = new ArrayList<>();

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String currentUserId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userGreetingTxt = view.findViewById(R.id.userGreetingTxt);
        profileImage = view.findViewById(R.id.profileImage);
        flashcardsCard = view.findViewById(R.id.flashcardsCard);
        quizzesCard = view.findViewById(R.id.quizzesCard);
        chatRoomsCard = view.findViewById(R.id.chatRoomsCard);
        browseCard = view.findViewById(R.id.browseCard);
        continueRecyclerView = view.findViewById(R.id.continueRecyclerView);
        searchView = view.findViewById(R.id.searchView);

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        setupGreeting();
        setupProfileImage();
        setupCardClickListeners();
        setupContinueRecycler();
        loadRecentSets();

        searchView.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), BrowseActivity.class));
        });
    }

    private void setupGreeting() {
        db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String firstName = documentSnapshot.getString("firstName");
                    if (firstName != null && !firstName.isEmpty()) {
                        userGreetingTxt.setText("Hello, " + firstName + "!");
                    }
                });
    }


    private void setupProfileImage() {
        db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!isAdded()) return;

                    String photoUrl = documentSnapshot.getString("photoUrl");
                    if (photoUrl != null && !photoUrl.isEmpty()) {
                        Glide.with(requireContext())
                                .load(photoUrl)
                                .placeholder(R.drawable.user_profile)
                                .circleCrop()
                                .into(profileImage);
                    }
                });

        profileImage.setOnClickListener(v -> {
            if (!isAdded()) return;
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new UserProfileFragment())
                        .addToBackStack(null)
                        .commit();
                ((MainActivity) getActivity()).setBottomNavSelection(R.id.nav_profile);
            }
        });
    }

    private void setupCardClickListeners() {
        flashcardsCard.setOnClickListener(v -> {
            navigateToSetFragment("flashcard");
        });

        quizzesCard.setOnClickListener(v -> {
            navigateToSetFragment("quiz");
        });

        chatRoomsCard.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new ChatFragment())
                        .addToBackStack(null)
                        .commit();
                ((MainActivity) getActivity()).setBottomNavSelection(R.id.nav_chat);
            }
        });

        browseCard.setOnClickListener(v -> {
            startActivity(new Intent(getContext(), BrowseActivity.class));
        });
    }

    private void navigateToSetFragment(String type) {
        Bundle args = new Bundle();
        args.putString("defaultFilter", type);
        SetFragment fragment = new SetFragment();
        fragment.setArguments(args);

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit();
            ((MainActivity) getActivity()).setBottomNavSelection(R.id.nav_sets);
        }
    }

    private void setupContinueRecycler() {
        setAdapter = new SetAdapter(getContext(), new ArrayList<>(), set -> {
            openPreviewActivity(set);
        });
        continueRecyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        continueRecyclerView.setAdapter(setAdapter);
    }


    private void loadRecentSets() {
        db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    List<Map<String, Object>> owned = (List<Map<String, Object>>) documentSnapshot.get("owned_sets");
                    List<Map<String, Object>> saved = (List<Map<String, Object>>) documentSnapshot.get("saved_sets");

                    recentSets.clear();
                    if (owned != null) {
                        for (Map<String, Object> item : owned) {
                            loadSetFromItem(item);
                        }
                    }
                    if (saved != null) {
                        for (Map<String, Object> item : saved) {
                            loadSetFromItem(item);
                        }
                    }
                });
    }

    private void loadSetFromItem(Map<String, Object> item) {
        String id = (String) item.get("id");
        String type = (String) item.get("type");
        Long lastAccessed = item.get("lastAccessed") instanceof Number ? ((Number) item.get("lastAccessed")).longValue() : 0;

        if (id == null || type == null) return;

        String collection = type.equals("quiz") ? "quiz" : "flashcards";

        db.collection(collection)
                .document(id)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;

                    Flashcard set = doc.toObject(Flashcard.class);
                    if (set == null) return;

                    set.setId(doc.getId());
                    set.setType(type);
                    set.setLastAccessed(lastAccessed);

                    Object rawProgress = item.get("progress");
                    if (rawProgress instanceof Number) {
                        set.setProgress(((Number) rawProgress).intValue());
                    } else {
                        set.setProgress(0);
                    }

                    recentSets.add(set);
                    updateContinueRecycler();
                });
    }


    private void updateContinueRecycler() {
        recentSets.sort((a, b) -> Long.compare(b.getLastAccessed(), a.getLastAccessed()));
        setAdapter = new SetAdapter(getContext(), new ArrayList<>(recentSets), set -> {
            openPreviewActivity(set);
        });
        continueRecyclerView.setAdapter(setAdapter);
    }

    private void openPreviewActivity(Flashcard set) {
        if (getContext() == null) return;

        Intent intent;
        if ("quiz".equals(set.getType())) {
            intent = new Intent(getContext(), com.labactivity.studysync.QuizPreviewActivity.class);
            intent.putExtra("quizId", set.getId());
        } else {
            intent = new Intent(getContext(), com.labactivity.studysync.FlashcardPreviewActivity.class);
            intent.putExtra("setId", set.getId());
        }
        startActivity(intent);
    }


}
