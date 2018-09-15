package com.example.tirthankar.findlocalisationapp;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class ScanService extends Service{
    // logging
    private final String TAG = "ScanService";

    int mStartMode;       // indicates how to behave if the service is killed
    IBinder mBinder;      // interface for clients that bind
    boolean mAllowRebind; // indicates whether onRebind should be used

    boolean isScanning = false;
    private final Object lock = new Object();

    // wifi scanning
    private WifiManager wifi;

    // bluetooth scanning
    private BluetoothAdapter BTAdapter = BluetoothAdapter.getDefaultAdapter();
    BluetoothBroadcastReceiver receiver = null;

    // post data request queue
    RequestQueue queue;
    private JSONObject jsonBody = new JSONObject();
    private JSONObject bluetoothResults = new JSONObject();
    private JSONObject wifiResults = new JSONObject();

    private String familyName;
    private String locationName;
    private String deviceName;
    private String serverAddress = "";
    private boolean allowGPS = false;
    private boolean tracking = false;
    @Override
    public void onCreate() {

        // The service is being created
        Log.d(TAG, "creating new scan service");
        queue = Volley.newRequestQueue(this);
        // setup wifi
        wifi = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        if (wifi.isWifiEnabled() == false) {
            wifi.setWifiEnabled(true);
        }
        // register wifi intent filter
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(mWifiScanReceiver, intentFilter);

        try {
            // setup bluetooth
            Log.d(TAG, "setting up bluetooth");
            if (receiver == null) {
                receiver = new BluetoothBroadcastReceiver();
                registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        familyName = intent.getStringExtra("family");
        deviceName = intent.getStringExtra("device");
        locationName = intent.getStringExtra("location");
        serverAddress = intent.getStringExtra("server");
        tracking = intent.getBooleanExtra("tracking",false);


        Log.d(TAG, "familyName: " + familyName +" server "+serverAddress);

        Timer timer = new Timer();
        if(!tracking) {
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    Log.d(TAG, "scheduler");
                    synchronized (lock) {
                        if (isScanning == false) {
                            doScan();
                        }
                    }
                }
            }, 1000, 10000);
        }else{
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    Log.d(TAG, "scheduler");
                    synchronized (lock) {
                        if (isScanning == false) {
                            doScan();
                        }
                    }
                }
            }, 1000, 1000);
        }

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // All clients have unbound with unbindService()
        return mAllowRebind;
    }

    @Override
    public void onDestroy() {
        // The service is no longer used and is being destroyed
        Log.v(TAG, "onDestroy");
        try {
            if (receiver != null)
                unregisterReceiver(receiver);
        } catch (Exception e) {
            Log.w(TAG, e.toString());
        }
        try {
            if (mWifiScanReceiver != null)
                unregisterReceiver(mWifiScanReceiver);
        } catch (Exception e) {
            Log.w(TAG, e.toString());
        }
        stopSelf();
        super.onDestroy();
    }

    // bluetooth reciever
    private class BluetoothBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String name = device.getAddress().toLowerCase();
                Log.v(TAG, "bluetooth: " + name + " => " + rssi + "dBm");
                try {
                    bluetoothResults.put(name, rssi);
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
            }
        }
    }

    private void doScan() {
        synchronized (lock) {
            if (isScanning == true) {
                return;
            }
            isScanning = true;
        }
        bluetoothResults = new JSONObject();
        wifiResults = new JSONObject();
        BTAdapter.startDiscovery();
        if (wifi.startScan()) {
            Log.d(TAG, "started wifi scan");
        } else {
            Log.w(TAG, "started wifi scan false?");
        }
        Log.d(TAG, "started discovery");
    }


    private final BroadcastReceiver mWifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            // This condition is not necessary if you listen to only one action
            if (intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                Log.d(TAG, "timer off, trying to send data");
                List<ScanResult> wifiScanList = wifi.getScanResults();
                for (int i = 0; i < wifiScanList.size(); i++) {
                    String name = wifiScanList.get(i).BSSID.toLowerCase();
                    int rssi = wifiScanList.get(i).level;
                    Log.d(TAG, "wifi: " + name + " => " + rssi + "dBm");
                    try {
                        wifiResults.put(name, rssi);
                    } catch (Exception e) {
                        Log.e(TAG, e.toString());
                    }
                }
                sendData();
                BTAdapter.cancelDiscovery();
                BTAdapter = BluetoothAdapter.getDefaultAdapter();
                synchronized (lock) {
                    isScanning = false;
                }
            }
        }
    };

    public void sendData() {
        try {
            String URL = serverAddress + "/data";
            Log.d(TAG,"url:"+URL);

            if(!tracking) {
                //create a stringreq list
                for (int i = 0; i < 10; i++) {
                    jsonBody = new JSONObject();
                    jsonBody.put("f", familyName);
                    jsonBody.put("d", deviceName+i);
                    jsonBody.put("l", locationName);
                    jsonBody.put("t", System.currentTimeMillis());
                    JSONObject sensors = new JSONObject();
                    sensors.put("bluetooth", bluetoothResults);
                    sensors.put("wifi", wifiResults);
                    jsonBody.put("s", sensors);

                    final String mRequestBody = jsonBody.toString();
                    Log.d(TAG, mRequestBody);

                    StringRequest stringRequest = new StringRequest(Request.Method.POST, URL, new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            Log.d(TAG, "response"+response);

                        }
                    }, new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            Log.e(TAG, error.toString());
                        }
                    }) {
                        @Override
                        public String getBodyContentType() {
                            return "application/json; charset=utf-8";
                        }

                        @Override
                        public byte[] getBody() throws AuthFailureError {
                            try {
                                return mRequestBody == null ? null : mRequestBody.getBytes("utf-8");
                            } catch (UnsupportedEncodingException uee) {
                                VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of %s using %s", mRequestBody, "utf-8");
                                return null;
                            }
                        }

                        @Override
                        protected Response<String> parseNetworkResponse(NetworkResponse response) {
                            String responseString = "";
                            if (response != null) {
                                responseString = new String(response.data);
                            }
                            return Response.success(responseString, HttpHeaderParser.parseCacheHeaders(response));
                        }
                    };

                    queue.add(stringRequest);
                }
            }else{
                jsonBody.put("f", familyName);
                jsonBody.put("d", deviceName);
                jsonBody.put("l", "");
                jsonBody.put("t", System.currentTimeMillis());
                JSONObject sensors = new JSONObject();
                sensors.put("bluetooth", bluetoothResults);
                sensors.put("wifi", wifiResults);
                jsonBody.put("s", sensors);
                final String mRequestBody = jsonBody.toString();
                Log.d(TAG, mRequestBody);

                StringRequest stringRequest = new StringRequest(Request.Method.POST, URL, new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d(TAG, "response"+response);

                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG, error.toString());
                    }
                }) {
                    @Override
                    public String getBodyContentType() {
                        return "application/json; charset=utf-8";
                    }

                    @Override
                    public byte[] getBody() throws AuthFailureError {
                        try {
                            return mRequestBody == null ? null : mRequestBody.getBytes("utf-8");
                        } catch (UnsupportedEncodingException uee) {
                            VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of %s using %s", mRequestBody, "utf-8");
                            return null;
                        }
                    }

                    @Override
                    protected Response<String> parseNetworkResponse(NetworkResponse response) {
                        String responseString = "";
                        if (response != null) {
                            responseString = new String(response.data);
                        }
                        return Response.success(responseString, HttpHeaderParser.parseCacheHeaders(response));
                    }
                };
                queue.add(stringRequest);
            }


        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
