package com.bebraradar.ui.screens;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bebraradar.R;
import com.bebraradar.model.RouteStop;
import com.bebraradar.model.Train;
import com.bebraradar.ui.adapter.RouteStopsAdapter;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.card.MaterialCardView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.Overlay;
import android.util.DisplayMetrics;
import android.util.TypedValue;

public class TrainRouteFragment extends Fragment {

    private static final long SIMULATION_INTERVAL_MS = 1000L;
    private static final String SIMULATED_ROUTE_ASSET = "simulated_route.json";
    private static final String DEFAULT_DELAY_TEXT = "12 min";
    private static final double TRAIN_HIGHLIGHT_RADIUS_METERS = 120.0;

    public interface Listener {
        void onCloseRoute();
    }

    private Listener listener;
    private final List<RouteStop> stops = new ArrayList<>();
    private RouteStopsAdapter adapter;

    private MapView mapView;
    private MaterialCardView mapCard;
    private MaterialCardView routeBottomSheet;
    private BottomSheetBehavior<MaterialCardView> bottomSheetBehavior;
    private View mapTapOverlay;
    private View mapExpandHint;
    private TextView mapHintText;
    private ImageButton collapseMapButton;
    private View mapTopBar;
    private boolean mapExpanded = false;
    private int bottomSheetCollapsedPeek;
    private int bottomSheetExpandedPeek;
    private int mapCollapsedHeight;
    private int mapHorizontalMargin;
    private int mapCollapsedTopMargin;
    private final List<Marker> stopMarkers = new ArrayList<>();
    private Polyline routePolyline;
    private Marker busMarker;
    private Marker trainMarker;
    private Polyline busTrack;
    private Polyline trainTrack;
    private Polygon trainHighlightCircle;
    private DelayOverlay trainDelayOverlay;
    private final List<GeoPoint> busTrackPoints = new ArrayList<>();
    private final List<GeoPoint> trainTrackPoints = new ArrayList<>();
    private final List<RouteSample> simulatedRoute = new ArrayList<>();
    private BoundingBox simulatedRouteBounds;
    private Handler simulationHandler;
    private boolean simulationRunning;
    private int simulationIndex;
    private boolean highlightToggle;
    private RouteSample lastSimulationSample;
    private final Runnable simulationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!simulationRunning || simulationHandler == null) {
                return;
            }
            advanceSimulation();
            simulationHandler.postDelayed(this, SIMULATION_INTERVAL_MS);
        }
    };

    private TextView routeTitle;
    private TextView routeTrainName;
    private TextView routeTrainNumber;
    private TextView routeRunsStatus;
    private TextView routeDeparture;
    private TextView routeDepartureMeta;
    private TextView routeArrival;
    private TextView routeArrivalMeta;
    private TextView routeDurationText;
    private TextView routeSubtitle;
    private Train currentTrain;
    private String currentDelayText = DEFAULT_DELAY_TEXT;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_train_route, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ImageButton backButton = view.findViewById(R.id.backToResultsButton);
        ImageButton shareButton = view.findViewById(R.id.shareRouteButton);
        ImageButton refreshButton = view.findViewById(R.id.refreshRouteButton);

        routeTitle = view.findViewById(R.id.routeTitle);
        routeSubtitle = view.findViewById(R.id.routeSubtitle);
        routeTrainName = view.findViewById(R.id.routeTrainName);
        routeTrainNumber = view.findViewById(R.id.routeTrainNumber);
        routeRunsStatus = view.findViewById(R.id.routeRunsStatus);
        routeDeparture = view.findViewById(R.id.routeDeparture);
        routeDepartureMeta = view.findViewById(R.id.routeDepartureMeta);
        routeArrival = view.findViewById(R.id.routeArrival);
        routeArrivalMeta = view.findViewById(R.id.routeArrivalMeta);
        routeDurationText = view.findViewById(R.id.routeDurationText);

        RecyclerView stopsRecycler = view.findViewById(R.id.routeStopsRecyclerView);
        adapter = new RouteStopsAdapter(requireContext(), stops);
        stopsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        stopsRecycler.setAdapter(adapter);

        setupMap(view);
        setupBottomSheet(view);

        backButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCloseRoute();
            }
        });

        shareButton.setOnClickListener(v -> Toast.makeText(requireContext(), R.string.toast_feature_soon, Toast.LENGTH_SHORT).show());
        refreshButton.setOnClickListener(v -> Toast.makeText(requireContext(), R.string.toast_feature_soon, Toast.LENGTH_SHORT).show());

        if (currentTrain == null) {
            applyDefaultContent();
        } else {
            bindTrain(currentTrain);
        }
    }

    public void updateTrainData(@NonNull Train train, @NonNull List<RouteStop> newStops) {
        currentTrain = train;
        stops.clear();
        stops.addAll(newStops);
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (getView() != null) {
            bindTrain(train);
        }
    }

    private void bindTrain(@NonNull Train train) {
        if (routeTrainName == null) {
            return;
        }
        routeSubtitle.setText(getString(R.string.route_subtitle_format, train.getDepartureStation(), train.getArrivalStation()));
        routeTrainName.setText(train.getName());
        routeTrainNumber.setText(getString(R.string.train_number_label, train.getNumber()));
        routeRunsStatus.setText(train.isRunsDaily() ? R.string.label_runs_daily : R.string.label_runs_on_schedule);
        routeDeparture.setText(train.getDepartureTime());
        routeDepartureMeta.setText(getString(R.string.route_meta_format, train.getDepartureDate(), train.getDepartureStation()));
        routeArrival.setText(train.getArrivalTime());
        routeArrivalMeta.setText(getString(R.string.route_meta_format, train.getArrivalDate(), train.getArrivalStation()));
        routeDurationText.setText(getString(R.string.train_duration_format, train.getDuration()));
        currentDelayText = resolveDelayTextForTrain(train);
        updateDelayOverlay(lastSimulationSample != null ? lastSimulationSample.trainPoint : null);
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateMapContent();
    }

    private void applyDefaultContent() {
        Train defaultTrain = new Train(
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
        );
        List<RouteStop> defaultStops = createDefaultStops();
        updateTrainData(defaultTrain, defaultStops);
    }

    private List<RouteStop> createDefaultStops() {
        List<RouteStop> defaultStops = new ArrayList<>();
        defaultStops.add(new RouteStop("2:21 am", "Bandra Terminus", "0 km Platform", "2:30 am", true, false, 19.0546, 72.8406));
        defaultStops.add(new RouteStop("3:12 am", "Andheri", "0 km Platform", "3:22 am", true, true, 19.1188, 72.8467));
        defaultStops.add(new RouteStop("3:12 am", "Borivali", "0 km Platform", "4:22 am", false, false, 19.2295, 72.8570));
        return defaultStops;
    }

    private void setupMap(@NonNull View root) {
        mapCard = root.findViewById(R.id.mapCard);
        mapView = root.findViewById(R.id.liveMapView);
        mapTopBar = root.findViewById(R.id.mapTopBar);
        mapTapOverlay = root.findViewById(R.id.mapTapOverlay);
        mapExpandHint = root.findViewById(R.id.mapExpandHint);
        mapHintText = root.findViewById(R.id.mapHintText);
        collapseMapButton = root.findViewById(R.id.collapseMapButton);

        Configuration.getInstance().setUserAgentValue(requireContext().getApplicationContext().getPackageName());
        mapView.setMultiTouchControls(true);
        mapView.setTilesScaledToDpi(true);

        mapCollapsedHeight = getResources().getDimensionPixelSize(R.dimen.map_collapsed_height);
        mapHorizontalMargin = getResources().getDimensionPixelSize(R.dimen.page_padding);
        mapCollapsedTopMargin = getResources().getDimensionPixelSize(R.dimen.map_collapsed_top_margin);

        mapTapOverlay.setOnClickListener(v -> expandMap());
        collapseMapButton.setOnClickListener(v -> collapseMap());

        loadSimulatedRoute();
        initializeSimulationLayers();
        resetSimulationState();
        applyCollapsedMapLayout();
    }

    private void setupBottomSheet(@NonNull View root) {
        routeBottomSheet = root.findViewById(R.id.routeBottomSheet);
        bottomSheetBehavior = BottomSheetBehavior.from(routeBottomSheet);
        bottomSheetCollapsedPeek = getResources().getDimensionPixelSize(R.dimen.bottom_sheet_peek_collapsed);
        bottomSheetExpandedPeek = getResources().getDimensionPixelSize(R.dimen.bottom_sheet_peek_expanded);
        setBottomSheetForCollapsedMap();
        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (!mapExpanded && newState != BottomSheetBehavior.STATE_EXPANDED) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                    return;
                }
                if (newState == BottomSheetBehavior.STATE_EXPANDED && mapExpanded) {
                    collapseMap();
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                // no-op
            }
        });
    }

    private void expandMap() {
        if (mapExpanded) {
            return;
        }
        mapExpanded = true;
        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) mapCard.getLayoutParams();
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        params.setMargins(0, 0, 0, 0);
        mapCard.setLayoutParams(params);
        mapCard.setRadius(0f);
        mapCard.setCardElevation(0f);
        mapCard.setClipToOutline(false);
        mapTapOverlay.setVisibility(View.GONE);
        mapExpandHint.setVisibility(View.GONE);
        collapseMapButton.setVisibility(View.VISIBLE);
        mapHintText.setText(R.string.map_collapse_hint);
        setBottomSheetForExpandedMap();
        updateHeaderColors(true);
    }

    private void collapseMap() {
        if (!mapExpanded) {
            return;
        }
        applyCollapsedMapLayout();
        setBottomSheetForCollapsedMap();
    }

    private void applyCollapsedMapLayout() {
        mapExpanded = false;
        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) mapCard.getLayoutParams();
        params.height = mapCollapsedHeight;
        params.setMargins(mapHorizontalMargin, mapCollapsedTopMargin, mapHorizontalMargin, 0);
        mapCard.setLayoutParams(params);
        mapCard.setRadius(getResources().getDimension(R.dimen.card_corner_radius_large));
        mapCard.setCardElevation(getResources().getDimension(R.dimen.card_elevation_small));
        mapCard.setClipToOutline(true);
        mapTapOverlay.setVisibility(View.VISIBLE);
        mapExpandHint.setVisibility(View.VISIBLE);
        collapseMapButton.setVisibility(View.GONE);
        mapHintText.setText(R.string.map_expand_hint);
        updateHeaderColors(false);
    }

    @NonNull
    private String resolveDelayTextForTrain(@NonNull Train train) {
        return DEFAULT_DELAY_TEXT;
    }

    private void updateDelayOverlay(@Nullable GeoPoint point) {
        if (mapView == null) {
            return;
        }
        if (trainDelayOverlay == null) {
            trainDelayOverlay = new DelayOverlay(requireContext());
            addOverlayIfMissing(trainDelayOverlay);
        }
        String label = point == null ? null : getString(R.string.route_delay_label, currentDelayText);
        trainDelayOverlay.update(point, label);
        trainDelayOverlay.setVisible(point != null);
        if (point != null) {
            bringOverlayToFront(trainDelayOverlay);
        }
        mapView.invalidate();
        if (trainMarker != null && lastSimulationSample != null) {
            updateMarkerDetails(trainMarker,
                    lastSimulationSample.trainPoint,
                    lastSimulationSample.timeHms,
                    lastSimulationSample.trainTripId);
        }
    }

    private void loadSimulatedRoute() {
        simulatedRoute.clear();
        simulatedRouteBounds = null;
        busTrackPoints.clear();
        trainTrackPoints.clear();
        lastSimulationSample = null;

        AssetManager assetManager = requireContext().getAssets();
        try (InputStream inputStream = assetManager.open(SIMULATED_ROUTE_ASSET);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            if (builder.length() == 0) {
                return;
            }
            JSONArray array = new JSONArray(builder.toString());
            double minLat = Double.MAX_VALUE;
            double maxLat = -Double.MAX_VALUE;
            double minLon = Double.MAX_VALUE;
            double maxLon = -Double.MAX_VALUE;
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                double busLat = item.optDouble("busLat", Double.NaN);
                double busLon = item.optDouble("busLon", Double.NaN);
                double trainLat = item.optDouble("trainLat", Double.NaN);
                double trainLon = item.optDouble("trainLon", Double.NaN);
                if (Double.isNaN(busLat) || Double.isNaN(busLon) || Double.isNaN(trainLat) || Double.isNaN(trainLon)) {
                    continue;
                }
                RouteSample sample = new RouteSample(
                        item.optInt("index", i),
                        item.optDouble("timeSec", i),
                        item.optString("timeHms", ""),
                        busLat,
                        busLon,
                        trainLat,
                        trainLon,
                        item.optString("busTripId", ""),
                        item.optString("trainTripId", "")
                );
                simulatedRoute.add(sample);

                minLat = Math.min(Math.min(minLat, busLat), trainLat);
                maxLat = Math.max(Math.max(maxLat, busLat), trainLat);
                minLon = Math.min(Math.min(minLon, busLon), trainLon);
                maxLon = Math.max(Math.max(maxLon, busLon), trainLon);
            }
            if (!simulatedRoute.isEmpty() && !Double.isInfinite(minLat) && !Double.isInfinite(maxLat)
                    && !Double.isInfinite(minLon) && !Double.isInfinite(maxLon)) {
                simulatedRouteBounds = new BoundingBox(maxLat, maxLon, minLat, minLon);
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    private void initializeSimulationLayers() {
        if (mapView == null || simulatedRoute.isEmpty()) {
            return;
        }

        if (simulationHandler == null) {
            simulationHandler = new Handler(Looper.getMainLooper());
        }

        if (busTrack == null) {
            busTrack = new Polyline();
            busTrack.setColor(ContextCompat.getColor(requireContext(), R.color.route_bus));
            busTrack.setWidth(5f);
        }
        addOverlayIfMissing(busTrack);

        if (trainTrack == null) {
            trainTrack = new Polyline();
            trainTrack.setColor(ContextCompat.getColor(requireContext(), R.color.primary_blue));
            trainTrack.setWidth(5f);
        }
        addOverlayIfMissing(trainTrack);

        if (trainHighlightCircle == null) {
            trainHighlightCircle = new Polygon();
            trainHighlightCircle.setStrokeWidth(6f);
            trainHighlightCircle.setStrokeColor(Color.TRANSPARENT);
            trainHighlightCircle.setFillColor(Color.TRANSPARENT);
        }
        addOverlayIfMissing(trainHighlightCircle);

        if (busMarker == null) {
            busMarker = new Marker(mapView);
            busMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            Drawable icon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_bus);
            if (icon != null) {
                busMarker.setIcon(icon);
            }
            busMarker.setTitle(getString(R.string.simulation_label_bus));
        }
        addOverlayIfMissing(busMarker);

        if (trainMarker == null) {
            trainMarker = new Marker(mapView);
            trainMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            Drawable icon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_train);
            if (icon != null) {
                trainMarker.setIcon(icon);
            }
            trainMarker.setTitle(getString(R.string.simulation_label_train));
        }
        addOverlayIfMissing(trainMarker);
    }

    private void resetSimulationState() {
        simulationIndex = 0;
        if (simulatedRoute.isEmpty()) {
            return;
        }
        initializeSimulationLayers();
        highlightToggle = false;
        applySimulationSample(simulatedRoute.get(0), true);
        if (simulatedRoute.size() > 1) {
            simulationIndex = 1;
        }
    }

    private void advanceSimulation() {
        if (mapView == null || simulatedRoute.isEmpty()) {
            return;
        }
        if (simulationIndex == 0 && (!busTrackPoints.isEmpty() || !trainTrackPoints.isEmpty())) {
            applySimulationSample(simulatedRoute.get(0), true);
            simulationIndex = simulatedRoute.size() > 1 ? 1 : 0;
            return;
        }
        applySimulationSample(simulatedRoute.get(simulationIndex), false);
        simulationIndex = (simulationIndex + 1) % simulatedRoute.size();
    }

    private void applySimulationSample(@NonNull RouteSample sample, boolean resetTrack) {
        if (mapView == null) {
            return;
        }
        initializeSimulationLayers();
        if (resetTrack) {
            busTrackPoints.clear();
            trainTrackPoints.clear();
            highlightToggle = false;
            if (busTrack != null) {
                busTrack.setPoints(new ArrayList<>());
            }
            if (trainTrack != null) {
                trainTrack.setPoints(new ArrayList<>());
            }
        }

        if (busMarker != null) {
            updateMarkerDetails(busMarker, sample.busPoint, sample.timeHms, sample.busTripId);
        }
        if (trainMarker != null) {
            updateMarkerDetails(trainMarker, sample.trainPoint, sample.timeHms, sample.trainTripId);
        }

        busTrackPoints.add(sample.busPoint);
        trainTrackPoints.add(sample.trainPoint);
        lastSimulationSample = sample;
        if (busTrack != null) {
            busTrack.setPoints(new ArrayList<>(busTrackPoints));
        }
        if (trainTrack != null) {
            trainTrack.setPoints(new ArrayList<>(trainTrackPoints));
        }

        updateDelayOverlay(sample.trainPoint);
        updateTrainHighlight(sample.trainPoint, resetTrack);

        if (busMarker != null) {
            bringOverlayToFront(busMarker);
        }
        if (trainMarker != null) {
            bringOverlayToFront(trainMarker);
        }

        mapView.invalidate();
    }

    private void updateTrainHighlight(@NonNull GeoPoint center, boolean resetToggle) {
        if (trainHighlightCircle == null) {
            return;
        }
        if (resetToggle) {
            highlightToggle = false;
        }
        List<GeoPoint> circlePoints = Polygon.pointsAsCircle(center, TRAIN_HIGHLIGHT_RADIUS_METERS);
        trainHighlightCircle.setPoints(circlePoints);
        highlightToggle = !highlightToggle;
        int strokeColor = highlightToggle
                ? ContextCompat.getColor(requireContext(), R.color.alert_red)
                : Color.TRANSPARENT;
        int fillColor = highlightToggle
                ? ColorUtils.setAlphaComponent(strokeColor, 48)
                : Color.TRANSPARENT;
        trainHighlightCircle.setStrokeColor(strokeColor);
        trainHighlightCircle.setFillColor(fillColor);
        bringOverlayToFront(trainHighlightCircle);
    }

    private void updateMarkerDetails(@NonNull Marker marker, @NonNull GeoPoint point,
                                     @NonNull String timeHms, @NonNull String tripId) {
        marker.setPosition(point);
        marker.setSnippet(getString(R.string.simulation_marker_time, timeHms));
        String description = getString(R.string.simulation_marker_trip, tripId);
        if (marker == trainMarker) {
            description = description + "\n" + getString(R.string.route_delay_label, currentDelayText);
        }
        marker.setSubDescription(description);
    }

    private void addOverlayIfMissing(@NonNull Overlay overlay) {
        if (mapView == null) {
            return;
        }
        List<Overlay> overlays = mapView.getOverlays();
        if (!overlays.contains(overlay)) {
            overlays.add(overlay);
        }
    }

    private void bringOverlayToFront(@NonNull Overlay overlay) {
        if (mapView == null) {
            return;
        }
        List<Overlay> overlays = mapView.getOverlays();
        overlays.remove(overlay);
        overlays.add(overlay);
    }

    private void startSimulation() {
        if (simulatedRoute.isEmpty()) {
            return;
        }
        if (simulationHandler == null) {
            simulationHandler = new Handler(Looper.getMainLooper());
        }
        if (simulatedRoute.size() == 1) {
            applySimulationSample(simulatedRoute.get(0), true);
            return;
        }
        if (simulationRunning) {
            return;
        }
        simulationRunning = true;
        simulationHandler.post(simulationRunnable);
    }

    private void stopSimulation() {
        simulationRunning = false;
        if (simulationHandler != null) {
            simulationHandler.removeCallbacks(simulationRunnable);
        }
        if (trainDelayOverlay != null) {
            trainDelayOverlay.setVisible(false);
        }
    }

    private static class DelayOverlay extends Overlay {

        private final Paint backgroundPaint;
        private final Paint textPaint;
        private final RectF bubbleRect = new RectF();
        private final float paddingPx;
        private final float cornerRadiusPx;
        private final float verticalOffsetPx;
        @Nullable
        private GeoPoint center;
        @Nullable
        private String text;
        private boolean visible;

        DelayOverlay(@NonNull Context context) {
            super();
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            paddingPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, metrics);
            cornerRadiusPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, metrics);
            verticalOffsetPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56f, metrics);

            backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            backgroundPaint.setColor(ContextCompat.getColor(context, R.color.alert_red));

            textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, metrics));
        }

        void update(@Nullable GeoPoint newCenter, @Nullable String newText) {
            center = newCenter;
            text = newText;
        }

        void setVisible(boolean isVisible) {
            visible = isVisible;
        }

        @Override
        public void draw(Canvas canvas, MapView mapView, boolean shadow) {
            if (shadow || !visible || center == null || text == null || text.isEmpty()) {
                return;
            }
            Projection projection = mapView.getProjection();
            Point screenPoint = projection.toPixels(center, null);
            if (screenPoint == null) {
                return;
            }
            float baselineY = screenPoint.y - verticalOffsetPx;
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float top = baselineY + metrics.top - paddingPx;
            float bottom = baselineY + metrics.bottom + paddingPx;
            float textWidth = textPaint.measureText(text);
            float halfWidth = textWidth / 2f;
            bubbleRect.set(screenPoint.x - halfWidth - paddingPx,
                    top,
                    screenPoint.x + halfWidth + paddingPx,
                    bottom);
            canvas.drawRoundRect(bubbleRect, cornerRadiusPx, cornerRadiusPx, backgroundPaint);
            canvas.drawText(text, screenPoint.x, baselineY, textPaint);
        }
    }

    private static class RouteSample {
        final int index;
        final double timeSec;
        final String timeHms;
        final GeoPoint busPoint;
        final GeoPoint trainPoint;
        final String busTripId;
        final String trainTripId;

        RouteSample(int index,
                    double timeSec,
                    @NonNull String timeHms,
                    double busLat,
                    double busLon,
                    double trainLat,
                    double trainLon,
                    @NonNull String busTripId,
                    @NonNull String trainTripId) {
            this.index = index;
            this.timeSec = timeSec;
            this.timeHms = timeHms;
            this.busPoint = new GeoPoint(busLat, busLon);
            this.trainPoint = new GeoPoint(trainLat, trainLon);
            this.busTripId = busTripId;
            this.trainTripId = trainTripId;
        }
    }


    private void setBottomSheetForCollapsedMap() {
        if (bottomSheetBehavior == null) {
            return;
        }
        bottomSheetBehavior.setSkipCollapsed(true);
        bottomSheetBehavior.setDraggable(false);
        bottomSheetBehavior.setPeekHeight(bottomSheetExpandedPeek, true);
        routeBottomSheet.post(() -> bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED));
    }

    private void setBottomSheetForExpandedMap() {
        if (bottomSheetBehavior == null) {
            return;
        }
        bottomSheetBehavior.setSkipCollapsed(false);
        bottomSheetBehavior.setDraggable(false);
        bottomSheetBehavior.setPeekHeight(bottomSheetCollapsedPeek, true);
        routeBottomSheet.post(() -> bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED));
    }

    private void updateHeaderColors(boolean onFullMap) {
        if (routeTitle == null || routeSubtitle == null) {
            return;
        }
        int titleColor = ContextCompat.getColor(requireContext(),
                onFullMap ? android.R.color.white : R.color.text_primary);
        int subtitleBase = ContextCompat.getColor(requireContext(),
                onFullMap ? android.R.color.white : R.color.text_secondary);
        int subtitleColor = onFullMap ? ColorUtils.setAlphaComponent(subtitleBase, 200) : subtitleBase;
        routeTitle.setTextColor(titleColor);
        routeSubtitle.setTextColor(subtitleColor);

        int scrimColor = onFullMap
                ? ColorUtils.setAlphaComponent(ContextCompat.getColor(requireContext(), R.color.text_primary), 72)
                : ContextCompat.getColor(requireContext(), android.R.color.transparent);
        mapTopBar.setBackgroundColor(scrimColor);
    }

    private void updateMapContent() {
        if (mapView == null) {
            return;
        }

        if (routePolyline != null) {
            mapView.getOverlays().remove(routePolyline);
            routePolyline = null;
        }
        for (Marker marker : stopMarkers) {
            mapView.getOverlays().remove(marker);
        }
        stopMarkers.clear();

        List<GeoPoint> stopPoints = new ArrayList<>();
        List<Marker> newMarkers = new ArrayList<>();
        for (RouteStop stop : stops) {
            if (!stop.hasLocation()) {
                continue;
            }
            GeoPoint point = new GeoPoint(stop.getLatitude(), stop.getLongitude());
            stopPoints.add(point);

            Marker marker = new Marker(mapView);
            marker.setPosition(point);
            marker.setTitle(stop.getStationName());
            marker.setSnippet(stop.getScheduleTime());
            marker.setSubDescription(stop.getStatusText());
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

            Drawable pin = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_pin);
            if (pin != null) {
                Drawable tinted = pin.mutate();
                int color;
                if (stop.isCurrent()) {
                    color = ContextCompat.getColor(requireContext(), R.color.primary_blue);
                } else if (stop.isCompleted()) {
                    color = ContextCompat.getColor(requireContext(), R.color.success_green);
                } else {
                    color = ContextCompat.getColor(requireContext(), R.color.alert_red);
                }
                DrawableCompat.setTint(tinted, color);
                marker.setIcon(tinted);
            }

            newMarkers.add(marker);
        }

        stopMarkers.addAll(newMarkers);

        if (stopPoints.size() > 1) {
            routePolyline = new Polyline();
            routePolyline.setPoints(stopPoints);
            routePolyline.setColor(ContextCompat.getColor(requireContext(), R.color.primary_blue));
            routePolyline.setWidth(4f);
            mapView.getOverlays().add(routePolyline);
        }

        for (Marker marker : stopMarkers) {
            mapView.getOverlays().add(marker);
        }

        if (busMarker != null) {
            bringOverlayToFront(busMarker);
        }
        if (trainMarker != null) {
            bringOverlayToFront(trainMarker);
        }

        BoundingBox bounds = null;
        if (!stopPoints.isEmpty()) {
            if (stopPoints.size() == 1 && simulatedRouteBounds == null) {
                mapView.getController().setZoom(12.5);
                mapView.getController().setCenter(stopPoints.get(0));
                mapView.invalidate();
                return;
            }
            bounds = BoundingBox.fromGeoPoints(stopPoints);
        }

        if (simulatedRouteBounds != null) {
            if (bounds == null) {
                bounds = simulatedRouteBounds;
            } else {
                double north = Math.max(bounds.getLatNorth(), simulatedRouteBounds.getLatNorth());
                double east = Math.max(bounds.getLonEast(), simulatedRouteBounds.getLonEast());
                double south = Math.min(bounds.getLatSouth(), simulatedRouteBounds.getLatSouth());
                double west = Math.min(bounds.getLonWest(), simulatedRouteBounds.getLonWest());
                bounds = new BoundingBox(north, east, south, west);
            }
        }

        if (bounds != null) {
            final BoundingBox finalBounds = bounds;
            mapView.post(() -> mapView.zoomToBoundingBox(finalBounds, true));
        }

        mapView.invalidate();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Listener) {
            listener = (Listener) context;
        } else {
            throw new IllegalStateException("Parent activity must implement TrainRouteFragment.Listener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        startSimulation();
    }

    @Override
    public void onPause() {
        stopSimulation();
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        stopSimulation();
        if (mapView != null) {
            if (busMarker != null) {
                mapView.getOverlays().remove(busMarker);
            }
            if (trainMarker != null) {
                mapView.getOverlays().remove(trainMarker);
            }
            if (busTrack != null) {
                mapView.getOverlays().remove(busTrack);
            }
            if (trainTrack != null) {
                mapView.getOverlays().remove(trainTrack);
            }
            if (trainDelayOverlay != null) {
                mapView.getOverlays().remove(trainDelayOverlay);
            }
            if (trainHighlightCircle != null) {
                mapView.getOverlays().remove(trainHighlightCircle);
            }
        }
        busMarker = null;
        trainMarker = null;
        busTrack = null;
        trainTrack = null;
        trainDelayOverlay = null;
        trainHighlightCircle = null;
        highlightToggle = false;
        busTrackPoints.clear();
        trainTrackPoints.clear();
        if (mapView != null) {
            mapView.onDetach();
            mapView = null;
        }
        super.onDestroyView();
    }
}
