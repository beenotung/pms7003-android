package com.github.beenotung.pms7003_android;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
//import com.google.android.gms.location.LocationListener;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private SimpleWebServer simpleWebServer;
    private TextView tvStatus;
    private FloatingActionButton fab;
    boolean isActive = false;
    boolean isReady = false;
    boolean isGPSAvtive = false;

    SimpleDateFormat timeFormatter = new SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm:ss.SSSS a");
    private LocationManager locationManager;
    private LocationListener locationListener;

    String getTimestamp() {
        Calendar now = Calendar.getInstance();
        return timeFormatter.format(now.getTime());
    }

    void setStatusWithTimestamp(String s) {
        setStatus(getTimestamp() + "\n" + s);
    }

    void getGPS() {
        final String TAG = "GPS";
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isReady && !isGPSAvtive) {
                    Log.i(TAG, "ask for GPS");
                    locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER, 5000, 10, locationListener);
                    isGPSAvtive = true;
                }
                if (isReady) {
                    Location location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    dataLogger.writeToFile("alt=" + location.getAltitude() + " lat=" + location.getLatitude() + " lng=" + location.getLongitude());
                    Log.i(TAG, "location: " + location);
                }
            }
        });
    }

    void setStatus(final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isActive) {
                    tvStatus.setText(s);
                }
            }
        });
    }

    void startServer() {
        fab.setImageResource(android.R.drawable.presence_offline);
        tvStatus.setText("Running");
        simpleWebServer = new SimpleWebServer(8181, getAssets(), dataLogger);
        simpleWebServer.onConnected(new J8.Consumer<Socket>() {
            @Override
            public void apply(final Socket socket) {
                isReady = true;
                setStatusWithTimestamp("Connected from: " + socket.getRemoteSocketAddress());
            }
        });
        simpleWebServer.onReceived(new J8.Consumer<Socket>() {
            @Override
            public void apply(final Socket socket) {
                setStatusWithTimestamp("Received data from: " + socket.getRemoteSocketAddress());
                getGPS();
            }
        });
        simpleWebServer.start();
    }

    void stopServer() {
        isReady = false;
        fab.setImageResource(android.R.drawable.ic_menu_upload);
        tvStatus.setText("Idle");
        if (simpleWebServer != null) {
            simpleWebServer.stop();
            simpleWebServer = null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
                if (simpleWebServer == null) {
                    startServer();
                    Snackbar.make(view, "starting", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                } else {
                    stopServer();
                    Snackbar.make(view, "stopping", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                }
            }
        });

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        tvStatus = (TextView) findViewById(R.id.tvStatus);
        isActive = true;

        locationManager = (LocationManager)
                getSystemService(Context.LOCATION_SERVICE);

        locationListener = new MyLocationListener();

        /* reference : http://stackoverflow.com/questions/32083913/android-gps-requires-access-fine-location-error-even-though-my-manifest-file */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!canAccessLocation()) {
                requestPermissions(INITIAL_PERMS, INITIAL_REQUEST);
            }
        }

        try {
            dataLogger = new DataLogger();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (canAccessLocation() && simpleWebServer == null)
            startServer();
        else
            stopServer();
    }

    private static final String[] INITIAL_PERMS = {
            Manifest.permission.ACCESS_FINE_LOCATION
            , Manifest.permission.ACCESS_COARSE_LOCATION
//            , Manifest.permission.READ_CONTACTS
    };
    private static final int INITIAL_REQUEST = 1337;

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_camera) {
            // Handle the camera action
        } else if (id == R.id.nav_gallery) {

        } else if (id == R.id.nav_slideshow) {

        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActive = true;
    }

    @Override
    protected void onPause() {
        isActive = false;
        super.onPause();
    }

    @Override
    protected void onStop() {
        stopServer();
        super.onStop();
    }

    class MyLocationListener implements LocationListener {
        final String TAG = "Location";

        @Override
        public void onLocationChanged(Location location) {
            Log.i(TAG, "onLocationChanged: " + location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Log.i(TAG, "onStatusChanged: " + status);
        }

        @Override
        public void onProviderEnabled(String provider) {
            Log.i(TAG, "onProviderEnabled: " + provider);
        }

        @Override
        public void onProviderDisabled(String provider) {
            Log.i(TAG, "onProviderDisabled: " + provider);
        }
    }

    private boolean hasPermission(String perm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return (PackageManager.PERMISSION_GRANTED == checkSelfPermission(perm));
        }
        return true;
    }

    private boolean canAccessLocation() {
        return (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                && hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION));
    }

    DataLogger dataLogger;

    class DataLogger {
        final String TAG = "DataLogger";
        private BufferedWriter out;


        /*
        * to try : http://stackoverflow.com/questions/1756296/android-writing-logs-to-text-file
        *
File outputFile = new File("pathToFile");
Runtime.getRuntime().exec("logcat -c");
Runtime.getRuntime().exec("logcat -v time -f " + file.getAbsolutePath())
        *
        * */

        DataLogger() throws IOException {
            createFileOnDevice(true);
//            createFileOnDevice2();
        }

        private void createFileOnDevice2() throws IOException {
//            File logFile = new File("sdcard/log.file");
            File logFile = new File("/storage/emulated/0/Android/data/com.github.beenotung.pms7003_android/files/pms7003_log.txt");
            if (!logFile.exists()) {
                try {
                    logFile.createNewFile();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            out = new BufferedWriter(new FileWriter(logFile, true));
        }

        private void createFileOnDevice(Boolean append) throws IOException {
                /*
                 * Function to initially create the log file and it also writes the time of creation to file.
                 */
            File rootFolder = new File(Environment.getDataDirectory().getPath() + "/Android/data/com.github.beenotung.pms7003_android/files");
            if (!rootFolder.exists()) {
                if (!rootFolder.mkdirs()) {
                    Log.e(TAG, "createFileOnDevice: failed to create root folder");
                }
            }
            if (rootFolder.canWrite()) {
                File LogFile = new File(rootFolder, "pms7003_log.txt");
                FileWriter LogWriter = new FileWriter(LogFile, append);
                out = new BufferedWriter(LogWriter);
            } else {
                Log.e(TAG, "createFileOnDevice: cannot write to the file: " + rootFolder);
            }
        }

        public void writeToFile(String message) {
            try {
                out.write("\nTime: " + getTimestamp() + "\n");
                out.write(message + "\n");
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
