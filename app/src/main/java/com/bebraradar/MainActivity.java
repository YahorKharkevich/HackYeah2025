package com.bebraradar;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class MainActivity extends AppCompatActivity implements LocationListener, OnMapReadyCallback {

    private Button scanButton;
    private Button settingsButton;
    private Button getLocationButton;
    private TextView statusText;
    private TextView locationText;
    private TextView accuracyText;
    private boolean isScanning = false;
    
    // Геолокация
    private LocationManager locationManager;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private boolean isLocationEnabled = false;
    
    // Карта
    private GoogleMap googleMap;
    private SupportMapFragment mapFragment;
    private LatLng currentLocation;
    private boolean isMapReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Инициализация компонентов
        initViews();
        
        // Настройка обработчиков событий
        setupClickListeners();
        
        // Инициализация карты
        initMap();
    }
    
    private void initViews() {
        scanButton = findViewById(R.id.scanButton);
        settingsButton = findViewById(R.id.settingsButton);
        getLocationButton = findViewById(R.id.getLocationButton);
        statusText = findViewById(R.id.statusText);
        locationText = findViewById(R.id.locationText);
        accuracyText = findViewById(R.id.accuracyText);
        
        // Инициализация LocationManager
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
    }
    
    private void setupClickListeners() {
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleScanning();
            }
        });
        
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettings();
            }
        });
        
        getLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getCurrentLocation();
            }
        });
    }
    
    private void initMap() {
        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }
    
    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        isMapReady = true;
        
        // Настройка карты
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(true);
        
        // Установка начального местоположения (Москва)
        LatLng moscow = new LatLng(55.7558, 37.6176);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(moscow, 10));
        
        Toast.makeText(this, "Карта готова", Toast.LENGTH_SHORT).show();
    }
    
    private void toggleScanning() {
        if (isScanning) {
            stopScanning();
        } else {
            startScanning();
        }
    }
    
    private void startScanning() {
        isScanning = true;
        scanButton.setText("Остановить сканирование");
        scanButton.setBackgroundColor(getResources().getColor(android.R.color.holo_red_light));
        statusText.setText("Сканирование активно...");
        
        Toast.makeText(this, "Сканирование запущено", Toast.LENGTH_SHORT).show();
    }
    
    private void stopScanning() {
        isScanning = false;
        scanButton.setText("Начать сканирование");
        scanButton.setBackgroundColor(getResources().getColor(android.R.color.holo_green_light));
        statusText.setText("Готов к работе");
        
        Toast.makeText(this, "Сканирование остановлено", Toast.LENGTH_SHORT).show();
    }
    
    private void showSettings() {
        Toast.makeText(this, "Открываем настройки...", Toast.LENGTH_SHORT).show();
        // Здесь можно добавить переход к экрану настроек
    }
    
    // ========== МЕТОДЫ ГЕОЛОКАЦИИ ==========
    
    private void getCurrentLocation() {
        // Проверяем разрешения
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission();
            return;
        }
        
        // Проверяем, включена ли геолокация
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && 
            !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            Toast.makeText(this, "Геолокация отключена. Включите GPS или сетевую геолокацию.", 
                    Toast.LENGTH_LONG).show();
            return;
        }
        
        try {
            // Запрашиваем последнее известное местоположение
            Location lastKnownLocation = null;
            
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            
            if (lastKnownLocation == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            
            if (lastKnownLocation != null) {
                updateLocationUI(lastKnownLocation);
            } else {
                // Если нет последнего известного местоположения, запрашиваем обновления
                requestLocationUpdates();
            }
            
        } catch (SecurityException e) {
            Toast.makeText(this, "Ошибка доступа к геолокации", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void requestLocationUpdates() {
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, this);
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 1, this);
            }
            
            locationText.setText("Поиск местоположения...");
            accuracyText.setText("Обновление...");
            
        } catch (SecurityException e) {
            Toast.makeText(this, "Ошибка доступа к геолокации", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, 
                           Manifest.permission.ACCESS_COARSE_LOCATION}, 
                LOCATION_PERMISSION_REQUEST_CODE);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Разрешение на геолокацию предоставлено", Toast.LENGTH_SHORT).show();
                getCurrentLocation();
            } else {
                Toast.makeText(this, "Разрешение на геолокацию отклонено", Toast.LENGTH_SHORT).show();
                locationText.setText("Геолокация недоступна");
                accuracyText.setText("Нет разрешения");
            }
        }
    }
    
    @Override
    public void onLocationChanged(@NonNull Location location) {
        updateLocationUI(location);
        // Останавливаем обновления после получения первого местоположения
        locationManager.removeUpdates(this);
    }
    
    @Override
    public void onProviderEnabled(@NonNull String provider) {
        // Провайдер геолокации включен
    }
    
    @Override
    public void onProviderDisabled(@NonNull String provider) {
        // Провайдер геолокации отключен
        locationText.setText("Геолокация отключена");
        accuracyText.setText("Провайдер недоступен");
    }
    
    private void updateLocationUI(Location location) {
        if (location != null) {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            float accuracy = location.getAccuracy();
            
            locationText.setText(String.format("Широта: %.6f\nДолгота: %.6f", latitude, longitude));
            accuracyText.setText(String.format("Точность: %.1f м", accuracy));
            
            // Обновляем карту
            currentLocation = new LatLng(latitude, longitude);
            updateMapLocation(currentLocation);
            
            isLocationEnabled = true;
            Toast.makeText(this, "Местоположение обновлено", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void updateMapLocation(LatLng location) {
        if (googleMap != null && isMapReady) {
            // Очищаем предыдущие маркеры
            googleMap.clear();
            
            // Добавляем маркер текущего местоположения
            googleMap.addMarker(new MarkerOptions()
                    .position(location)
                    .title("Ваше местоположение")
                    .snippet("BebraRadar"));
            
            // Перемещаем камеру к новому местоположению
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 15));
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }
}
