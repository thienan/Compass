package com.duy.compass.weather.sync;

import android.content.Context;
import android.location.Location;
import android.net.Uri;
import android.os.StrictMode;
import android.support.annotation.Nullable;
import android.util.Log;

import com.duy.compass.database.DatabaseHelper;
import com.duy.compass.model.Sunshine;
import com.duy.compass.model.WeatherData;
import com.duy.compass.util.DLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * SyncAdapter
 */
public class WeatherManager {


    // Interval at which to sync with the weather, in seconds
    // 60 seconds (1min) * 180 = 3 hours
    public static final int SYNC_INTERVAL = 60 * 180;
    public static final int SYNC_FLEXTIME = SYNC_INTERVAL / 3;


    // These indices must match the projection
    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_MAX_TEMP = 1;
    private static final int INDEX_MIN_TEMP = 2;
    private static final int INDEX_SHORT_DESC = 3;

    private static final long DAY_IN_MILLIS = 1000 * 60 * 60 * 24;
    private static final int WEATHER_NOTIFICATION_ID = 3004;
    private static final String TAG = "WeatherManager";
    private boolean DEBUG = true;
    private DatabaseHelper mDatabaseHelper;
    private Context mContext;

    public WeatherManager(Context context) {
        this.mContext = context;
    }

    public static Sunshine getSunTimeFromJson(String forecastJsonStr) throws JSONException {
        DLog.d(TAG, "getSunTimeFromJson() called with: forecastJsonStr = [" + forecastJsonStr + "]");

        JSONObject forecastJson = new JSONObject(forecastJsonStr);
        JSONObject sysJson = forecastJson.getJSONObject("sys");
        long sunrise = sysJson.getLong("sunrise");
        long sunset = sysJson.getLong("sunset");
        return new Sunshine(sunset * 1000, sunrise * 1000);
    }

    public static String getWeatherForecastData(double lon, double lat) {
        //These two need to be declared outside the try/catch
        //so that they can be closed in the finally block
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;

        //Wil contain the raw JSON response as a string
        String forecastJsonStr = null;

        String format = "json";
        String units = "metric";
        int numDays = 14;
        String key = "632bd08931e170a4e19c711abab52be3";
        try {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                    .permitAll().build();
            StrictMode.setThreadPolicy(policy);

            // Construct the URL for the OpenWeatherMap query
            // Possible parameters are avaiable at OWM's forecast API page, at
            // http://openweathermap.org/API#forecast
            final String forecastBaseUrl = "http://api.openweathermap.org/data/2.5/weather?";
            final String longitudeParam = "lon";
            final String latitudeParam = "lat";
            final String apiKey = "appid";

            Uri builtUri = Uri.parse(forecastBaseUrl).buildUpon()
                    .appendQueryParameter(longitudeParam, String.valueOf(lon))
                    .appendQueryParameter(latitudeParam, String.valueOf(lat))
                    .appendQueryParameter(apiKey, key)
                    .build();

            URL url = new URL(builtUri.toString());

            // Create the request to OpenWeatherMap, and open the connection
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.connect();

            //Read the input stream into a String
            InputStream inputStream = urlConnection.getInputStream();
            StringBuffer buffer = new StringBuffer();
            if (inputStream == null) {
                //Nothing to do
                return null;
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = reader.readLine()) != null) {
                //Since it's JSON, adding a newline isn't necessary (it won't affect
                //parsing) But it does make debugging a lot easier if you print out the
                //completed buffer for debugging
                buffer.append(line + "\n");
            }

            if (buffer.length() == 0) {
                //Stream was empty. No point in parsing
                return null;
            }
            forecastJsonStr = buffer.toString();

        } catch (IOException e) {
            Log.e("MainFragment", "Error: ", e);
            //If the code didn't successfully get the weather data, there's no point in attempting to parse it
            forecastJsonStr = null;
        } finally {
            if (urlConnection != null)
                urlConnection.disconnect();

            if (reader != null) {
                try {
                    reader.close();
                } catch (final IOException e) {
                    Log.e("MainFragment", "Error closing stream", e);
                }
            }
        }

        return forecastJsonStr;
    }

    @Nullable
    public static WeatherData getWeatherData(Location location) {
        DLog.d(TAG, "getWeatherData() called with: location = [" + location + "]");
        try {
            double lat = location.getLatitude();
            double lon = location.getLongitude();
            String weatherForecast = getWeatherForecastData(lon, lat);
            return getWeatherDataFromJson(weatherForecast);
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Take the String representing the complete forecast in JSON Format and
     * pull out the data we need to construct the Strings needed for the wireframes.
     * <p>
     * Fortunately parsing is easy:  constructor takes the JSON string and converts it
     * into an Object hierarchy for us.
     */
    private static WeatherData getWeatherDataFromJson(String forecastJsonStr)
            throws JSONException {
        DLog.d(TAG, "getWeatherDataFromJson() called with: forecastJsonStr = [" + forecastJsonStr + "]");

        JSONObject forecastJson = new JSONObject(forecastJsonStr);
        JSONObject mainData = forecastJson.getJSONObject("main");

        WeatherData weatherData = new WeatherData();
        weatherData.setId(forecastJson.getInt("id"));
        weatherData.setTemp((float) mainData.getDouble("temp"));
        weatherData.setTempMax((float) mainData.getDouble("temp_max"));
        weatherData.setTempMin((float) mainData.getDouble("temp_min"));
        weatherData.setHumidity((float) mainData.getDouble("humidity"));
        weatherData.setPressure((float) mainData.getDouble("pressure"));
        JSONObject sysJson = forecastJson.getJSONObject("sys");
        long sunrise = sysJson.getLong("sunrise");
        long sunset = sysJson.getLong("sunset");
        Sunshine sunshine = new Sunshine(sunset * 1000, sunrise * 1000);
        weatherData.setSunshine(sunshine);

        return weatherData;
    }

    public Context getContext() {
        return mContext;
    }

}
