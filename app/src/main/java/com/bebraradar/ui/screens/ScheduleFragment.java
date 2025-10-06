package com.bebraradar.ui.screens;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bebraradar.BuildConfig;
import com.bebraradar.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Simple schedule browser that talks to the timetable REST endpoints.
 */
public class ScheduleFragment extends Fragment {

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE;

    private RecyclerView listView;
    private TextView dateLabel;
    private TextView headerLabel;
    private MaterialButton backButton;
    private ChipGroup weekdayGroup;

    private final List<String> routes = new ArrayList<>();
    private final List<TripItem> trips = new ArrayList<>();
    private RoutesAdapter routesAdapter;
    private TripsAdapter tripsAdapter;

    private LocalDate selectedDate = LocalDate.now();
    private String selectedWeekday = null; // Mon, Tue ...
    private String selectedRouteId = null;
    private String cachedBaseUrl;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_schedule, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dateLabel = view.findViewById(R.id.dateText);
        headerLabel = view.findViewById(R.id.headerText);
        backButton = view.findViewById(R.id.backButton);
        listView = view.findViewById(R.id.scheduleRecycler);
        weekdayGroup = view.findViewById(R.id.weekdayGroup);
        MaterialButton pickDateButton = view.findViewById(R.id.pickDateButton);

        listView.setLayoutManager(new LinearLayoutManager(requireContext()));
        routesAdapter = new RoutesAdapter(routes, this::onRouteSelected);
        tripsAdapter = new TripsAdapter(trips, this::onTripSelected);

        pickDateButton.setOnClickListener(v -> openDatePicker());
        backButton.setOnClickListener(v -> showRoutesView());

        weekdayGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                selectedWeekday = null;
            } else {
                int id = checkedIds.get(0);
                Chip chip = group.findViewById(id);
                selectedWeekday = chip != null ? chip.getText().toString() : null;
            }
            fetchRoutes();
        });

        updateDateLabel();
        showRoutesView();
        fetchRoutes();
    }

    @Override
    public void onDestroyView() {
        listView.setAdapter(null);
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        networkExecutor.shutdownNow();
        super.onDestroy();
    }

    private void openDatePicker() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        DatePickerDialog dialog = new DatePickerDialog(requireContext(), (picker, year, month, dayOfMonth) -> {
            selectedDate = LocalDate.of(year, month + 1, dayOfMonth);
            updateDateLabel();
            fetchRoutes();
        }, selectedDate.getYear(), selectedDate.getMonthValue() - 1, selectedDate.getDayOfMonth());
        dialog.show();
    }

    private void updateDateLabel() {
        dateLabel.setText(selectedDate.format(dateFormatter));
    }

    private void showRoutesView() {
        selectedRouteId = null;
        backButton.setVisibility(View.GONE);
        headerLabel.setText(buildRoutesHeader());
        listView.setAdapter(routesAdapter);
        if (routes.isEmpty()) {
            headerLabel.setText(getString(R.string.schedule_no_routes));
        }
    }

    private void showTripsView(String routeId) {
        selectedRouteId = routeId;
        backButton.setVisibility(View.VISIBLE);
        headerLabel.setText(getString(R.string.schedule_trips_header, selectedDate.format(dateFormatter), routeId));
        listView.setAdapter(tripsAdapter);
        if (trips.isEmpty()) {
            headerLabel.setText(getString(R.string.schedule_no_trips));
        }
    }

    private void onRouteSelected(String routeId) {
        fetchTrips(routeId);
    }

    private void onTripSelected(TripItem item) {
        if (!isAdded()) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        if (item.stops.isEmpty()) {
            builder.append(getString(R.string.schedule_no_trips));
        } else {
            for (int i = 0; i < item.stops.size(); i++) {
                TripStop stop = item.stops.get(i);
                builder.append(getString(R.string.schedule_stop_line,
                        i + 1,
                        stop.stopName,
                        stop.stopId,
                        formatSeconds(stop.arrivalSeconds),
                        formatSeconds(stop.departureSeconds)));
                if (i < item.stops.size() - 1) {
                    builder.append('\n');
                }
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.schedule_trip_title, item.tripId))
            .setMessage(builder.toString())
            .setPositiveButton(R.string.schedule_dialog_close, null)
            .show();
    }

    private void fetchRoutes() {
        final String queryUrl = buildRoutesUrl();
        if (queryUrl == null) {
            return;
        }

        headerLabel.setText(R.string.schedule_tab_title);
        networkExecutor.execute(() -> {
            Request request = new Request.Builder().url(queryUrl).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return;
                }
                String body = response.body().string();
                JSONArray array = new JSONArray(body);
                List<String> fetched = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.getJSONObject(i);
                    String id = item.optString("id", null);
                    if (id != null && !id.isEmpty()) {
                        fetched.add(id);
                    }
                }
                postToUi(() -> {
                    routes.clear();
                    routes.addAll(fetched);
                    routesAdapter.notifyDataSetChanged();
                    showRoutesView();
                });
            } catch (IOException | JSONException ignored) {
            }
        });
    }

    private void fetchTrips(String routeId) {
        final String url = buildTripsUrl(routeId);
        if (url == null) {
            return;
        }
        postToUi(() -> {
            trips.clear();
            tripsAdapter.notifyDataSetChanged();
            showTripsView(routeId);
        });

        networkExecutor.execute(() -> {
            Request request = new Request.Builder().url(url).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return;
                }
                String body = response.body().string();
                JSONArray array = new JSONArray(body);
                List<TripItem> fetched = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject tripJson = array.getJSONObject(i);
                    long tripId = tripJson.optLong("tripId", -1);
                    String startTime = tripJson.optString("startTime", "");
                    String displayStart = formatStart(startTime);
                    JSONArray stopsJson = tripJson.optJSONArray("stops");
                    List<TripStop> stopList = new ArrayList<>();
                    if (stopsJson != null) {
                        for (int j = 0; j < stopsJson.length(); j++) {
                            JSONObject stopJson = stopsJson.getJSONObject(j);
                            stopList.add(new TripStop(
                                stopJson.optString("stopId", ""),
                                stopJson.optString("stopName", ""),
                                stopJson.optInt("arrivalTime", 0),
                                stopJson.optInt("departureTime", 0)
                            ));
                        }
                    }
                    fetched.add(new TripItem(tripId, startTime, displayStart, stopList));
                }
                postToUi(() -> {
                    if (selectedRouteId == null || !selectedRouteId.equals(routeId)) {
                        return;
                    }
                trips.clear();
                trips.addAll(fetched);
                tripsAdapter.notifyDataSetChanged();
                if (trips.isEmpty()) {
                    headerLabel.setText(getString(R.string.schedule_no_trips));
                }
                });
            } catch (IOException | JSONException ignored) {
            }
        });
    }

    private String buildRoutesHeader() {
        if (selectedWeekday != null) {
            return getString(R.string.schedule_routes_header, selectedWeekday);
        }
        return getString(R.string.schedule_routes_header, selectedDate.format(dateFormatter));
    }

    private String buildRoutesUrl() {
        String base = resolveBaseUrl();
        if (base == null) {
            return null;
        }
        if (selectedWeekday != null) {
            return base + "timetable/routes?weekday=" + selectedWeekday;
        }
        return base + "timetable/routes?date=" + selectedDate.format(dateFormatter);
    }

    private String buildTripsUrl(String routeId) {
        String base = resolveBaseUrl();
        if (base == null) {
            return null;
        }
        return base + "timetable/routes/" + routeId + "/trips?date=" + selectedDate.format(dateFormatter);
    }

    private String resolveBaseUrl() {
        if (cachedBaseUrl != null) {
            return cachedBaseUrl;
        }
        String[] candidates = buildApiEndpointCandidates();
        for (String candidate : candidates) {
            if (candidate != null) {
                cachedBaseUrl = candidate;
                return cachedBaseUrl;
            }
        }
        return null;
    }

    private String[] buildApiEndpointCandidates() {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(sanitizeBaseUrl(BuildConfig.DEFAULT_API_BASE_URL));
        candidates.add(sanitizeBaseUrl("http://10.0.2.2:8080/"));
        candidates.add(sanitizeBaseUrl("http://10.0.2.2:8081/"));
        candidates.add(sanitizeBaseUrl("http://127.0.0.1:8080/"));
        candidates.add(sanitizeBaseUrl("http://localhost:8080/"));
        candidates.remove(null);
        return candidates.toArray(new String[0]);
    }

    private String formatStart(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.isEmpty()) {
            return "";
        }
        int tIdx = isoTimestamp.indexOf('T');
        if (tIdx == -1 || tIdx + 1 >= isoTimestamp.length()) {
            return isoTimestamp;
        }
        String timePart = isoTimestamp.substring(tIdx + 1);
        int divider = findFirstIndex(timePart, 'Z', '+', '-');
        if (divider != -1) {
            timePart = timePart.substring(0, divider);
        }
        if (timePart.length() >= 5) {
            return timePart.substring(0, 5);
        }
        return timePart;
    }

    private int findFirstIndex(String value, char... targets) {
        int min = -1;
        for (char target : targets) {
            int idx = value.indexOf(target);
            if (idx >= 0 && (min == -1 || idx < min)) {
                min = idx;
            }
        }
        return min;
    }

    private String sanitizeBaseUrl(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://" + trimmed;
        }
        if (!trimmed.endsWith("/")) {
            trimmed = trimmed + "/";
        }
        if (trimmed.contains("$")) {
            return null;
        }
        return trimmed;
    }

    private void postToUi(Runnable task) {
        if (!isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(task);
    }

    private String formatSeconds(int totalSeconds) {
        int days = totalSeconds / 86400;
        int remainder = totalSeconds % 86400;
        if (remainder < 0) {
            remainder += 86400;
            days -= 1;
        }
        int hours = remainder / 3600;
        int minutes = (remainder % 3600) / 60;
        String base = String.format(Locale.getDefault(), "%02d:%02d", hours, minutes);
        if (days == 0) {
            return base;
        }
        return base + String.format(Locale.getDefault(), " (+%dd)", days);
    }

    private static class RoutesAdapter extends RecyclerView.Adapter<RouteViewHolder> {
        private final List<String> items;
        private final RouteClickListener listener;

        RoutesAdapter(List<String> items, RouteClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public RouteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_schedule_route, parent, false);
            return new RouteViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RouteViewHolder holder, int position) {
            String routeId = items.get(position);
            holder.routeIdText.setText(routeId);
            holder.itemView.setOnClickListener(v -> listener.onRouteClicked(routeId));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static class TripsAdapter extends RecyclerView.Adapter<TripViewHolder> {
        private final List<TripItem> items;
        private final TripClickListener listener;

        TripsAdapter(List<TripItem> items, TripClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public TripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_schedule_trip, parent, false);
            return new TripViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TripViewHolder holder, int position) {
            TripItem item = items.get(position);
            holder.title.setText(holder.itemView.getContext().getString(R.string.schedule_trip_title, item.tripId));
            String timeLabel = item.displayStart == null || item.displayStart.isEmpty()
                    ? item.rawStartTime
                    : item.displayStart;
            holder.meta.setText(String.format(Locale.getDefault(), "%s • %d stops", timeLabel, item.stops.size()));
            holder.itemView.setOnClickListener(v -> listener.onTripClicked(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static class RouteViewHolder extends RecyclerView.ViewHolder {
        final TextView routeIdText;
        RouteViewHolder(@NonNull View itemView) {
            super(itemView);
            routeIdText = itemView.findViewById(R.id.routeIdText);
        }
    }

    private static class TripViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView meta;
        TripViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tripTitle);
            meta = itemView.findViewById(R.id.tripMeta);
        }
    }

    private interface RouteClickListener {
        void onRouteClicked(String routeId);
    }

    private interface TripClickListener {
        void onTripClicked(TripItem item);
    }

    private static class TripItem {
        final long tripId;
        final String rawStartTime;
        final String displayStart;
        final List<TripStop> stops;

        TripItem(long tripId, String rawStartTime, String displayStart, List<TripStop> stops) {
            this.tripId = tripId;
            this.rawStartTime = rawStartTime;
            this.displayStart = displayStart;
            this.stops = stops;
        }
    }

    private static class TripStop {
        final String stopId;
        final String stopName;
        final int arrivalSeconds;
        final int departureSeconds;

        TripStop(String stopId, String stopName, int arrivalSeconds, int departureSeconds) {
            this.stopId = stopId;
            this.stopName = stopName;
            this.arrivalSeconds = arrivalSeconds;
            this.departureSeconds = departureSeconds;
        }
    }
}
