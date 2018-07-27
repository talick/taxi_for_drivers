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
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.talgat.distancecounter.model.Request;
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
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import static com.example.talgat.distancecounter.utils.BitmapUtils.createSmallMarkersIcon;

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
    private TextView totalSummTextView;
    private TextView waitingTimeTextView;
    private TextView driverStatebtn;
    private Button timeButton;

    double distan;
    int totalSumm = 0;
    int waitingTime = 0;
    boolean countTime = false;

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

    Request request;

    DocumentReference requestRef;
    Timestamp pickupTime;

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

        request = (Request) getIntent().getSerializableExtra("request");

        requestRef = db.collection("requests").document();
        Map<String, String> map = new HashMap<>();
        map.put("driverId", mUser.getUid());
        map.put("requestId", requestRef.getId());
        db.collection("req").document(request.getCustomerId()).set(map);


        doBindService();
        customersBitmap = createSmallMarkersIcon(R.drawable.people_map_marker, this);
        driversBitmap = createSmallMarkersIcon(R.drawable.vehile_map_marker, this);

        initViews();
        initBotNavView();
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
        totalSummTextView = findViewById(R.id.total_summ);
        waitingTimeTextView = findViewById(R.id.waiting_time);
        driverStatebtn = findViewById(R.id.driverStatebtn);
        timeButton = findViewById(R.id.startTimeCountBnt);
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
                timeButton.setVisibility(View.VISIBLE);
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

                    updateGeoLocation(latLng);

                }

                @Override
                public void onChangeDistance(double distance) {
                    distance = Math.floor(distance * 100) / 100;
                    Log.e(TAG, String.format("Distance: %s m.", distance));
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

    private void updateGeoLocation(LatLng latLng) {

        CollectionReference refWorking = db.collection("driverWorking");

        Map<String, String> geoMap = new HashMap<>();
        geoMap.put("latitude", String.valueOf(latLng.latitude));
        geoMap.put("longitude", String.valueOf(latLng.longitude));
        refWorking.document(mUser.getUid()).set(geoMap);
    }

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
    public void onBackPressed() {

        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
        Log.e(TAG, "onDestroy");
        if (mBoundService != null) {
            mBoundService.listener = null;
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
                    Intent intent = new Intent(CounterActivity.this, MainActivity.class);
                    intent.putExtra("isOnline", 1);
                    startActivity(intent);
                    finish();
                }
                break;

            case R.id.startTimeCountBnt:
                if (countTime) {
                    countTime = false;
                    countWaitingTime();
                    timeButton.setText("Включить ожидание");
                } else {
                    countTime = true;
                    countWaitingTime();
                    timeButton.setText("Отключить ожидание");
                }
        }
    }

    private void notifyDriverArrived() {
/*        Map<String, Object> map = new HashMap<>();
        map.put("arrived", true);

        db.collection("customerRequest").document(customerId).update(map);
*/
        pickupTime = Timestamp.now();
        Map<String, Object> map = new HashMap<>();
        map.put("driverArrived", true);
        db.collection("req").document(request.getCustomerId()).update(map);

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
                            notifyTripOnGoing();
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e(TAG, "fail: " + e + "  " + e.getMessage());
                        }
                    });



        } else {
            mBoundService.isTrack = false;
            mBoundService.index = 0;
            driverStatebtn.setText("End request");
            timeButton.setVisibility(View.INVISIBLE);
            countTime = false;
            countWaitingTime();

            writeReqtoDb();
            notifyTripIsCompleted();

            if (startMarker != null) {
                startMarker.remove();
                startMarker = null;
            }

            isWorking = false;
        }
    }

    private void notifyTripOnGoing() {
        Map<String, Object> map = new HashMap<>();
        map.put("onGoing", true);
        db.collection("req").document(request.getCustomerId()).update(map);
    }

    private void notifyTripIsCompleted() {
        Map<String, Object> map = new HashMap<>();
        map.put("isCompleted", true);
        map.put("summ", totalSumm);
        db.collection("req").document(request.getCustomerId()).update(map);
    }
    private void writeReqtoDb() {
        Map<String, Object> map = new HashMap<>();
        map.put("distance", distan);
        map.put("customerId", request.getCustomerId());
        map.put("driverId", Objects.requireNonNull(mAuth.getCurrentUser()).getUid());
        map.put("pickupLocation", request.getAddress());
        map.put("pickupTime", pickupTime);

        map.put("summ", totalSumm);
        db.collection("requests").add(map).addOnCompleteListener(new OnCompleteListener<DocumentReference>() {
            @Override
            public void onComplete(@NonNull Task<DocumentReference> task) {
                Log.e(TAG, "writ4eReqtoDb: it's ok");
            }
        });
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

                    int dist = 0;
                    if (documentSnapshot.get("total_distance") instanceof Double) {
                        Double distance = (Double) documentSnapshot.get("total_distance");
                        Log.e(TAG, distance + " ");
                        if (distance == null) {
                            return;
                        }
                        updateCounter(distance);

                    } else if (documentSnapshot.get("total_distance") instanceof Long) {
                        Long distance = (Long) documentSnapshot.get("total_distance");
                        if (distance != null && distance >0) {
                            dist = (int) ((distance * 1000)/ 1000);
                        }
                        distanceTextView.setText(dist + " км");
                    }



                } else {
                    Log.d(TAG, "Current data: null");
                }
            }
        });

    }

    private void updateCounter(Double distance) {
        Double sum;
        distan = distance;
        if (distance == 0) {
            sum = 25 + waitingTime * 0.083;
        } else {
            sum = 25 + (distance*12) + waitingTime * 0.083;
            sum = Math.floor(sum * 100) / 100;

        }

        totalSumm = (int) (sum / 1);
        DecimalFormat df = new DecimalFormat("#.###");
        String dx=df.format(distance);
        distanceTextView.setText(dx + " км");
        totalSummTextView.setText(String.valueOf(sum));

    }

    private void updateCounter(int time) {
        Double sum;
        if (distan == 0) {
            sum = 25 + time * 0.083;
        } else {
            sum = 25 + ((distan/1000) *12) + time * 0.083;
        }
        totalSumm = (int) (sum / 1);
        DecimalFormat df = new DecimalFormat("#.##");
        String dx=df.format(sum);
        totalSummTextView.setText(dx);

    }

    Thread thread = new Thread() {

        @Override
        public void run() {
            try {
                while (!isInterrupted()) {
                    Thread.sleep(1000);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            waitingTime++;
                            int minutes = waitingTime / 60;
                            int seconds= waitingTime % 60;
                            String min;
                            String sec;
                            if (minutes < 10) {
                                min = "0"+minutes;
                            }else {
                                min = String.valueOf(minutes);
                            }
                            if (seconds < 10) {
                                sec = "0" + seconds;
                            } else {
                                sec = String.valueOf(seconds);
                            }

                            waitingTimeTextView.setText(min + ":" + sec);
                            updateCounter(waitingTime);
                        }
                    });
                }
            } catch (InterruptedException e) {
            }
        }
    };

    private void countWaitingTime() {


        if (countTime) {
            thread.start();
        } else {
            thread.interrupt();
        }
    }


}
