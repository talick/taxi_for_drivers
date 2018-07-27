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
import android.support.v7.widget.DividerItemDecoration;
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

import com.example.talgat.distancecounter.model.DirectionInfo;
import com.example.talgat.distancecounter.model.Request;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
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
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
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
import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.errors.ApiException;
import com.google.maps.model.DirectionsResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import static com.example.talgat.distancecounter.utils.BitmapUtils.createSmallMarkersIcon;

public class MainActivity extends AppCompatActivity implements
        OnMapReadyCallback,
        GoogleMap.OnMarkerClickListener,
        GoogleMap.OnMapClickListener{

    private GoogleMap mMap;

    RecyclerView rv;
    private RequestAdapter adapter;
    private List<Request> requests;

    private final String TAG = MainActivity.class.getSimpleName();
    private static final int PERMISSION_REQUEST = 1;

    private static final int CATEGORY_BOOK = 6;
    private static final int CATEGORY_MAP = 7;
    private static final int CATEGORY_STATUS = 8;

    LocationCallback locationCallback;
    Location currentLocation;

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


    SupportMapFragment mapFragment;
    private ViewGroup booksLayout;
    private ViewGroup statusLayout;
    private ViewGroup mapsLayout;

    int online;

    Map<String, Marker> customersMarkers;

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

            customersBitmap = createSmallMarkersIcon(R.drawable.people_map_marker, this);
            driversBitmap = createSmallMarkersIcon(R.drawable.vehile_map_marker, this);

             online = getIntent().getIntExtra("isOnline", 0);
            customersMarkers = new HashMap<>();

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

            locationCallback = initLocationCallback();
            requestLocationUpdates();

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
        if (online == 1) {
            bottomNavigationView.setSelectedItemId(R.id.category_books);
            ((RadioButton) findViewById(R.id.statusOnline)).setChecked(true);
            isOnline = true;
        }else bottomNavigationView.setSelectedItemId(R.id.category_status);
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
        rv.addItemDecoration(new DividerItemDecoration(this, LinearLayoutManager.VERTICAL));
        adapter = new RequestAdapter(requests, MainActivity.this);
        rv.setAdapter(adapter);

        adapter.setListener(rvItemClicklistener);

        requests = new ArrayList<>();
        Log.e(TAG, "TGA");

        eventListReg = FirebaseFirestore.getInstance().collection("currentRequests")
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
                    LatLng latLng = new LatLng(Double.valueOf(request.getLatitude()), Double.valueOf(request.getLongitude()));
                    customersMarkers.put(request.getCustomerId(), mMap.addMarker(new MarkerOptions()
                            .icon(BitmapDescriptorFactory.fromBitmap(customersBitmap))
                            .title("Customer")
                            .position(latLng)));
                    Log.e(TAG, "eventListener added: " + request.getAddress());
                }


                if (dc.getType() == DocumentChange.Type.REMOVED) {

                    Request request1 = dc.getDocument().toObject(Request.class);
                    Log.e(TAG, String.valueOf(requests.indexOf(request1)));
                    requests.remove(request1);
                    customersMarkers.get(request1.getCustomerId()).remove();
                    customersMarkers.remove(request1.getCustomerId());
                }



                if (requests.isEmpty()) {
                    hideRV();
                } else {
                    showRV();
                }
            }

            adapter.setRequests(requests);
        }
    };


    private void hideRV() {
        rv.setVisibility(View.GONE);
        findViewById(R.id.no_books_notification_text_view).setVisibility(View.VISIBLE);
    }
    private void showRV() {
        rv.setVisibility(View.VISIBLE);
        findViewById(R.id.no_books_notification_text_view).setVisibility(View.GONE);
    }


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

                    eventListReg.remove();
                    db.collection("currentRequests").document(requests.get(pos).getCustomerId()).delete().addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if (!task.isSuccessful()) {
                                Toast.makeText(MainActivity.this, "Не удалось принять заказ", Toast.LENGTH_SHORT).show();
                            } else {
                                Intent intent = new Intent(MainActivity.this, CounterActivity.class);
                                intent.putExtra("request", requests.get(pos));
                                startActivity(intent);
                                finish();
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
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.getUiSettings().setRotateGesturesEnabled(false);
        mMap.setOnMarkerClickListener(this);
        mMap.setOnMapClickListener(this);

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST && grantResults.length == 1
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        } else {
            finish();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.logoutButton:
                logout();
                break;
        }

    }




    private void logout() {
        FirebaseAuth.getInstance().signOut();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
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

                }
                break;
                case R.id.statusRepair:
                    if (checked){
                        Log.e(TAG, "Выбран repair");
                        isOnline = false;

                    }
                    break;

        }
    }




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

                if (location != null) {

                    LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

                    currentLocation = location;

                    if (marker == null) {
                        marker = mMap.addMarker(new MarkerOptions()
                                .icon(BitmapDescriptorFactory.fromBitmap(driversBitmap))
                                .title("Current Pos")
                                .position(latLng));
                    } else {
                        marker.setPosition(latLng);
                    }
                }

            }
        };
    }

    Polyline polyline;

    @Override
    public boolean onMarkerClick(Marker marker) {
        if (marker.equals(startMarker))
            return false;

        GeoApiContext geoApiContext = new GeoApiContext.Builder()
                .apiKey("AIzaSyASE9VN6z8Ir_4W8Kkicb8R4nBTeEqIydM")
                .build();

        Log.e(TAG, "onmarker Click2");
        DirectionsResult result = null;
        try {
            Log.e(TAG, "onmarker Click");
            result = DirectionsApi.newRequest(geoApiContext)
                    .origin(new com.google.maps.model.LatLng(currentLocation.getLatitude(), currentLocation.getLongitude()))
                    .destination(new com.google.maps.model.LatLng(marker.getPosition().latitude, marker.getPosition().longitude))
                    .await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ApiException e) {
            e.printStackTrace();
        }

        List<com.google.maps.model.LatLng> path = result.routes[0].overviewPolyline.decodePath();
        if (polyline == null) {
            PolylineOptions line = new PolylineOptions();
            LatLngBounds.Builder builder = new LatLngBounds.Builder();

            for (int i = 0; i<path.size(); i++) {
                line.add(new LatLng(path.get(i).lat, path.get(i).lng));
                builder.include(new LatLng(path.get(i).lat, path.get(i).lng));
            }

            line.width(16f).color(R.color.colorAccent);
            polyline = mMap.addPolyline(line);
        } else {
            List<LatLng> list = new ArrayList<>();
            for (int i = 0; i < path.size(); i++) {
                list.add(new LatLng(path.get(i).lat, path.get(i).lng));
            }
            polyline.setPoints(list);
        }

        String info = getEndLocationTitle(result);
        Log.e(TAG, info);
        marker.setTitle(info);


        return true;
    }

    private DirectionInfo createDirecitonInfo(Request request) {
        GeoApiContext geoApiContext = new GeoApiContext.Builder()
                .apiKey("AIzaSyASE9VN6z8Ir_4W8Kkicb8R4nBTeEqIydM")
                .build();

        Log.e(TAG, "onmarker Click2");
        DirectionsResult result = null;
        try {
            Log.e(TAG, "onmarker Click");
            result = DirectionsApi.newRequest(geoApiContext)
                    .origin(new com.google.maps.model.LatLng(currentLocation.getLatitude(), currentLocation.getLongitude()))
                    .destination(new com.google.maps.model.LatLng(Double.valueOf(request.getLatitude()), Double.valueOf(request.getLongitude())))
                    .await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ApiException e) {
            e.printStackTrace();
        }

        List<com.google.maps.model.LatLng> path = result.routes[0].overviewPolyline.decodePath();
        long distance = result.routes[0].legs[0].distance.inMeters;
        String time = result.routes[0].legs[0].duration.humanReadable;

        return new DirectionInfo(request.getCustomerId(), path, time, distance);
    }

    private String getEndLocationTitle(DirectionsResult results){
        return  "Time :"+ results.routes[0].legs[0].duration.humanReadable +
                " Distance :" + results.routes[0].legs[0].distance.humanReadable;
    }
    @Override
    public void onMapClick(LatLng latLng) {
        if (polyline != null) {
            polyline.remove();
            polyline = null;
        }
    }
}


