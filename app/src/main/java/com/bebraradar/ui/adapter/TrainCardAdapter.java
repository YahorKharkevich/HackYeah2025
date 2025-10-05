package com.bebraradar.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bebraradar.R;
import com.bebraradar.model.Train;

import java.util.List;

public class TrainCardAdapter extends RecyclerView.Adapter<TrainCardAdapter.TrainCardViewHolder> {

    public interface OnTrainSelectedListener {
        void onTrainSelected(@NonNull Train train);
    }

    private final List<Train> trains;
    private final LayoutInflater inflater;
    private final OnTrainSelectedListener listener;

    public TrainCardAdapter(@NonNull Context context,
                            @NonNull List<Train> trains,
                            @NonNull OnTrainSelectedListener listener) {
        this.inflater = LayoutInflater.from(context);
        this.trains = trains;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TrainCardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.item_train_card, parent, false);
        return new TrainCardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TrainCardViewHolder holder, int position) {
        Train train = trains.get(position);
        holder.bind(train);
    }

    @Override
    public int getItemCount() {
        return trains.size();
    }

    class TrainCardViewHolder extends RecyclerView.ViewHolder {

        private final TextView nameText;
        private final TextView numberText;
        private final TextView runsText;
        private final TextView departureDateText;
        private final TextView departureTimeText;
        private final TextView departureStationText;
        private final TextView arrivalDateText;
        private final TextView arrivalTimeText;
        private final TextView arrivalStationText;
        private final TextView durationText;
        private final TextView viewRouteButton;

        TrainCardViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.trainNameText);
            numberText = itemView.findViewById(R.id.trainNumberText);
            runsText = itemView.findViewById(R.id.trainRunsText);
            departureDateText = itemView.findViewById(R.id.departureDateText);
            departureTimeText = itemView.findViewById(R.id.departureTimeText);
            departureStationText = itemView.findViewById(R.id.departureStationText);
            arrivalDateText = itemView.findViewById(R.id.arrivalDateText);
            arrivalTimeText = itemView.findViewById(R.id.arrivalTimeText);
            arrivalStationText = itemView.findViewById(R.id.arrivalStationText);
            durationText = itemView.findViewById(R.id.trainDurationText);
            viewRouteButton = itemView.findViewById(R.id.viewRouteButton);
        }

        void bind(Train train) {
            nameText.setText(train.getName());
            numberText.setText(itemView.getContext().getString(R.string.train_number_label, train.getNumber()));
            runsText.setText(train.isRunsDaily() ? R.string.label_runs_daily : R.string.label_runs_on_schedule);

            int runsColor = ContextCompat.getColor(itemView.getContext(),
                    train.isRunsDaily() ? R.color.success_green : R.color.text_secondary);
            runsText.setTextColor(runsColor);

            departureDateText.setText(train.getDepartureDate());
            departureTimeText.setText(train.getDepartureTime());
            departureStationText.setText(train.getDepartureStation());

            arrivalDateText.setText(train.getArrivalDate());
            arrivalTimeText.setText(train.getArrivalTime());
            arrivalStationText.setText(train.getArrivalStation());

            durationText.setText(itemView.getContext().getString(R.string.train_duration_format, train.getDuration()));

            View.OnClickListener clickListener = v -> listener.onTrainSelected(train);
            itemView.setOnClickListener(clickListener);
            viewRouteButton.setOnClickListener(clickListener);
        }
    }
}
