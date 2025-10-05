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
import com.bebraradar.model.RouteStop;

import java.util.List;

public class RouteStopsAdapter extends RecyclerView.Adapter<RouteStopsAdapter.RouteStopViewHolder> {

    private final List<RouteStop> stops;
    private final LayoutInflater inflater;

    public RouteStopsAdapter(@NonNull Context context, @NonNull List<RouteStop> stops) {
        this.stops = stops;
        this.inflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public RouteStopViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.item_route_stop, parent, false);
        return new RouteStopViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RouteStopViewHolder holder, int position) {
        RouteStop stop = stops.get(position);
        holder.bind(stop, position == stops.size() - 1);
    }

    @Override
    public int getItemCount() {
        return stops.size();
    }

    static class RouteStopViewHolder extends RecyclerView.ViewHolder {

        private final TextView timeText;
        private final TextView stationText;
        private final TextView metaText;
        private final TextView statusText;
        private final View lineView;
        private final View dotView;

        RouteStopViewHolder(@NonNull View itemView) {
            super(itemView);
            timeText = itemView.findViewById(R.id.stopTimeText);
            stationText = itemView.findViewById(R.id.stopStationText);
            metaText = itemView.findViewById(R.id.stopMetaText);
            statusText = itemView.findViewById(R.id.stopStatusText);
            lineView = itemView.findViewById(R.id.timelineLine);
            dotView = itemView.findViewById(R.id.timelineDot);
        }

        void bind(@NonNull RouteStop stop, boolean isLast) {
            Context context = itemView.getContext();
            timeText.setText(stop.getScheduleTime());
            stationText.setText(stop.getStationName());
            metaText.setText(stop.getMeta());
            statusText.setText(stop.getStatusText());

            lineView.setVisibility(isLast ? View.INVISIBLE : View.VISIBLE);

            int dotDrawable = stop.isCompleted() ? R.drawable.bg_timeline_dot_success : R.drawable.bg_timeline_dot_grey;
            if (stop.isCurrent()) {
                dotDrawable = R.drawable.bg_timeline_dot_primary;
                statusText.setTextColor(ContextCompat.getColor(context, R.color.primary_blue));
            } else if (stop.isCompleted()) {
                statusText.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            } else {
                statusText.setTextColor(ContextCompat.getColor(context, R.color.alert_red));
            }
            dotView.setBackground(ContextCompat.getDrawable(context, dotDrawable));
        }
    }
}
