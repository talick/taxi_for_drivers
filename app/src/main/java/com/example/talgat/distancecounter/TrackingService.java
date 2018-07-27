package com.example.talgat.distancecounter;

import android.Manifest;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.maps.android.SphericalUtil;

import java.util.HashMap;
import java.util.Map;
public class TrackingService  extends Service {

    private static final String TAG = TrackingService.class.getSimpleName();

    private final IBinder mBinder = new LocalBinder();

    MyServiceListener listener;
    LocationCallback locationCallback;

    public boolean isTrack = false;

    boolean isDriverWorking = false;
    LatLng lastGeo;
    LatLng currentGeo;

    double distance = 0;
    public String currentBookId;
    public int index;

    private FirebaseFirestore db;

    public class LocalBinder extends Binder {
        TrackingService getService() {
            return TrackingService.this;
        }
    }
    @Override
    public IBinder onBind(Intent intent) {return mBinder;}

    @Override
    public void onCreate() {
        super.onCreate();
        //buildNotification();

        db = FirebaseFirestore.getInstance();
        locationCallback = initLocationCallback();
        requestLocationUpdates();
    }

    private void buildNotification() {
        String stop = "stop";
        registerReceiver(stopReceiver, new IntentFilter(stop));
        PendingIntent broadcastIntent = PendingIntent.getBroadcast(
                this, 0, new Intent(stop), PendingIntent.FLAG_UPDATE_CURRENT);
        // Create the persistent notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "channelID")
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_text))
                .setOngoing(true)
                .setContentIntent(broadcastIntent)
                .setSmallIcon(R.drawable.ic_tracker);
        startForeground(1, builder.build());
        Log.e(TAG, "notification binded");
    }


    @Override
    public boolean onUnbind(Intent intent) {
        stopSelf();
        return super.onUnbind(intent);

    }

    protected BroadcastReceiver stopReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "received stop broadcast");
            // Stop the service when the notification is tapped
            unregisterReceiver(stopReceiver);
            stopSelf();
        }
    };

/*    private void loginToFirebase() {
        String email = getString(R.string.firebase_email);
        String password = getString(R.string.firebase_password);
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if (task.isSuccessful()) {
                    Log.e(TAG, "firebase auth success");
                    requestLocationUpdates();
                } else {
                    Log.e(TAG, "firebase auth failed");
                }
            }
        });
    }*/


    private void requestLocationUpdates() {
        LocationRequest request = new LocationRequest();
        request.setInterval(2000);
        request.setFastestInterval(2000);
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(getApplicationContext());
        int permission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        if (permission == PackageManager.PERMISSION_GRANTED) {
            client.requestLocationUpdates(request, locationCallback, null);
        }
    }

    private LocationCallback initLocationCallback() {
        return new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                Location location = locationResult.getLastLocation();


                Log.e(TAG, "locationCallback" + isTrack);



                if (location != null && listener != null) {

                    currentGeo = new LatLng(location.getLatitude(), location.getLongitude());

                    Log.e(TAG, "locationCallback");

                    listener.onChangeLocation(location);
                    if (isTrack) {
                        countdistance();
                        Log.e(TAG, "countDistance");


                    } else {
                        distance = 0;
                        lastGeo = null;
                    }


                }

            }
        };
    }

    private void countdistance() {
        Map<String, Object> geoData = new HashMap<>();
        if (lastGeo == null) {
            lastGeo = currentGeo;
        }

        double shortDist = SphericalUtil.computeDistanceBetween(lastGeo, currentGeo);

        Toast.makeText(getApplicationContext(), "short dis: " + shortDist, Toast.LENGTH_SHORT).show();

        if (index == 0) {
            geoData.put("latitude", String.valueOf(currentGeo.latitude));
            geoData.put("longitude", String.valueOf(currentGeo.longitude));
            geoData.put("index", index);

            index++;

            db.collection("books").document(currentBookId)
                    .collection("geolocation")
                    .add(geoData);
        }


        if (shortDist >=1) {
            distance = distance + shortDist;
            geoData.put("latitude", String.valueOf(currentGeo.latitude));
            geoData.put("longitude", String.valueOf(currentGeo.longitude));
            geoData.put("index", index);

            index++;

            db.collection("books").document(currentBookId)
                    .collection("geolocation")
                    .add(geoData);

            listener.onChangeDistance(distance);
            lastGeo = currentGeo;
        }
    }

    public interface MyServiceListener{
        void onChangeLocation(Location location);

        void onChangeDistance(double distance);
    }

    public void setServiceListener(MyServiceListener listener) {
        this.listener = listener;
    }

}
