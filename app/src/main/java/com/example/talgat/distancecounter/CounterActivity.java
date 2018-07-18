package com.example.talgat.distancecounter;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

public class CounterActivity extends AppCompatActivity implements OnMapReadyCallback{

    private static final int STATE_TAXI_ARRIVED = 3;
    private static final int STATE_FINISH_REQUEST = 4;
    private final String TAG = CounterActivity.class.getSimpleName();
    private static final int SHOW_COUNTER = 1;
    private static final int SHOW_MAP = 2;

    private TrackingService mBoundService;
    boolean mIsBound;

    Marker marker;
    Marker startMarker;
    LatLng lastGeo;

    private ViewGroup counterLayout;
    private ViewGroup mapsLayout;

    private TextView distanceTextView;
    private TextView driverStatebtn;
    double distan;

    private boolean isWorking = false;
    private boolean driverarrived = false;

    private int workingState;
    private Bitmap customersBitmap;
    private Bitmap driversBitmap;

    private GoogleMap mMap;
    SupportMapFragment mapFragment;

    FirebaseFirestore db;
    FirebaseAuth mAuth;
    FirebaseUser mUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_counter);

        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map2);
        mapFragment.getMapAsync(this);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        mUser = mAuth.getCurrentUser();

        doBindService();
        customersBitmap = createSmallMarkersIcon(R.drawable.people_map_marker);
        driversBitmap = createSmallMarkersIcon(R.drawable.vehile_map_marker);

        initViews();
        initBotNavView();
    }

    private Bitmap createSmallMarkersIcon(int iconRes) {
        int height = 100;
        int width = 100;
        BitmapDrawable bitmapdraw=(BitmapDrawable)getResources().getDrawable(iconRes);
        Bitmap b=bitmapdraw.getBitmap();
        return Bitmap.createScaledBitmap(b, width, height, false);
    }

    private void initBotNavView() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {

                switch (item.getItemId()) {
                    case R.id.category_counter:
                        updateUI(SHOW_COUNTER);
                        break;
                    case R.id.category_maps:
                        updateUI(SHOW_MAP);
                        break;
                }
                return true;
            }
        });
        bottomNavigationView.setSelectedItemId(R.id.category_status);
    }

    private void initViews() {
        counterLayout = findViewById(R.id.counterLayout);
        mapsLayout = findViewById(R.id.mapsLayout);

        distanceTextView = findViewById(R.id.distance);
        driverStatebtn = findViewById(R.id.driverStatebtn);
    }


    private void updateUI(int state) {
        switch (state) {
            case SHOW_COUNTER:
                counterLayout.setVisibility(View.VISIBLE);
                mapsLayout.setVisibility(View.GONE);
                break;
            case SHOW_MAP:
                mapsLayout.setVisibility(View.VISIBLE);
                counterLayout.setVisibility(View.GONE);
                break;
            case STATE_TAXI_ARRIVED:
                driverStatebtn.setText("Начать поездку");
                break;
        }
    }



    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBoundService = ((TrackingService.LocalBinder)service).getService();

            mBoundService.setServiceListener(new TrackingService.MyServiceListener() {
                @Override
                public void onChangeLocation(Location location) {
                    LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                    lastGeo = latLng;
                    if (marker == null) {
                        marker = mMap.addMarker(new MarkerOptions()
                                .icon(BitmapDescriptorFactory.fromBitmap(driversBitmap))
                                .title("Current Pos")
                                .position(latLng));

                        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
                    } else {
                        marker.setPosition(latLng);
                    }
                    LatLngBounds.Builder builder = new LatLngBounds.Builder();
                    builder.include(marker.getPosition());

                    CameraPosition cameraPosition = CameraPosition.builder()
                            .target(latLng)
                            .zoom(15)
                            .build();
//                     mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 300));
                    mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 2000, null);


                }

                @Override
                public void onChangeDistance(double distance) {
                    distance = Math.floor(distance * 100) / 100;
                    Log.e(TAG, String.format("%s m.", distance));
                    distan = distance;
                }
            });
            Toast.makeText(getApplicationContext(), "service connected", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBoundService = null;
            Toast.makeText(getApplicationContext(), "service disconnected", Toast.LENGTH_SHORT).show();

        }
    };

    void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation
        // that we know will be running in our own process (and thus
        // won't be supporting component replacement by other
        // applications).
        bindService(new Intent(this, TrackingService.class),
                mConnection,
                Context.BIND_AUTO_CREATE);
        Log.e(TAG, "bind service");
        mIsBound = true;
    }
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Add a marker in Sydney and move the camera
        LatLng sydney = new LatLng(-34, 151);
        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));

        mMap.setMaxZoomPreference(17);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(42.8520033088458, 74.62109389128251), 15));

    }


    void doUnbindService() {
        if (mIsBound) {
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
        if (mBoundService != null) {
            mBoundService.listener = null;
            mBoundService.stopSelf();
        }
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.driverStatebtn:
                if (!driverarrived) {
                    notifyDriverArrived();
                    isWorking = true;
                    driverarrived = true;

                } else if (isWorking) {

                    displayCount();
                    driverarrived = true;
                } else {
                    startActivity(new Intent(CounterActivity.this, MainActivity.class));
                    finish();
                }
                break;
        }
    }

    private void notifyDriverArrived() {
/*        Map<String, Object> map = new HashMap<>();
        map.put("arrived", true);

        db.collection("customerRequest").document(customerId).update(map);
*/
        updateUI(STATE_TAXI_ARRIVED);
    }

    private void displayCount() {
        if (!mBoundService.isTrack) {

            driverStatebtn.setText("finish");

            Map<String, Object> data = new HashMap<>();
            data.put("car_id", "Talgat");
            Double d = 0.0;
            data.put("total_distance", d);

            FirebaseFirestore.getInstance().collection("books")
                    .add(data)
                    .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                        @Override
                        public void onSuccess(DocumentReference documentReference) {
                            mBoundService.currentBookId = documentReference.getId();

                            //startMarker = mMap.addMarker(new MarkerOptions().title("start Pos").position(marker.getPosition()));
                            Log.e(TAG, "document created");
                            mBoundService.isTrack = true;
                            subscribeToUpdates();
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e(TAG, "fail: " + e + "  " + e.getMessage());
                        }
                    });

            distanceTextView.setText("Distance: 0 m");


        } else {
            mBoundService.isTrack = false;
            mBoundService.index = 0;
            distanceTextView.setText(String.format("last Distance: %s m", distan));
            driverStatebtn.setText("End request");


            if (startMarker != null) {
                startMarker.remove();
                startMarker = null;
            }

            isWorking = false;
        }
    }

    private void subscribeToUpdates() {

        final DocumentReference docRef = db.collection("books").document(mBoundService.currentBookId);
        docRef.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot documentSnapshot, @Nullable FirebaseFirestoreException e) {
                if (e != null) {
                    Log.d(TAG, "Listen failed.", e);

                    return;
                }

                if (documentSnapshot != null && documentSnapshot.exists()) {
                    Log.d(TAG, "Current data: " + documentSnapshot.get("total_distance"));

                    if (documentSnapshot.get("total_distance") instanceof Double) {
                        Double distance = (Double) documentSnapshot.get("total_distance");
                        if (distance != null && distance >0) {
                            distance = (distance * 1000)/ 1000;
                        }
                        distanceTextView.setText("FB distance: " + distance + "km");
                    } else if (documentSnapshot.get("total_distance") instanceof Long) {
                        Long distance = (Long) documentSnapshot.get("total_distance");
                        if (distance != null && distance >0) {
                            distance = (distance * 1000)/ 1000;
                        }
                        distanceTextView.setText("FB distance: " + distance + "km");
                    }



                } else {
                    Log.d(TAG, "Current data: null");
                }
            }
        });

    }
}
