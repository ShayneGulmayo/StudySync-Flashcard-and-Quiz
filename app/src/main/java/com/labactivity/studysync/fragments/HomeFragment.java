package com.labactivity.studysync.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.labactivity.studysync.BrowseActivity;
import com.labactivity.studysync.GenerateSetActivity;
import com.labactivity.studysync.MainActivity;
import com.labactivity.studysync.NotificationsActivity;
import com.labactivity.studysync.R;
import com.labactivity.studysync.adapters.SetAdapter;
import com.labactivity.studysync.models.Flashcard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HomeFragment extends Fragment {

    private TextView userGreetingTxt;
    private ImageView profileImage, notifBtn;
    private View flashcardsCard, chatRoomsCard, browseCard, notifIndicator;

    private EditText searchView;
    private RecyclerView continueRecyclerView;
    private SetAdapter setAdapter;
    private final List<Flashcard> recentSets = new ArrayList<>();

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String currentUserId;
    private View noRecentSetsView;



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
        chatRoomsCard = view.findViewById(R.id.chatRoomsCard);
        browseCard = view.findViewById(R.id.browseCard);
        continueRecyclerView = view.findViewById(R.id.continueRecyclerView);
        searchView = view.findViewById(R.id.searchView);
        notifBtn = view.findViewById(R.id.notifBtn);
        notifIndicator = view.findViewById(R.id.notifIndicator);
        View addFlashcardBtn = view.findViewById(R.id.add_flashcard);
        View addQuizBtn = view.findViewById(R.id.add_quiz);
        noRecentSetsView = view.findViewById(R.id.noRecentSetsView);


        applyClickShrinkAnimation(flashcardsCard);
        applyClickShrinkAnimation(chatRoomsCard);
        applyClickShrinkAnimation(browseCard);
        applyClickShrinkAnimation(addFlashcardBtn);
        applyClickShrinkAnimation(addQuizBtn);



        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e("HomeFragment", "User not logged in");
            return;
        }

        currentUserId = currentUser.getUid();
        checkUnreadNotifications(); // Safe to call after user check


        setupGreeting();
        setupProfileImage();
        setupCardClickListeners();
        setupContinueRecycler();
        loadRecentSets();

        searchView.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), BrowseActivity.class));
        });
        notifBtn.setOnClickListener(v ->{
            startActivity(new Intent(requireContext(), NotificationsActivity.class));
        } );
        addFlashcardBtn.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), GenerateSetActivity.class);
            intent.putExtra("setType", "flashcard");
            startActivity(intent);
        });

        addQuizBtn.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), GenerateSetActivity.class);
            intent.putExtra("setType", "quiz");
            startActivity(intent);
        });

    }

    @Override
    public void onResume() {
        super.onResume();
        checkUnreadNotifications();  // 🔁 Refresh every time fragment resumes
    }

    private void applyClickShrinkAnimation(View card) {
        card.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(120).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    break;
            }
            return false; // Important: lets the onClick still fire
        });
    }

    private void checkUnreadNotifications() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e("HomeFragment", "User is not logged in.");
            return;
        }

        String uid = currentUser.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users").document(uid)
                .collection("notifications")
                .whereEqualTo("read", false)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int unreadCount = queryDocumentSnapshots.size();
                    if (isAdded()) {
                        if (unreadCount > 0) {
                            notifIndicator.setVisibility(View.VISIBLE); // 👈 Show the indicator
                        } else {
                            notifIndicator.setVisibility(View.GONE); // 👈 Hide it if all are read
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e("HomeFragment", "Error checking notifications", e));
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
                    recentSets.clear();

                    List<Map<String, Object>> owned = (List<Map<String, Object>>) documentSnapshot.get("owned_sets");
                    List<Map<String, Object>> saved = (List<Map<String, Object>>) documentSnapshot.get("saved_sets");

                    List<Map<String, Object>> allSets = new ArrayList<>();
                    if (owned != null) allSets.addAll(owned);
                    if (saved != null) allSets.addAll(saved);

                    if (allSets.isEmpty()) {
                        updateContinueRecycler();
                        return;
                    }

                    final int totalSetsToLoad = allSets.size();
                    final int[] loadedCount = {0};

                    for (Map<String, Object> item : allSets) {
                        loadSetFromItem(item, totalSetsToLoad, loadedCount);
                    }
                });
    }

    private void loadSetFromItem(Map<String, Object> item, int totalSetsToLoad, int[] loadedCount) {
        String id = (String) item.get("id");
        String type = (String) item.get("type");

        Long lastAccessed = item.get("lastAccessed") instanceof Number ? ((Number) item.get("lastAccessed")).longValue() : 0;

        if (id == null || type == null) {
            loadedCount[0]++;
            if (loadedCount[0] == totalSetsToLoad) {
                updateContinueRecycler();
            }
            return;
        }

        String collection = type.equals("quiz") ? "quiz" : "flashcards";

        db.collection(collection)
                .document(id)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Flashcard set = doc.toObject(Flashcard.class);
                        if (set != null) {
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
                        }
                    }

                    loadedCount[0]++;
                    if (loadedCount[0] == totalSetsToLoad) {
                        updateContinueRecycler();
                    }
                })
                .addOnFailureListener(e -> {
                    loadedCount[0]++;
                    if (loadedCount[0] == totalSetsToLoad) {
                        updateContinueRecycler();
                    }
                    Log.e("HomeFragment", "Error loading set: " + id, e);
                });
    }


    private void updateContinueRecycler() {
        if (!isAdded()) return;

        recentSets.sort((a, b) -> Long.compare(b.getLastAccessed(), a.getLastAccessed()));

        if (recentSets.isEmpty()) {
            continueRecyclerView.setVisibility(View.GONE);
            if (noRecentSetsView != null) {
                noRecentSetsView.setVisibility(View.VISIBLE);
            }
        } else {
            continueRecyclerView.setVisibility(View.VISIBLE);
            if (noRecentSetsView != null) {
                noRecentSetsView.setVisibility(View.GONE);
            }

            setAdapter = new SetAdapter(getContext(), new ArrayList<>(recentSets), this::openPreviewActivity);
            continueRecyclerView.setAdapter(setAdapter);
        }
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
