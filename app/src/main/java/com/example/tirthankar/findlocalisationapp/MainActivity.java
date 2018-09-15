package com.example.tirthankar.findlocalisationapp;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private EditText eTfamily,eTdevice,eTserver,eTlocation;
    private TextView textViewRes;
    private String family,device,server,location;
    private boolean tracking;
    private CheckBox checkTracking;
    private Button btn;
    private Intent serviceIntent;
    private boolean error = false;
    private final int ACCESS_COARSE_LOCATION =1;
    private boolean appAllowed = true;

    //Volley Request queue
    RequestQueue requestQueue;

    StringRequest stringRequest;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        askPermission();


        requestQueue = Volley.newRequestQueue(this);

        serviceIntent = new Intent(this, ScanService.class);

        textViewRes = findViewById(R.id.textViewRes);
        eTfamily = findViewById(R.id.editTextFamily);
        eTdevice = findViewById(R.id.editTextDevice);
        eTserver = findViewById(R.id.editTextFamilyServer);
        eTlocation = findViewById(R.id.editTextLocation);
        //eTlocation.setFocusable(false);
        checkTracking = findViewById(R.id.checkBox);
        checkTracking.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(checkTracking.isChecked()){
                    disableEditText(eTdevice);
                    disableEditText(eTfamily);
                    disableEditText(eTlocation);
                    tracking = true;
                }
                else{
                    enableEditText(eTdevice);
                    enableEditText(eTfamily);
                    enableEditText(eTlocation);
                    tracking = false;
                }
            }
        });

        btn = findViewById(R.id.btnStart);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                family = eTfamily.getText().toString();
                if(family.isEmpty()){
                    eTfamily.setError("Family name cannot be empty");
                    eTfamily.setFocusable(true);
                    error = true;
                }else if(!tracking && usedNames(family)){
                    eTfamily.setError("Use other family name");
                    eTfamily.setFocusable(true);
                    error = true;
                }

                device = eTdevice.getText().toString();
                if(device.isEmpty()){
                    eTdevice.setError("Device name cannot be empty");
                    eTdevice.setFocusable(true);
                    error = true;
                }

                server = eTserver.getText().toString();
                if(server.isEmpty()){
                    eTserver.setError("Server address cannot be empty");
                    eTserver.setFocusable(true);
                    error = true;
                }

                location = eTlocation.getText().toString();
                if(location.isEmpty() && !tracking){
                    eTlocation.setError("Location cannot be empty");
                    eTlocation.setFocusable(true);
                    error = true;
                }

                if(!error && appAllowed) {
                    if(tracking){
                        stringRequest = new StringRequest(server+"/api/v1/location/"+family+"/"+device, new Response.Listener<String>() {
                            @Override
                            public void onResponse(String response) {
                                try {
                                    JSONObject jsonObject = new JSONObject(response);
                                    JSONObject analysis = jsonObject.getJSONObject("analysis");
                                    JSONArray guesses = analysis.getJSONArray("guesses");
                                    JSONObject guess = guesses.getJSONObject(0);
                                    textViewRes.setText("You are at location "+guess.getString("location")+" with probability of "+guess.getString("probability"));
                                    Log.d("VolleyRes",guess.toString());

                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }

                            }
                        }, new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                Log.d("Volley",error.toString());
                            }
                        });
                    }
                    if (btn.getText().toString().toLowerCase().equals("start scan")) {
                        btn.setText("STOP SCAN");
                        serviceIntent.putExtra("family",family);
                        serviceIntent.putExtra("device",device);
                        serviceIntent.putExtra("location",location);
                        serviceIntent.putExtra("server",server);
                        serviceIntent.putExtra("tracking",tracking);
                        startService(serviceIntent);

                        if(tracking){
                            startTracking();
                        }

                    } else {
                        btn.setText("START SCAN");
                        stopService(serviceIntent);
                        if(tracking)
                            handler.removeCallbacks(startTracking);
                    }
                }else{
                    Toast.makeText(MainActivity.this, "Permission not granted", Toast.LENGTH_SHORT).show();
                }
            }
        });
        //start a service based on button click
    }

    private void askPermission() {
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, ACCESS_COARSE_LOCATION);
            return;
        }else{
            // Write you code here if permission already given.
        }
    }

    Runnable startTracking = new Runnable() {
        @Override
        public void run() {

            requestQueue.add(stringRequest);
            //Call this code after every 10 seconds to perform realtime tracking
            handler.postDelayed(startTracking,1000);
        }
    };

    private void startTracking() {
        handler=new Handler();
        handler.post(startTracking);
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == ACCESS_COARSE_LOCATION){
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                appAllowed = true;
            } else {
                // permission denied, boo! Disable the
                // functionality that depends on this permission.
                appAllowed = false;
            }
        }
    }

    private boolean usedNames(String family) {
        List<String> alreadyUsedNames = new ArrayList<>();
        alreadyUsedNames.add("ibm_v1");
        alreadyUsedNames.add("testdb");
        alreadyUsedNames.add("testdb1");
        alreadyUsedNames.add("testdb2");
        alreadyUsedNames.add("testdb3");
        alreadyUsedNames.add("testdb4");
        alreadyUsedNames.add("testdb5");
        if(alreadyUsedNames.contains(family))
            return true;
        return  false;
    }

    private void disableEditText(EditText editText) {
        //editText.setFocusable(false);
        editText.setEnabled(false);
        editText.setCursorVisible(false);
    }
    private void enableEditText(EditText editText) {
        //editText.setFocusable(true);
        editText.setEnabled(true);
        editText.setCursorVisible(true);
    }
}
