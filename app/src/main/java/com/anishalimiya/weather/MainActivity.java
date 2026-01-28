package com.anishalimiya.weather;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.location.LocationManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.Intent;
import android.provider.Settings;

import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;


public class MainActivity extends AppCompatActivity {
    TextView tvCity, tvTemperature, tvCondition, tvFeelsLike, tvHumidity, tvWind, tvPressure, tvSunrise, tvSunset;
    ImageView ivWeatherIcon;

    //provides the best possible location information
    FusedLocationProviderClient fusedLocationClient;

    private static final int LOCATION_PERMISSION_REQUEST = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvCity = findViewById(R.id.tvCity);
        tvTemperature = findViewById(R.id.tvTemperature);
        tvCondition = findViewById(R.id.tvCondition);

        tvFeelsLike = findViewById(R.id.tvFeelsLike);
        tvHumidity = findViewById(R.id.tvHumidity);
        tvWind = findViewById(R.id.tvWind);
        tvPressure = findViewById(R.id.tvPressure);
        tvSunrise = findViewById(R.id.tvSunrise);
        tvSunset = findViewById(R.id.tvSunset);

        ivWeatherIcon = findViewById(R.id.ivWeatherIcon);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        checkLocationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If user turned on location in settings, continue flow
        if (isLocationEnabled()) {
            checkLocationPermission(); // or directly getLocation()
        }
    }

    //to ensure user's consent to access their precise GPS location
    private void checkLocationPermission() {
        //to verify the current state of the permission
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED)
        {
            //to show the permission dialog
            //passing an array containing the specific permission being requested
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);

            //then runs onRequestPermissionResult()
        }
        else
        {
            //start tracking the GPS
            getLocation();
        }
    }


    //A "callback" method that is automatically triggered by the Android system after the user clicks "Allow" or "Deny" on the popup followed by CheckLocationPermission()
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        //ensures that we are responding to the location request and not some other request like Camera or Mic
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            //grantResults[0] holds the result for the first permission in our list
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //call this function to get the GPS data
                getLocation();
            }
            else
            {
                Toast.makeText(this, "Location permission is required for Weather!", Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean isLocationEnabled() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        return lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    private void showGpsOffDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.gps_off_title))
                .setMessage(getString(R.string.gps_off_message))
                .setCancelable(false)
                .setNegativeButton(getString(R.string.not_now), (dialog, which) ->{
                        dialog.dismiss();
                        finish();    //terminate
                })
                .setPositiveButton(getString(R.string.turn_on), (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                })
                .show();
    }

    //the actual logic to retrieve the GPS coordinates
    private void getLocation() {
        if (!isLocationEnabled()) {
            showGpsOffDialog();
            return;
        }

        //to ensure permissions weren't revoked in the settings mid-use
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            //stop the function immediately to prevent the app from crashing
            return;
        }

        //initiates the request to the Google Play Services Location engine
        //Try cached location first (fast)
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    //Sometimes a new phone or a phone with GPS turned off will return null
                    if (location != null) {
                        //takes the coordinates and makes the API call to the Weather Service i.e. OpenWeatherMap
                        //Passing lat and lon to this function to display the weather for those coordinates
                        fetchWeather(location.getLatitude(), location.getLongitude());
                    }
                    //if cached is null, actively request a fresh location
                    else
                    {
                        CancellationTokenSource cts = new CancellationTokenSource();

                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                                .addOnSuccessListener(currentLocation ->{
                                    if(currentLocation != null)
                                    {
                                        fetchWeather(currentLocation.getLatitude(), currentLocation.getLongitude());
                                    }
                                    else
                                    {
                                        Toast.makeText(this, "Unable to get your location. Please try again", Toast.LENGTH_LONG).show();
                                    }
                                })
                                .addOnFailureListener(e -> Toast.makeText(this,
                                        "Location error: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show());
                    }
                });
    }

    //takes the coordinates we just found and sends a request over the internet to the weather service (OpenWeatherMap) to get real-time data
    private void fetchWeather(double lat, double lon)
    {
        //unique ID that proves to OpenWeatherMap that you are an authorized user.
        String apiKey = BuildConfig.WEATHER_API_KEY;

        String language = Locale.getDefault().getLanguage();

        String urlString = "https://api.openweathermap.org/data/2.5/weather?lat="
                + lat
                + "&lon=" + lon
                + "&units=metric"
                + "&appid=" + apiKey
                + "&lang=" + language;

        //Android doesn't allow network calls on main Thread.
        new Thread(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                //BufferedReader and Stringbuilder work together to read the data coming from the internet line-by-line and stitch it back together into one long piece of text.

                //Prepares the app to read the stream of data arriving from the web
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));

                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }

                //takes the raw text (JSON) and turns it into readable information like "25°C" or "Sunny."
                parseWeatherData(result.toString());
            }

            //in case the internet is off or the URL is wrong
            catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    //Helper method to convert sunrise and sunset time. OpenWeatherMap gives UNIX timestamp.
    private String formatTime(long unixSeconds) {
        Date date = new Date(unixSeconds * 1000);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(date);
    }

    //takes JSON sent by the weather server and picks out the specific pieces of information we want to show the user, like the city name, the temperature and the weather condition.
    private void parseWeatherData(String json) {
        try
        {
            //turns the raw string of text into a searchable Java object.
            JSONObject jsonObject = new JSONObject(json);

            String city = jsonObject.getString("name");

            JSONObject main = jsonObject.getJSONObject("main");
            double temp = main.getDouble("temp");
            double feelsLike = main.getDouble("feels_like");
            int humidity = main.getInt("humidity");
            int pressure = main.getInt("pressure");

            //Wind
            JSONObject wind = jsonObject.getJSONObject("wind");
            double windSpeed = wind.getDouble("speed");

            //Sunrise & Sunset
            JSONObject sys = jsonObject.getJSONObject("sys");
            long sunrise = sys.getLong("sunrise");
            long sunset = sys.getLong("sunset");

            JSONArray weatherArray = jsonObject.getJSONArray("weather");
            JSONObject weather = weatherArray.getJSONObject(0);
            String condition = weather.getString("description");
            String icon = weather.getString("icon");

            String iconUrl = "https://openweathermap.org/img/wn/" + icon + "@2x.png";

            //UI changes must take place using runOnUiThread()
            runOnUiThread(() -> {
                tvCity.setText(city);
                tvTemperature.setText(String.format(Locale.getDefault(), "%.1f °C", temp));

                tvCondition.setText(condition);

                tvFeelsLike.setText(
                        getString(R.string.feels_like, feelsLike));

                tvHumidity.setText(getString(R.string.humidity, humidity));

                tvWind.setText(
                        getString(R.string.wind, windSpeed));

                tvPressure.setText(getString(R.string.pressure, pressure));

                tvSunrise.setText(getString(R.string.sunrise, formatTime(sunrise)));
                tvSunset.setText(getString(R.string.sunset, formatTime(sunset)));

                Glide.with(this)
                        .load(iconUrl)
                        .into(ivWeatherIcon);
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}