package com.bebraradar.ui.screens;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bebraradar.R;
import com.bebraradar.model.Train;
import com.bebraradar.ui.adapter.TrainCardAdapter;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class TrainResultsFragment extends Fragment implements TrainCardAdapter.OnTrainSelectedListener {

    public interface Listener {
        void onCloseResults();

        void onTrainSelected(@NonNull Train train);
    }

    private Listener listener;
    private TrainCardAdapter adapter;
    private final List<Train> trains = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_train_results, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ImageButton backButton = view.findViewById(R.id.backToSearchButton);
        MaterialButton filterButton = view.findViewById(R.id.dateFilterButton);
        MaterialButton sortButton = view.findViewById(R.id.showFaresToggle);
        RecyclerView recyclerView = view.findViewById(R.id.trainListRecyclerView);
        TextView subtitle = view.findViewById(R.id.trainDetailsSubtitle);

        backButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCloseResults();
            }
        });

        filterButton.setOnClickListener(v -> Toast.makeText(requireContext(), R.string.toast_feature_soon, Toast.LENGTH_SHORT).show());
        sortButton.setOnClickListener(v -> Toast.makeText(requireContext(), R.string.toast_feature_soon, Toast.LENGTH_SHORT).show());

        adapter = new TrainCardAdapter(requireContext(), trains, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        populateMockData();
        subtitle.setText(getString(R.string.train_details_count, trains.size()));
    }

    private void populateMockData() {
        trains.clear();
        trains.add(new Train(
                "Mysore - Ajmer Express",
                "22927",
                "01 Nov, Mon",
                "10:15",
                "Mysore Junction",
                "02 Nov, Tue",
                "04:20",
                "Ajmer Junction",
                true,
                "18h 05m"
        ));
        trains.add(new Train(
                "Karnavati Express",
                "12933",
                "01 Nov, Mon",
                "11:00",
                "Bandra Terminus",
                "02 Nov, Tue",
                "04:35",
                "Ahmedabad Jn",
                true,
                "17h 35m"
        ));
        trains.add(new Train(
                "Rajdhani Express",
                "12951",
                "01 Nov, Mon",
                "10:15",
                "Mumbai Central",
                "02 Nov, Tue",
                "04:20",
                "New Delhi",
                false,
                "17h 30m"
        ));
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onTrainSelected(@NonNull Train train) {
        if (listener != null) {
            listener.onTrainSelected(train);
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Listener) {
            listener = (Listener) context;
        } else {
            throw new IllegalStateException("Parent activity must implement TrainResultsFragment.Listener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }
}
