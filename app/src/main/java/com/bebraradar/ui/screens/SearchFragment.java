package com.bebraradar.ui.screens;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bebraradar.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class SearchFragment extends Fragment {

    public interface Listener {
        void onFindTrainsRequested(@NonNull String fromStation, @NonNull String toStation);
    }

    private Listener listener;
    private TextInputLayout fromStationLayout;
    private TextInputLayout toStationLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        fromStationLayout = view.findViewById(R.id.fromStationInput);
        toStationLayout = view.findViewById(R.id.toStationInput);
        MaterialButton findTrainsButton = view.findViewById(R.id.findTrainsButton);
        ImageButton swapButton = view.findViewById(R.id.swapButton);

        swapButton.setOnClickListener(v -> swapStations());

        findTrainsButton.setOnClickListener(v -> {
            String fromStation = getText(fromStationLayout);
            String toStation = getText(toStationLayout);

            if (listener != null) {
                listener.onFindTrainsRequested(fromStation, toStation);
            }
        });
    }

    private void swapStations() {
        TextInputEditText fromEditText = (TextInputEditText) fromStationLayout.getEditText();
        TextInputEditText toEditText = (TextInputEditText) toStationLayout.getEditText();
        if (fromEditText == null || toEditText == null) {
            return;
        }
        CharSequence fromText = fromEditText.getText();
        CharSequence toText = toEditText.getText();
        fromEditText.setText(toText);
        toEditText.setText(fromText);
    }

    private String getText(@NonNull TextInputLayout layout) {
        TextInputEditText editText = (TextInputEditText) layout.getEditText();
        if (editText == null) {
            return "";
        }
        CharSequence text = editText.getText();
        return text == null ? "" : text.toString().trim();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Listener) {
            listener = (Listener) context;
        } else {
            throw new IllegalStateException("Parent activity must implement SearchFragment.Listener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }
}
