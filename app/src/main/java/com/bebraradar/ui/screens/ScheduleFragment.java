package com.bebraradar.ui.screens;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bebraradar.BuildConfig;
import com.bebraradar.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

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

public class ScheduleFragment extends Fragment {

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE;
    private final DateTimeFormatter displayDateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.getDefault());

    private MapView mapView;
    private Polyline routePolyline;
    private final List<Marker> stopMarkers = new ArrayList<>();

    private View routesContent;
    private View detailContent;
    private TextView dateLabel;
    private MaterialButton pickDateButton;
    private ChipGroup weekdayGroup;
    private TextView routesHeader;
    private RecyclerView routesRecycler;

    private TextView detailRouteBadge;
    private TextView detailDirectionLabel;
    private TextView detailScheduleDate;
    private TextView detailRouteTitle;
    private TextView detailRouteSubtitle;
    private LinearLayout stopsContainer;
    private TextView detailTripsHeader;
    private RecyclerView tripsRecycler;
    private MaterialButton backButton;

    private final List<String> routes = new ArrayList<>();
    private final List<TripItem> trips = new ArrayList<>();
    private RoutesAdapter routesAdapter;
    private TripsAdapter tripsAdapter;

    private LocalDate selectedDate = LocalDate.now();
    private String selectedWeekday = null;
    private String selectedRouteId = null;
    private TripItem selectedTrip = null;
    private String cachedBaseUrl;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_schedule, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mapView = view.findViewById(R.id.scheduleMapView);
        if (mapView != null) {
            Configuration.getInstance().setUserAgentValue(requireContext().getApplicationContext().getPackageName());
            mapView.setTileSource(TileSourceFactory.MAPNIK);
            mapView.setMultiTouchControls(true);
            mapView.setVisibility(View.GONE);
        }

        routesContent = view.findViewById(R.id.routesContent);
        detailContent = view.findViewById(R.id.detailContent);

        dateLabel = view.findViewById(R.id.dateText);
        pickDateButton = view.findViewById(R.id.pickDateButton);
        weekdayGroup = view.findViewById(R.id.weekdayGroup);
        routesHeader = view.findViewById(R.id.headerText);
        routesRecycler = view.findViewById(R.id.routesRecycler);

        detailRouteBadge = view.findViewById(R.id.detailRouteBadge);
        detailDirectionLabel = view.findViewById(R.id.detailDirectionLabel);
        detailScheduleDate = view.findViewById(R.id.detailScheduleDate);
        detailRouteTitle = view.findViewById(R.id.detailRouteTitle);
        detailRouteSubtitle = view.findViewById(R.id.detailRouteSubtitle);
        stopsContainer = view.findViewById(R.id.stopsContainer);
        detailTripsHeader = view.findViewById(R.id.detailTripsHeader);
        tripsRecycler = view.findViewById(R.id.detailTripsRecycler);
        backButton = view.findViewById(R.id.backButton);

        routesAdapter = new RoutesAdapter(routes, this::onRouteSelected);
        routesRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        routesRecycler.setAdapter(routesAdapter);

        tripsAdapter = new TripsAdapter(trips, this::onTripSelected);
        tripsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        tripsRecycler.setAdapter(tripsAdapter);
        tripsRecycler.setNestedScrollingEnabled(false);

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
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    public void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (mapView != null) {
            clearMapOverlays();
            mapView.onDetach();
            mapView = null;
        }
        networkExecutor.shutdownNow();
        super.onDestroyView();
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
        dateLabel.setText(selectedDate.format(displayDateFormatter));
    }

    private void showRoutesView() {
        routesContent.setVisibility(View.VISIBLE);
        detailContent.setVisibility(View.GONE);
        backButton.setVisibility(View.GONE);
        hideMap();
    }

    private void showDetailView() {
        routesContent.setVisibility(View.GONE);
        detailContent.setVisibility(View.VISIBLE);
        backButton.setVisibility(View.VISIBLE);
    }

    private void fetchRoutes() {
        final String url = buildRoutesUrl();
        if (url == null) {
            return;
        }
        routesHeader.setText(getString(R.string.schedule_routes_header,
            selectedWeekday != null ? selectedWeekday : selectedDate.format(displayDateFormatter)));

        networkExecutor.execute(() -> {
            Request request = new Request.Builder().url(url).build();
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
            selectedRouteId = routeId;
            trips.clear();
            tripsAdapter.notifyDataSetChanged();
            stopsContainer.removeAllViews();
            showDetailView();
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
                            TripStop stop = new TripStop(
                                stopJson.optString("stopId", ""),
                                stopJson.optString("stopName", ""),
                                stopJson.isNull("latitude") ? null : stopJson.optDouble("latitude"),
                                stopJson.isNull("longitude") ? null : stopJson.optDouble("longitude"),
                                stopJson.optInt("arrivalTime", 0),
                                stopJson.optInt("departureTime", 0)
                            );
                            stopList.add(stop);
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
                        detailRouteBadge.setText(routeId);
                        detailScheduleDate.setText(getString(R.string.schedule_valid_from, selectedDate.format(displayDateFormatter)));
                        detailRouteTitle.setText(getString(R.string.schedule_no_trips));
                        detailRouteSubtitle.setText("");
                        stopsContainer.removeAllViews();
                        hideMap();
                        detailTripsHeader.setVisibility(View.GONE);
                    } else {
                        detailTripsHeader.setVisibility(View.VISIBLE);
                        TripItem first = trips.get(0);
                        selectTrip(first);
                    }
                });
            } catch (IOException | JSONException ignored) {
            }
        });
    }

    private void onRouteSelected(String routeId) {
        fetchTrips(routeId);
    }

    private void onTripSelected(TripItem item) {
        selectTrip(item);
    }

    private void selectTrip(TripItem trip) {
        selectedTrip = trip;
        tripsAdapter.setSelectedTripId(trip != null ? trip.tripId : -1);
        updateTripHeader(trip);
        populateStopsTimeline(trip);
        updateMapWithTrip(trip);
    }

    private void updateTripHeader(TripItem trip) {
        detailRouteBadge.setText(selectedRouteId != null ? selectedRouteId : "");
        detailScheduleDate.setText(getString(R.string.schedule_valid_from, selectedDate.format(displayDateFormatter)));
        if (detailDirectionLabel != null) {
            detailDirectionLabel.setText(R.string.schedule_change_direction);
        }
        if (trip == null || trip.stops.isEmpty()) {
            detailRouteTitle.setText(getString(R.string.schedule_no_trips));
            detailRouteSubtitle.setText("");
            return;
        }
        TripStop first = trip.stops.get(0);
        TripStop last = trip.stops.get(trip.stops.size() - 1);
        detailRouteTitle.setText(getString(R.string.schedule_direction_format, first.stopName, last.stopName));
        detailRouteSubtitle.setText(getString(R.string.schedule_direction_format, last.stopName, first.stopName));
    }

    private void populateStopsTimeline(TripItem trip) {
        stopsContainer.removeAllViews();
        if (trip == null || trip.stops.isEmpty()) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        int baseSeconds = trip.stops.get(0).arrivalSeconds;
        int size = trip.stops.size();
        int[] minutesByIndex = new int[size];
        int highlightIndex = -1;
        for (int i = 0; i < size; i++) {
            TripStop stop = trip.stops.get(i);
            int offset = Math.max(0, stop.arrivalSeconds - baseSeconds);
            int minutes = offset / 60;
            minutesByIndex[i] = minutes;
            if (highlightIndex == -1 && minutes > 0) {
                highlightIndex = i;
            }
        }
        if (highlightIndex == -1) {
            highlightIndex = 0;
        }

        int highlightColor = requireContext().getColor(R.color.primary_blue);
        int primaryColor = requireContext().getColor(R.color.text_primary);
        int secondaryColor = requireContext().getColor(R.color.text_secondary);

        for (int i = 0; i < size; i++) {
            TripStop stop = trip.stops.get(i);
            View itemView = inflater.inflate(R.layout.item_schedule_stop, stopsContainer, false);
            boolean isHighlighted = i == highlightIndex;

            TextView timeLabel = itemView.findViewById(R.id.stopTimeLabel);
            timeLabel.setText(getString(R.string.schedule_minutes_label, minutesByIndex[i]));
            timeLabel.setTextColor(isHighlighted ? highlightColor : secondaryColor);

            TextView stopNameLabel = itemView.findViewById(R.id.stopNameLabel);
            stopNameLabel.setText(stop.stopName);
            stopNameLabel.setTextColor(isHighlighted ? highlightColor : primaryColor);

            TextView stopCodeLabel = itemView.findViewById(R.id.stopCodeLabel);
            if (stop.stopId == null || stop.stopId.isEmpty()) {
                stopCodeLabel.setVisibility(View.GONE);
            } else {
                stopCodeLabel.setVisibility(View.VISIBLE);
                stopCodeLabel.setText(stop.stopId);
                stopCodeLabel.setTextColor(isHighlighted ? highlightColor : secondaryColor);
            }

            View topLine = itemView.findViewById(R.id.timelineTop);
            View bottomLine = itemView.findViewById(R.id.timelineBottom);
            topLine.setVisibility(i == 0 ? View.INVISIBLE : View.VISIBLE);
            bottomLine.setVisibility(i == size - 1 ? View.INVISIBLE : View.VISIBLE);

            View timelineDot = itemView.findViewById(R.id.timelineDot);
            timelineDot.setBackgroundResource(isHighlighted
                ? R.drawable.bg_timeline_dot_primary
                : R.drawable.bg_timeline_dot_grey);

            itemView.setSelected(isHighlighted);
            stopsContainer.addView(itemView);
        }
    }

    private void updateMapWithTrip(TripItem trip) {
        if (mapView == null) {
            return;
        }
        clearMapOverlays();
        if (trip == null) {
            hideMap();
            return;
        }
        List<GeoPoint> points = new ArrayList<>();
        for (TripStop stop : trip.stops) {
            if (stop.latitude != null && stop.longitude != null) {
                points.add(new GeoPoint(stop.latitude, stop.longitude));
            }
        }
        if (points.size() < 2) {
            hideMap();
            return;
        }
        mapView.setVisibility(View.VISIBLE);
        if (routePolyline == null) {
            routePolyline = new Polyline();
        } else {
            mapView.getOverlays().remove(routePolyline);
        }
        routePolyline.setPoints(points);
        routePolyline.setWidth(6f);
        routePolyline.setColor(requireContext().getColor(R.color.primary_blue));
        mapView.getOverlays().add(routePolyline);

        for (TripStop stop : trip.stops) {
            if (stop.latitude == null || stop.longitude == null) {
                continue;
            }
            Marker marker = new Marker(mapView);
            marker.setPosition(new GeoPoint(stop.latitude, stop.longitude));
            marker.setTitle(stop.stopName);
            marker.setSubDescription(stop.stopId);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            mapView.getOverlays().add(marker);
            stopMarkers.add(marker);
        }
        try {
            BoundingBox box = BoundingBox.fromGeoPointsSafe(points);
            mapView.zoomToBoundingBox(box, true, 64);
        } catch (IllegalArgumentException ignored) {
        }
        mapView.invalidate();
    }

    private void hideMap() {
        if (mapView != null) {
            mapView.setVisibility(View.GONE);
            clearMapOverlays();
        }
    }

    private void clearMapOverlays() {
        if (mapView == null) {
            return;
        }
        if (routePolyline != null) {
            mapView.getOverlays().remove(routePolyline);
        }
        routePolyline = null;
        for (Marker marker : stopMarkers) {
            mapView.getOverlays().remove(marker);
        }
        stopMarkers.clear();
        mapView.invalidate();
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
        candidates.add(sanitizeBaseUrl("http://127.0.0.1:8080/"));
        candidates.add(sanitizeBaseUrl("http://localhost:8080/"));
        candidates.remove(null);
        return candidates.toArray(new String[0]);
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
        private long selectedTripId = -1;

        TripsAdapter(List<TripItem> items, TripClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        void setSelectedTripId(long selectedTripId) {
            this.selectedTripId = selectedTripId;
            notifyDataSetChanged();
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
            String timeLabel = item.displayStart == null || item.displayStart.isEmpty()
                    ? item.rawStartTime
                    : item.displayStart;
            holder.title.setText(holder.itemView.getContext().getString(R.string.schedule_trip_title, item.tripId));
            holder.meta.setText(String.format(Locale.getDefault(), "%s • %d stops", timeLabel, item.stops.size()));

            MaterialCardView card = (MaterialCardView) holder.itemView;
            if (item.tripId == selectedTripId) {
                card.setStrokeWidth(4);
                card.setStrokeColor(holder.itemView.getContext().getColor(R.color.primary_blue));
            } else {
                card.setStrokeWidth(0);
            }

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
        final Double latitude;
        final Double longitude;
        final int arrivalSeconds;
        final int departureSeconds;

        TripStop(String stopId, String stopName, Double latitude, Double longitude, int arrivalSeconds, int departureSeconds) {
            this.stopId = stopId;
            this.stopName = stopName;
            this.latitude = latitude;
            this.longitude = longitude;
            this.arrivalSeconds = arrivalSeconds;
            this.departureSeconds = departureSeconds;
        }
    }
}
