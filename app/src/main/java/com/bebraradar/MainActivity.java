package com.bebraradar;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.bebraradar.BuildConfig;
import com.bebraradar.model.RouteStop;
import com.bebraradar.model.Train;
import com.bebraradar.ui.screens.SearchFragment;
import com.bebraradar.ui.screens.TrainResultsFragment;
import com.bebraradar.ui.screens.TrainRouteFragment;
import com.bebraradar.ui.screens.ScheduleFragment;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity
        implements SearchFragment.Listener,
        TrainResultsFragment.Listener,
        TrainRouteFragment.Listener {
    private final OkHttpClient httpClient = new OkHttpClient();
    private String[] apiBaseEndpoints;

    private ViewPager2 viewPager;
    private ScreensPagerAdapter pagerAdapter;
    private ExecutorService networkExecutor;
    private MaterialButtonToggleGroup bottomNavGroup;
    private boolean updatingNavigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewPager = findViewById(R.id.mainViewPager);
        pagerAdapter = new ScreensPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setUserInputEnabled(false);
        viewPager.setOffscreenPageLimit(pagerAdapter.getItemCount());
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateNavigation(position);
            }
        });

        bottomNavGroup = findViewById(R.id.bottomNavGroup);
        if (bottomNavGroup != null) {
            bottomNavGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked || updatingNavigation) {
                    return;
                }
                Integer targetIndex = getIndexForButton(checkedId);
                if (targetIndex != null && targetIndex != viewPager.getCurrentItem()) {
                    navigateToPage(targetIndex);
                }
            });
            updateNavigation(0);
        }

        apiBaseEndpoints = buildApiEndpointCandidates();
        networkExecutor = Executors.newSingleThreadExecutor();
        checkApiAvailability();
    }

    private void navigateToPage(int index) {
        viewPager.setCurrentItem(index, true);
    }

    private void updateNavigation(int selectedIndex) {
        if (bottomNavGroup == null) {
            return;
        }
        Integer buttonId = getButtonIdForIndex(selectedIndex);
        if (buttonId == null) {
            return;
        }
        updatingNavigation = true;
        bottomNavGroup.check(buttonId);
        updatingNavigation = false;
    }

    @Nullable
    private Integer getButtonIdForIndex(int index) {
        if (index == 0) {
            return R.id.navSearchButton;
        } else if (index == 1) {
            return R.id.navResultsButton;
        } else if (index == 2) {
            return R.id.navRouteButton;
        } else if (index == 3) {
            return R.id.navScheduleButton;
        }
        return null;
    }

    @Nullable
    private Integer getIndexForButton(int buttonId) {
        if (buttonId == R.id.navSearchButton) {
            return 0;
        } else if (buttonId == R.id.navResultsButton) {
            return 1;
        } else if (buttonId == R.id.navRouteButton) {
            return 2;
        } else if (buttonId == R.id.navScheduleButton) {
            return 3;
        }
        return null;
    }

    @Override
    public void onFindTrainsRequested(@NonNull String fromStation, @NonNull String toStation) {
        navigateToPage(1);
    }

    @Override
    public void onCloseResults() {
        navigateToPage(0);
    }

    @Override
    public void onTrainSelected(@NonNull Train train) {
        TrainRouteFragment routeFragment = pagerAdapter.getRouteFragment();
        routeFragment.updateTrainData(train, createRouteStops(train));
        navigateToPage(2);
    }

    @Override
    public void onCloseRoute() {
        navigateToPage(1);
    }

    private List<RouteStop> createRouteStops(@NonNull Train train) {
        List<RouteStop> stops = new ArrayList<>();
        if (train.getName().contains("Mysore")) {
            stops.add(new RouteStop("10:15", "Mysore Junction", "Platform 1", "On time", true, true, 12.3122, 76.6497));
            stops.add(new RouteStop("14:45", "Bangalore City", "Platform 3", "+5 min", true, false, 12.9789, 77.5713));
            stops.add(new RouteStop("22:10", "Ajmer Junction", "Platform 2", "Arrives", false, false, 26.4499, 74.6399));
        } else if (train.getName().contains("Rajdhani")) {
            stops.add(new RouteStop("10:15", "Mumbai Central", "Platform 5", "On time", true, true, 18.9690, 72.8194));
            stops.add(new RouteStop("18:45", "Vadodara", "Platform 2", "+3 min", true, false, 22.3107, 73.1926));
            stops.add(new RouteStop("04:20", "New Delhi", "Platform 1", "Expected", false, false, 28.6139, 77.2090));
        } else {
            stops.add(new RouteStop("2:21 am", "Bandra Terminus", "0 km Platform", "2:30 am", true, false, 19.0546, 72.8406));
            stops.add(new RouteStop("3:12 am", "Andheri", "0 km Platform", "3:22 am", true, true, 19.1188, 72.8467));
            stops.add(new RouteStop("3:12 am", "Borivali", "0 km Platform", "4:22 am", false, false, 19.2295, 72.8570));
        }
        return stops;
    }

    private void checkApiAvailability() {
        if (networkExecutor == null) {
            return;
        }
        networkExecutor.execute(() -> {
            boolean available = false;
            String reachableEndpoint = null;
            String routesId = null;
            for (String endpoint : apiBaseEndpoints) {
                Request request = new Request.Builder().url(endpoint).build();
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        available = true;
                        reachableEndpoint = endpoint;

                        routesId = fetchRoutesId(endpoint);
                        break;
                    }
                } catch (IOException ignored) {
                }
            }
            boolean finalAvailable = available;
            final String finalEndpoint = reachableEndpoint;
            final String finalRoutesId = routesId;
            showToastOnUiThread(finalAvailable
                    ? formatApiAvailableMessage(finalRoutesId)
                    : getString(R.string.toast_api_unavailable));
            if (!finalAvailable) {
                System.out.println("[API] Failed to reach any endpoint: " + String.join(", ", apiBaseEndpoints));
            } else {
                System.out.println("[API] Using endpoint: " + finalEndpoint);
            }
        });
    }

    private String formatApiAvailableMessage(String routesId) {
        if (routesId == null || routesId.isEmpty()) {
            return getString(R.string.toast_api_available);
        }
        return getString(R.string.toast_api_available_with_id, routesId);
    }

    private String fetchRoutesId(@NonNull String baseUrl) {
        String routesUrl = baseUrl + "routes";
        Request request = new Request.Builder()
                .url(routesUrl)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return null;
            }
            String body = response.body() != null ? response.body().string() : null;
            if (body == null || body.isEmpty()) {
                return null;
            }
            return parseFirstId(body);
        } catch (IOException e) {
            return null;
        }
    }

    private String parseFirstId(@NonNull String body) {
        int idIndex = body.indexOf("\"id\"");
        if (idIndex == -1) {
            return null;
        }
        int colonIndex = body.indexOf(':', idIndex);
        if (colonIndex == -1) {
            return null;
        }
        int startQuote = body.indexOf('"', colonIndex);
        int endQuote = -1;
        String parsedId = null;
        if (startQuote != -1) {
            endQuote = body.indexOf('"', startQuote + 1);
            if (endQuote > startQuote) {
                parsedId = body.substring(startQuote + 1, endQuote);
            }
        }
        if (parsedId == null) {
            // fallback for numeric id without quotes
            int start = colonIndex + 1;
            while (start < body.length() && Character.isWhitespace(body.charAt(start))) {
                start++;
            }
            int end = start;
            while (end < body.length() && Character.isDigit(body.charAt(end))) {
                end++;
            }
            if (end > start) {
                parsedId = body.substring(start, end);
            }
        }
        if (parsedId != null && !parsedId.isEmpty()) {
            return parsedId;
        }
        return null;
    }

    private String[] buildApiEndpointCandidates() {
        Set<String> candidates = new LinkedHashSet<>();
        String configured = sanitizeBaseUrl(BuildConfig.DEFAULT_API_BASE_URL);
        if (configured != null) {
            candidates.add(configured);
        }
        candidates.add(sanitizeBaseUrl("http://10.0.2.2:8080/"));
        candidates.add(sanitizeBaseUrl("http://10.0.2.2:8081/"));
        candidates.add(sanitizeBaseUrl("http://127.0.0.1:8080/"));
        candidates.add(sanitizeBaseUrl("http://127.0.0.1:8081/"));
        candidates.add(sanitizeBaseUrl("http://localhost:8080/"));
        candidates.add(sanitizeBaseUrl("http://localhost:8081/"));
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

    private void showToastOnUiThread(@NonNull String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkExecutor != null) {
            networkExecutor.shutdownNow();
            networkExecutor = null;
        }
    }

    private static class ScreensPagerAdapter extends FragmentStateAdapter {

        private final Fragment[] fragments;

        ScreensPagerAdapter(@NonNull AppCompatActivity activity) {
            super(activity);
            fragments = new Fragment[]{
                    new SearchFragment(),
                    new TrainResultsFragment(),
                    new TrainRouteFragment(),
                    new ScheduleFragment()
            };
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return fragments[position];
        }

        @Override
        public int getItemCount() {
            return fragments.length;
        }

        TrainRouteFragment getRouteFragment() {
            return (TrainRouteFragment) fragments[2];
        }
    }
}
