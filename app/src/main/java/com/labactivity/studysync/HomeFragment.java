package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class HomeFragment extends Fragment {

    private Button flashcardsBtn, quizBtn;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        flashcardsBtn = view.findViewById(R.id.flashcardsBtn);
        quizBtn = view.findViewById(R.id.quizBtn);

        flashcardsBtn.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), FlashcardsActivity.class);
            startActivity(intent);
        });

        quizBtn.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), QuizzesActivity.class);
            startActivity(intent);
        });

        return view;
    }
}
