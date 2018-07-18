package com.example.talgat.distancecounter;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {


    private static final int STATE_FINISH_REQUEST = 6;
    private GoogleMap mMap;
    private TextView textView;
    private TextView fbDistance;

    private final String TAG = MapsActivity.class.getSimpleName();
    private static final int PERMISSION_REQUEST = 1;

    private static final int CHANGE_STATUS = 1;
    private static final int SHOW_MAP_STATE = 2;
    private static final int SHOW_INFO_STATE = 3;
    private static final int STATE_TAXI_ARRIVED = 4;
    private static final int STATE_TAXI_HEADING_TO_THE_CUSTOMER = 5;


    private TrackingService mBoundService;
    boolean mIsBound;
    Marker marker;
    Marker startMarker;
    double distan;
    LatLng lastGeo;

    private Bitmap customersBitmap;
    private Bitmap driversBitmap;

    FirebaseFirestore db;
    FirebaseAuth mAuth;
    FirebaseUser mUser;




    boolean isOnline = false;
    private boolean isWorking = false;

    private ViewGroup onlineStateLayout;
    private ViewGroup offlineStateLayout;
    private ViewGroup infoLayout;
    private TextView taxiStatebutton;
    private Button setofflineButton;

    private Button startDistanceCountButton;

    private CollectionReference requestRef;

    String customerId;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            logout();
        } else {
            setContentView(R.layout.activity_maps);
            // Obtain the SupportMapFragment and get notified when the map is ready to be used.
            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.map);
            mapFragment.getMapAsync(this);

            db = FirebaseFirestore.getInstance();
            mAuth = FirebaseAuth.getInstance();
            mUser = mAuth.getCurrentUser();

            requestRef = db.collection("driverAvailable").document(mUser.getUid()).collection("customerRequest");

            requestRef.addSnapshotListener(checkRequest);

            customersBitmap = createSmallMarkersIcon(R.drawable.people_map_marker);
            driversBitmap = createSmallMarkersIcon(R.drawable.vehile_map_marker);

            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(this, "Please enable location services", Toast.LENGTH_SHORT).show();
                finish();
            }

            int permission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);

            if (permission == PackageManager.PERMISSION_GRANTED) {
                doBindService();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        PERMISSION_REQUEST);
            }

            initViews();
            updateUI(CHANGE_STATUS);
        }



    }


    private void initViews() {
        onlineStateLayout = findViewById(R.id.onlineState);
        offlineStateLayout = findViewById(R.id.offlineState);
        infoLayout = findViewById(R.id.infoLayout);

        textView = findViewById(R.id.distanceText);
        fbDistance = findViewById(R.id.distanceFromFirebase);
        startDistanceCountButton = findViewById(R.id.btn_start);
        taxiStatebutton = findViewById(R.id.taxiStateButton);
        setofflineButton = findViewById(R.id.setOfflineButton);
    }



    private Bitmap createSmallMarkersIcon(int iconRes) {
        int height = 100;
        int width = 100;
        BitmapDrawable bitmapdraw=(BitmapDrawable)getResources().getDrawable(iconRes);
        Bitmap b=bitmapdraw.getBitmap();
        return Bitmap.createScaledBitmap(b, width, height, false);
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Add a marker in Sydney and move the camera
/*        LatLng sydney = new LatLng(-34, 151);
        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));*/

        mMap.setMaxZoomPreference(17);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(42.8520033088458, 74.62109389128251), 15));

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST && grantResults.length == 1
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            doBindService();
        } else {
            finish();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
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
                   // mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 300));
                    //mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 2000, null);


                    if (isOnline) {
                        updateGeoLocation(latLng);
                    }
                }

                @Override
                public void onChangeDistance(double distance) {
                    distance = Math.floor(distance * 100) / 100;
                    textView.setText(String.format("Distance: %s m.", distance));
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

    private void updateGeoLocation(LatLng latLng) {

        CollectionReference refAvailable = db.collection("driverAvailable");
        CollectionReference refWorking = db.collection("driverWorking");

        Map<String, String> geoMap = new HashMap<>();
        geoMap.put("latitude", String.valueOf(latLng.latitude));
        geoMap.put("longitude", String.valueOf(latLng.longitude));
        if (isWorking) {
            refWorking.document(mUser.getUid()).set(geoMap);
            refAvailable.document(mUser.getUid()).delete();
        } else {
            refAvailable.document(mUser.getUid()).set(geoMap);
            refWorking.document(mUser.getUid()).delete();
        }
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
            case R.id.btn_start:
                if (isWorking) {

                    displayCount();
                } else {


                    updateUI(STATE_FINISH_REQUEST);
                }
                break;
            case R.id.logoutButton:
                logout();
                break;
            case R.id.setOnlineButton:
                isOnline = true;
                updateUI(CHANGE_STATUS);
                updateUI(SHOW_MAP_STATE);
                Log.e(TAG, "setOnlineButton");
                break;

            case R.id.setOfflineButton:
                isOnline = false;
                updateUI(CHANGE_STATUS);
                db.collection("driverAvailable").document(mUser.getUid()).delete();
                db.collection("driverAvailable").document(mUser.getUid()).collection("customerRequest").document(mUser.getUid()).delete();

                break;

            case R.id.taxiStateButton:
                notifyDriverArrived();
                break;
        }

    }


    private void displayCount() {
        if (!mBoundService.isTrack) {

            startDistanceCountButton.setText("finish");

            Map<String, Object> data = new HashMap<>();
            data.put("car_id", "Talgat");
            data.put("total_distance", 0);

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

            textView.setText("Distance: 0 m");


        } else {
            mBoundService.isTrack = false;
            mBoundService.index = 0;
            textView.setText(String.format("last Distance: %s m", distan));
            startDistanceCountButton.setText("End request");


            if (startMarker != null) {
                startMarker.remove();
                startMarker = null;
            }
            deleteCusRequest(customerId);

            isWorking = false;
        }
    }

    private void logout() {
        FirebaseAuth.getInstance().signOut();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
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

                    Double distance = (Double) documentSnapshot.get("total_distance");
                    if (distance != null && distance >0) {
                        distance = (distance * 1000)/ 1000;
                    }
                    fbDistance.setText("FB distance: " + distance + "km");

                } else {
                    Log.d(TAG, "Current data: null");
                }
            }
        });

    }


    private void updateUI(int state) {
        Log.e(TAG, "updateUI");
        switch (state) {
            case CHANGE_STATUS:
                if (isOnline) {
                    onlineStateLayout.setVisibility(View.VISIBLE);
                    offlineStateLayout.setVisibility(View.GONE);
                } else {
                    offlineStateLayout.setVisibility(View.VISIBLE);
                    onlineStateLayout.setVisibility(View.GONE);
                }
                break;

            case STATE_TAXI_HEADING_TO_THE_CUSTOMER:
                taxiStatebutton.setVisibility(View.VISIBLE);
                setofflineButton.setVisibility(View.GONE);
                break;

            case STATE_TAXI_ARRIVED:
                taxiStatebutton.setVisibility(View.GONE);
                infoLayout.setVisibility(View.VISIBLE);
                break;

            case STATE_FINISH_REQUEST:
                infoLayout.setVisibility(View.GONE);
                setofflineButton.setVisibility(View.VISIBLE);
                startDistanceCountButton.setText("Start");
        }
    }



    EventListener<QuerySnapshot> checkRequest = new EventListener<QuerySnapshot>() {
        @Override
        public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
            if (e != null) {
                Log.e(TAG, "Listen failed" + e);
                return;
            }
            if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {

                String lat = "";
                String lng = "";
                String customerRequestId = "";
                for (DocumentChange dc : queryDocumentSnapshots.getDocumentChanges()) {
                    if (dc.getType() == DocumentChange.Type.ADDED) {
                        customerRequestId = (String) dc.getDocument().get("customerId");
                        customerId = customerRequestId;
                        lat = (String) dc.getDocument().get("latitude");
                        lng = (String) dc.getDocument().get("longitude");

                    }
                }


                AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(MapsActivity.this);
                dialogBuilder.setTitle("Принять заказ?");
                dialogBuilder.setMessage("Клиент находится в: " + lat + " " + lng);


                Double finalLat = Double.valueOf(lat);
                Double finalLng = Double.valueOf(lng);
                final LatLng clientLocation = new LatLng(finalLat, finalLng);
                final String finalCustomerRequestId = customerRequestId;

                final LatLngBounds.Builder latlngbuilder = new LatLngBounds.Builder();
                latlngbuilder.include(clientLocation).include(lastGeo);

                dialogBuilder.setPositiveButton("Принять", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startMarker = mMap.addMarker(new MarkerOptions()
                                .icon(BitmapDescriptorFactory.fromBitmap(customersBitmap))
                                .position(clientLocation)
                                .title("Клиент"));
                        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(latlngbuilder.build(), 100));
                        changeCurrentStatus(finalCustomerRequestId);
                    }
                });
                dialogBuilder.setNegativeButton("Отклонить", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        cancelRequest(finalCustomerRequestId);
                    }
                });
                if (isOnline) {
                    dialogBuilder.show();
                }

            }
        }
    };


    private void changeCurrentStatus(String docId) {
        Map<String, Object> map = new HashMap<>();
        map.put("driverId", mUser.getUid());

        db.collection("customerRequest").document(docId).update(map);
        updateUI(STATE_TAXI_HEADING_TO_THE_CUSTOMER);
        isWorking = true;
    }

    private void cancelRequest(final String docId) {

        db.collection("customerRequest").document(docId)
                .get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        ArrayList<String>  list = (ArrayList<String>) documentSnapshot.get("blackDriverId");
                        if (list == null)
                            list = new ArrayList<>();
                        list.add(mUser.getUid());

                        Map<String, Object> map = new HashMap<>();

                        map.put("blackDriverId", list);

                        db.collection("customerRequest").document(docId).update(map);
                    }
                });
        deleteCusRequest(docId);
    }


    private void notifyDriverArrived() {
        Map<String, Object> map = new HashMap<>();
        map.put("arrived", true);

        db.collection("customerRequest").document(customerId).update(map);

        updateUI(STATE_TAXI_ARRIVED);
    }


    private void deleteCusRequest(String docId) {
        requestRef.document(docId).delete();
    }
}
