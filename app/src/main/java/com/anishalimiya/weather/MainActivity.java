package com.anishalimiya.weather;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.annotation.NonNull;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {
    TextView tvCity, tvTemperature, tvCondition;

    FusedLocationProviderClient fusedLocationClient;

    private static final int LOCATION_PERMISSION_REQUEST = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tvCity = findViewById(R.id.wCity);
        tvTemperature = findViewById(R.id.wTemperature);
        tvCondition = findViewById(R.id.wCondition);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        checkLocationPermission();
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
        }
    }


    //the actual logic to retrieve the GPS coordinates
    private void getLocation() {
        //to ensure permissions weren't revoked in the settings mid-use
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            //stop the function immediately to prevent the app from crashing
            return;
        }

        //initiates the request to the Google Play Services Location engine
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    //Sometimes a new phone or a phone with GPS turned off will return null
                    if (location != null) {
                        double lat = location.getLatitude();
                        double lon = location.getLongitude();

                        //takes the coordinates and makes the API call to the Weather Service i.e. OpenWeatherMap
                        //Passing lat and lon to this function to display the weather for those coordinates
                        fetchWeather(lat, lon);
                    }
                });
    }

    //takes the coordinates we just found and sends a request over the internet to the weather service (OpenWeatherMap) to get real-time data
    private void fetchWeather(double lat, double lon)
    {
        //unique ID that proves to OpenWeatherMap that you are an authorized user.
        String apiKey = BuildConfig.WEATHER_API_KEY;
        String urlString = "https://api.openweathermap.org/data/2.5/weather?lat="
                + lat + "&lon=" + lon + "&units=metric&appid=" + apiKey;

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


    //takes JSON sent by the weather server and picks out the specific pieces of information we want to show the user, like the city name, the temperature and the weather condition.
    private void parseWeatherData(String json) {
        try
        {
            //turns the raw string of text into a searchable Java object.
            JSONObject jsonObject = new JSONObject(json);
            String city = jsonObject.getString("name");

            JSONObject main = jsonObject.getJSONObject("main");
            double temp = main.getDouble("temp");

            JSONArray weatherArray = jsonObject.getJSONArray("weather");
            JSONObject weatherObj = weatherArray.getJSONObject(0);
            String condition = weatherObj.getString("description");

            runOnUiThread(() -> {
                tvCity.setText(city);
                tvTemperature.setText(temp + " °C");
                tvCondition.setText(condition);
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}