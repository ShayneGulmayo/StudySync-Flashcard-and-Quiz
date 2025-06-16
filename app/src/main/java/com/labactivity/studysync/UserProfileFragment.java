package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;

public class UserProfileFragment extends Fragment {

    private TextView userFullName, usernameTxt;
    private ImageView profilePhoto;
    private MaterialButton logoutBtn, seeSettingsBtn;
    private FirebaseAuth mAuth;

    public UserProfileFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_user_profile, container, false);

        mAuth = FirebaseAuth.getInstance();

        userFullName = view.findViewById(R.id.userFullName);
        usernameTxt = view.findViewById(R.id.usernameTxt);
        profilePhoto = view.findViewById(R.id.chatroom_photo);
        logoutBtn = view.findViewById(R.id.logoutBtn);
        seeSettingsBtn = view.findViewById(R.id.see_members_btn);

        userFullName.setText("John Doe");
        usernameTxt.setText("@johndoe");

        logoutBtn.setOnClickListener(v -> {
            mAuth.signOut();
            Toast.makeText(getActivity(), "Logged out successfully", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(getActivity(), LoginActivity.class));
            requireActivity().finish();
        });

        seeSettingsBtn.setOnClickListener(v -> {
            Toast.makeText(getActivity(), "Settings clicked (not implemented)", Toast.LENGTH_SHORT).show();
            // You can later open a SettingsActivity or similar here
        });

        return view;
    }
}
