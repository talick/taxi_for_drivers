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
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback{

    private static final int STATE_FINISH_REQUEST = 6;
    private GoogleMap mMap;
    private TextView textView;
    private TextView fbDistance;

    RecyclerView rv;
    private RequestAdapter adapter;
    private List<Request> requests;

    private final String TAG = MainActivity.class.getSimpleName();
    private static final int PERMISSION_REQUEST = 1;

    private static final int CHANGE_STATUS = 1;
    private static final int SHOW_MAP_STATE = 2;
    private static final int SHOW_INFO_STATE = 3;
    private static final int STATE_TAXI_ARRIVED = 4;
    private static final int STATE_TAXI_HEADING_TO_THE_CUSTOMER = 5;

    private static final int CATEGORY_BOOK = 6;
    private static final int CATEGORY_MAP = 7;
    private static final int CATEGORY_STATUS = 8;


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


    SupportMapFragment mapFragment;
    private ViewGroup booksLayout;
    private ViewGroup statusLayout;
    private ViewGroup mapsLayout;

    private ViewGroup bookInfoLayout;
    private ViewGroup infoLayout;
    private TextView taxiStatebutton1;
    private TextView taxiStatebutton;

    private Button startDistanceCountButton;

    //private CollectionReference requestRef;

    String customerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            logout();
        } else {
            setContentView(R.layout.activity_main);
            // Obtain the SupportMapFragment and get notified when the map is ready to be used.
            mapFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.map2);
            mapFragment.getMapAsync(this);


            db = FirebaseFirestore.getInstance();
            mAuth = FirebaseAuth.getInstance();
            mUser = mAuth.getCurrentUser();

            customersBitmap = createSmallMarkersIcon(R.drawable.people_map_marker);
            driversBitmap = createSmallMarkersIcon(R.drawable.vehile_map_marker);



            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(this, "Please enable location services", Toast.LENGTH_SHORT).show();
                finish();
            }

            int permission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION);

            if (permission == PackageManager.PERMISSION_GRANTED) {
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        PERMISSION_REQUEST);
            }

            initViews();


            initBotNavView();
            initRadioGr();


            initRV();

        }


    }

    private void initBotNavView() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {

                switch (item.getItemId()) {
                    case R.id.category_books:

                        updateUII(CATEGORY_BOOK);
                        break;
                    case R.id.category_map:
                        updateUII(CATEGORY_MAP);
                        break;
                    case R.id.category_status:
                        updateUII(CATEGORY_STATUS);
                        break;
                }
                return true;
            }
        });
        bottomNavigationView.setSelectedItemId(R.id.category_status);
    }

    private void initRadioGr() {
        RadioGroup radioGroup = findViewById(R.id.radios);
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.statusOnline:
                        break;
                }
            }
        });
    }



    ListenerRegistration eventListReg;
    private void initRV() {
        rv = findViewById(R.id.request_list_rv);
        rv.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        adapter = new RequestAdapter(requests);
        rv.setAdapter(adapter);

        adapter.setListener(rvItemClicklistener);

        requests = new ArrayList<>();
        Log.e(TAG, "TGA");

        eventListReg = FirebaseFirestore.getInstance().collection("requests")
                .addSnapshotListener(eventListener);


    }

    EventListener eventListener = new EventListener<QuerySnapshot>() {
        @Override
        public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
            Log.e(TAG, "Event Listener working");
            if (e != null) {
                Log.e(TAG, "listen:error", e);
                return;
            }

            for (DocumentChange dc : queryDocumentSnapshots.getDocumentChanges()) {
                Request request = dc.getDocument().toObject(Request.class);
                if (dc.getType() == DocumentChange.Type.ADDED && !requests.contains(request)) {
                    requests.add(request);
                    Log.e(TAG, "eventListener added: " + request.getAddress());
                }


                if (dc.getType() == DocumentChange.Type.REMOVED) {

                    requests.remove(dc.getOldIndex());
                }
            }

            adapter.setRequests(requests);
        }
    };


    RequestAdapter.OnClickItemListener rvItemClicklistener = new RequestAdapter.OnClickItemListener() {
        @Override
        public void onItemClick(final int pos) {
            if (!isOnline) {
                Toast.makeText(MainActivity.this, "поменяйте статус на онлайн", Toast.LENGTH_SHORT).show();
                return;
            }
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(MainActivity.this);
            dialogBuilder.setTitle("Принять заказ?");
            dialogBuilder.setMessage("Адрес: " + requests.get(pos).getAddress());

            dialogBuilder.setPositiveButton("Принять", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                    isWorking = true;
                    eventListReg.remove();
                    db.collection("requests").document(requests.get(pos).getCustomerId()).delete().addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if (!task.isSuccessful()) {
                                Toast.makeText(MainActivity.this, "Не удалось принять заказ", Toast.LENGTH_SHORT).show();
                            } else {
                                rv.setVisibility(View.GONE);
                                bookInfoLayout.setVisibility(View.VISIBLE);
                                taxiStatebutton.setVisibility(View.VISIBLE);
                                taxiStatebutton1.setVisibility(View.VISIBLE);

                                startActivity(new Intent(MainActivity.this, CounterActivity.class));
                                finish();

                                int permission = ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.ACCESS_FINE_LOCATION);

                                if (permission == PackageManager.PERMISSION_GRANTED) {
                                    //doBindService();
                                } else {
                                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                            PERMISSION_REQUEST);
                                }

                                if (startMarker == null) {
                                    startMarker = mMap.addMarker(new MarkerOptions()
                                            .icon(BitmapDescriptorFactory.fromBitmap(customersBitmap))
                                            .title("Current Pos")
                                            .position(new LatLng(Double.valueOf(requests.get(pos).getLatitude()), Double.valueOf(requests.get(pos).getLongitude()))));
                                }
                            }
                        }
                    });
                }
            });
            dialogBuilder.setNegativeButton("Отклонить", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });
            if (isOnline) {
                dialogBuilder.show();
            }
        }

        @Override
        public void onItemLongClick(int pos) {

        }
    };

    private void initViews() {
        booksLayout = findViewById(R.id.booksLayout);
        statusLayout = findViewById(R.id.statusLayout);
        mapsLayout = findViewById(R.id.mapsLayout);
        bookInfoLayout = findViewById(R.id.bookInfoLayout);
        infoLayout = findViewById(R.id.infoLayout);

        textView = findViewById(R.id.distanceText);
        fbDistance = findViewById(R.id.distanceFromFirebase);
        startDistanceCountButton = findViewById(R.id.btn_start);
        taxiStatebutton = findViewById(R.id.taxiStateButton);
        taxiStatebutton1 = findViewById(R.id.taxiStateButton1);
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
        LatLng sydney = new LatLng(-34, 151);
        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));

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
//                     mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 300));
                    mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 2000, null);


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

                    if (documentSnapshot.get("total_distance") instanceof Double) {
                        Double distance = (Double) documentSnapshot.get("total_distance");
                        if (distance != null && distance >0) {
                            distance = (distance * 1000)/ 1000;
                        }
                        fbDistance.setText("FB distance: " + distance + "km");
                    } else if (documentSnapshot.get("total_distance") instanceof Long) {
                        Long distance = (Long) documentSnapshot.get("total_distance");
                        if (distance != null && distance >0) {
                            distance = (distance * 1000)/ 1000;
                        }
                        fbDistance.setText("FB distance: " + distance + "km");
                    }



                } else {
                    Log.d(TAG, "Current data: null");
                }
            }
        });

    }

    private void updateUII(int state) {
        switch (state) {
            case CATEGORY_BOOK:
                statusLayout.setVisibility(View.GONE);
                booksLayout.setVisibility(View.VISIBLE);
                mapsLayout.setVisibility(View.GONE);

                break;
            case CATEGORY_MAP:
                statusLayout.setVisibility(View.GONE);
                booksLayout.setVisibility(View.GONE);
                mapsLayout.setVisibility(View.VISIBLE);
                break;
            case CATEGORY_STATUS:
                statusLayout.setVisibility(View.VISIBLE);
                booksLayout.setVisibility(View.GONE);
                mapsLayout.setVisibility(View.GONE);
                break;

        }
    }


    private void updateUI(int state) {
        Log.e(TAG, "updateUI");
        switch (state) {
            case CHANGE_STATUS:
                if (isOnline) {
                    booksLayout.setVisibility(View.VISIBLE);
                    statusLayout.setVisibility(View.GONE);
                } else {
                    statusLayout.setVisibility(View.VISIBLE);
                    booksLayout.setVisibility(View.GONE);
                }
                break;

            case STATE_TAXI_HEADING_TO_THE_CUSTOMER:
                taxiStatebutton.setVisibility(View.VISIBLE);
                break;

            case STATE_TAXI_ARRIVED:
                taxiStatebutton.setVisibility(View.GONE);
                infoLayout.setVisibility(View.VISIBLE);
                break;

            case STATE_FINISH_REQUEST:
                infoLayout.setVisibility(View.GONE);
                infoLayout.setVisibility(View.GONE);
                rv.setVisibility(View.VISIBLE);
                Objects.requireNonNull(mapFragment.getView()).setVisibility(View.GONE);
                startDistanceCountButton.setText("Start");
        }
    }






    private void changeCurrentStatus(String docId) {
        Map<String, Object> map = new HashMap<>();
        map.put("driverId", mUser.getUid());

        db.collection("customerRequest").document(docId).update(map);
        updateUI(STATE_TAXI_HEADING_TO_THE_CUSTOMER);
        isWorking = true;
    }



    private void notifyDriverArrived() {
/*        Map<String, Object> map = new HashMap<>();
        map.put("arrived", true);

        db.collection("customerRequest").document(customerId).update(map);
*/
        updateUI(STATE_TAXI_ARRIVED);
    }




    public void onClickRadio(View view) {
        boolean checked = ((RadioButton) view).isChecked();
        // Получаем нажатый переключатель
        switch(view.getId()) {
            case R.id.statusOnline:
                if (checked){
                    Log.e(TAG, "Выбран Online");
                    isOnline = true;
                }
                break;
            case R.id.statusPause:
                if (checked){
                    Log.e(TAG, "Выбран pause");
                    isOnline = false;
                    db.collection("driverAvailable").document(mUser.getUid()).delete();

                }
                break;
                case R.id.statusRepair:
                    if (checked){
                        Log.e(TAG, "Выбран repair");
                        isOnline = false;
                        db.collection("driverAvailable").document(mUser.getUid()).delete();

                    }
                    break;

        }
    }
}


