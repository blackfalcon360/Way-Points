package com.blackfalcon.waypoints;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings("deprecation")
public class MainActivity extends Activity implements SensorEventListener {

    private static final int GREEN = 0xFF2ECC71, GRAY = 0xFF9E9E9E, CARD = 0xFF0C0C1E, EDGE = 0xFF3A3A60,
            AMBER = 0xFFFFB300, BLUE = 0xFF4FC3F7, RED = 0xFFE57373;
    private static final int REQ_PERM = 1, REQ_GPX = 2, REQ_CSV = 3, REQ_IMPORT = 4;
    private static final int[] ARRIVAL = {0, 10, 25, 50, 100};

    private SharedPreferences prefs;
    private LocationManager lm;
    private SensorManager sm;
    private Sensor rot;
    private WaypointStore store;
    private final Fmt fmt = new Fmt();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    // settings
    private int sortMode, catFilter = -1, arrivalIdx;
    private boolean trueNorth, keepOn, showOthers;

    // position
    private boolean hasFix = false, running = false;
    private double fLat, fLon, fAlt = Double.NaN;
    private float fAcc = 99f, fSpd = 0f, fBrg = 0f;
    private long fixMs = 0, lastGpsMs = 0;
    private float declination = 0f;

    // compass
    private final float[] rotMat = new float[9];
    private float sinAvg = 0f, cosAvg = 1f, magHeading = 0f;
    private boolean haveHeading = false, firstSample = true;
    private long targetId = -1;
    private boolean arrivedFlag = false;

    // ui state
    private int tab = 0, overlay = 0;
    private long lastListMs = 0;
    private final TextView[] tabs = new TextView[3];
    private final View[] pages = new View[5];
    private TextView listGpsTv, tgtNameTv, distTv, bearingTv, hintTv, extraTv, noteTv, tgtBtnChoose, tgtBtnStop;
    private EditText searchEt;
    private LinearLayout sortBox, catBox, listBox, detailBox, editBox;
    private CompassView compass;
    private TextView othersChip;

    // edit state
    private Waypoint editing;
    private EditText nameEt, notesEt, latEt, lonEt, altEt;
    private int editCat;
    private boolean editFav;
    private TextView avgStatus;
    private boolean avgActive = false;
    private int avgSec, avgN;
    private double sumW, sumLat, sumLon, sumAlt, sumAltW;

    // exit confirmation state
    private boolean doubleBackToExitPressedOnce = false;

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private String t(String k) { return fmt.t(k); }

    // ------------------------------------------------------------ callbacks
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateGpsLines();
            if (tab == 1 && overlay == 0) updateCompass();
            handler.postDelayed(this, 1000);
        }
    };

    private final LocationListener locListener = new LocationListener() {
        @Override public void onLocationChanged(Location l) { onFix(l); }
        @Override public void onProviderEnabled(String p) { }
        @Override public void onProviderDisabled(String p) { }
        @Override public void onStatusChanged(String p, int s, Bundle e) { }
    };

    @android.annotation.TargetApi(34)
    private static double msl(Location l) { return l.hasMslAltitude() ? l.getMslAltitudeMeters() : l.getAltitude(); }

    // ------------------------------------------------------------- lifecycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("waypoints", MODE_PRIVATE);
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rot = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        store = new WaypointStore(new File(getFilesDir(), "waypoints.tsv"));

        fmt.lang = prefs.getInt("lang", Lang.EN);                 
        fmt.imperial = prefs.getBoolean("imperial", false);
        fmt.coordFmt = prefs.getInt("coordFmt", Geo.FMT_DD);
        trueNorth = prefs.getBoolean("trueNorth", true);
        arrivalIdx = prefs.getInt("arrival", 2);
        keepOn = prefs.getBoolean("keepOn", true);
        showOthers = prefs.getBoolean("others", false);
        sortMode = prefs.getInt("sort", 0);
        targetId = prefs.getLong("target", -1);
        tab = prefs.getInt("tab", 0);
        if (prefs.contains("lat")) {
            fLat = Double.longBitsToDouble(prefs.getLong("lat", 0));
            fLon = Double.longBitsToDouble(prefs.getLong("lon", 0));
        }
        buildUi();
        if (!hasPerm()) requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_PERM);

        // Background service start karne ke liye taa k Home button se off na ho aur notification panel mein chale
        Intent serviceIntent = new Intent(this, WaypointService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        running = true;
        handler.post(ticker);
        startLocation();
        syncSensor();
        refreshList();
    }

    @Override
    protected void onPause() {
        super.onPause();
        running = false;
        handler.removeCallbacks(ticker);
        try { lm.removeUpdates(locListener); } catch (Exception e) { }
        sm.unregisterListener(this);
        avgActive = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onBackPressed() {
        if (overlay != 0) { closeOverlay(); return; }
        if (tab != 0) { tab = 0; showTab(); return; }
        
        // Exit Confirmation (Double tap to exit)
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }

        this.doubleBackToExitPressedOnce = true;
        toast("App se bahar jaane ke liye dobara Back dabayein");

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                doubleBackToExitPressedOnce = false;
            }
        }, 2000);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        startLocation();
        updateGpsLines();
    }

    private boolean hasPerm() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean gpsEnabled() {
        try { return lm.isProviderEnabled(LocationManager.GPS_PROVIDER); } catch (Exception e) { return false; }
    }

    // ------------------------------------------------------------- position and heading
    private void startLocation() {
        try { lm.removeUpdates(locListener); } catch (Exception e) { }
        if (!running || !hasPerm()) return;
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0f, locListener, Looper.getMainLooper());
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000, 0f, locListener, Looper.getMainLooper());
            Location a = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location b = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            Location best = a != null && (b == null || a.getTime() >= b.getTime()) ? a : b;
            if (best != null && !hasFix) onFix(best);
        } catch (SecurityException | IllegalArgumentException e) { }
    }

    private void onFix(Location l) {
        boolean gps = LocationManager.GPS_PROVIDER.equals(l.getProvider());
        long now = System.currentTimeMillis();
        if (!gps && hasFix && now - lastGpsMs < 20000) return;       
        if (gps) lastGpsMs = now;
        fLat = l.getLatitude();
        fLon = l.getLongitude();
        fAlt = l.hasAltitude() ? (Build.VERSION.SDK_INT >= 34 ? msl(l) : l.getAltitude()) : Double.NaN;
        fAcc = l.hasAccuracy() ? l.getAccuracy() : 99f;
        fSpd = l.hasSpeed() ? l.getSpeed() : 0f;
        fBrg = l.hasBearing() ? l.getBearing() : 0f;
        fixMs = now;
        hasFix = true;
        declination = new GeomagneticField((float) fLat, (float) fLon, (float) (Double.isNaN(fAlt) ? 0 : fAlt), now).getDeclination();
        prefs.edit().putLong("lat", Double.doubleToLongBits(fLat)).putLong("lon", Double.doubleToLongBits(fLon)).apply();
        if (avgActive) avgAdd();
        updateGpsLines();
        if (tab == 1 && overlay == 0) updateCompass();
        if (tab == 0 && overlay == 0 && now - lastListMs > 4000) refreshList();
    }

    private void syncSensor() {
        sm.unregisterListener(this);
        firstSample = true;
        if (running && tab == 1 && overlay == 0 && rot != null) sm.registerListener(this, rot, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    public void onSensorChanged(SensorEvent ev) {
        if (ev.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
        SensorManager.getRotationMatrixFromVector(rotMat, ev.values);
        double az;
        if (Math.abs(rotMat[8]) < 0.7f) az = Math.atan2(-rotMat[2], -rotMat[5]);     
        else az = Math.atan2(rotMat[1], rotMat[4]);                                   
        if (firstSample) { sinAvg = (float) Math.sin(az); cosAvg = (float) Math.cos(az); firstSample = false; }
        else { sinAvg += 0.18f * ((float) Math.sin(az) - sinAvg); cosAvg += 0.18f * ((float) Math.cos(az) - cosAvg); }
        magHeading = (float) ((Math.toDegrees(Math.atan2(sinAvg, cosAvg)) + 360.0) % 360.0);
        haveHeading = true;
        pushCompass();
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private double headingTrue() {
        if (rot != null && haveHeading) return (magHeading + declination + 360.0) % 360.0;
        if (hasFix && fSpd > 1.0f) return fBrg;
        return Double.NaN;
    }

    private GradientDrawable box(int fill, int edge, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(dp(1), edge);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private TextView tv(String text, int size, int color) {
        TextView x = new TextView(this);
        x.setText(text);
        x.setTextSize(size);
        x.setTextColor(color);
        return x;
    }

    private LinearLayout.LayoutParams lp(int w, int h, float weight) { return new LinearLayout.LayoutParams(w, h, weight); }

    private LinearLayout.LayoutParams weight(int l, int r) {
        LinearLayout.LayoutParams p = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(l, 0, r, 0);
        return p;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(box(CARD, EDGE, 14));
        c.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams l = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0);
        l.bottomMargin = dp(8);
        c.setLayoutParams(l);
        return c;
    }

    private TextView button(String text, int edge) {
        TextView b = tv(text, 15, Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(box(0xFF1C1C3A, edge, 12));
        b.setPadding(dp(10), dp(12), dp(10), dp(12));
        return b;
    }

    private TextView chip(String text, boolean selected, View.OnClickListener l) {
        TextView c = tv(text, 14, selected ? Color.WHITE : GRAY);
        c.setGravity(Gravity.CENTER);
        c.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        c.setBackground(box(selected ? 0xFF1C1C3A : CARD, selected ? GREEN : EDGE, 10));
        c.setPadding(dp(12), dp(9), dp(12), dp(9));
        c.setOnClickListener(l);
        return c;
    }

    private EditText input(String hint, String text) {
        EditText e = new EditText(new ContextThemeWrapper(this, android.R.style.Theme_Material_Light));
        e.setSingleLine(true);
        e.setHint(hint);
        e.setText(text);
        e.setTextColor(Color.BLACK);
        e.setHintTextColor(0xFF777777);
        e.setTextSize(16);
        e.setBackground(box(Color.WHITE, 0xFFBBBBBB, 10));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        return e;
    }

    private TextView label(String s) {
        TextView x = tv(s, 13, AMBER);
        x.setTypeface(Typeface.DEFAULT_BOLD);
        x.setPadding(dp(2), dp(10), 0, dp(4));
        return x;
    }

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(new ContextThemeWrapper(this, android.R.style.Theme_Material_Light_Dialog_Alert));
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void copy(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("waypoint", text));
        toast(t("copied"));
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setLayoutDirection(fmt.lang == Lang.EN ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(8), dp(6), dp(8), dp(4));

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setPadding(0, 0, 0, dp(8));
        String[] keys = {"tab_list", "tab_compass", "tab_set"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView x = tv(t(keys[i]), 14, Color.WHITE);
            x.setGravity(Gravity.CENTER);
            x.setPadding(0, dp(9), 0, dp(9));
            x.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    overlay = 0;
                    avgActive = false;
                    tab = idx;
                    prefs.edit().putInt("tab", tab).apply();
                    showTab();
                }
            });
            tabRow.addView(x, weight(dp(2), dp(2)));
            tabs[i] = x;
        }
        col.addView(tabRow);

        FrameLayout frame = new FrameLayout(this);
        pages[0] = buildList();
        pages[1] = buildCompassPage();
        pages[2] = buildSettings();
        ScrollView ds = new ScrollView(this);
        detailBox = new LinearLayout(this);
        detailBox.setOrientation(LinearLayout.VERTICAL);
        detailBox.setPadding(dp(2), dp(2), dp(2), dp(48));
        ds.addView(detailBox);
        pages[3] = ds;
        ScrollView es = new ScrollView(this);
        editBox = new LinearLayout(this);
        editBox.setOrientation(LinearLayout.VERTICAL);
        editBox.setPadding(dp(2), dp(2), dp(2), dp(48));
        es.addView(editBox);
        pages[4] = es;
        for (View v : pages) frame.addView(v);
        col.addView(frame, lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView credit = tv("By: Black Falcon \uD83E\uDD85", 12, GRAY);
        credit.setPadding(dp(8), dp(3), dp(8), dp(3));
        credit.setBackground(box(0xCC000000, EDGE, 8));
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT);
        cl.setMargins(0, 0, dp(8), dp(6));
        root.addView(credit, cl);

        setContentView(root);
        showTab();
        refreshList();
        updateGpsLines();
    }

    private void showTab() {
        for (int i = 0; i < 3; i++) {
            pages[i].setVisibility(overlay == 0 && i == tab ? View.VISIBLE : View.GONE);
            tabs[i].setBackground(box(i == tab ? 0xFF1C1C3A : CARD, i == tab ? BLUE : EDGE, 10));
            tabs[i].setTypeface(i == tab ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
        pages[3].setVisibility(overlay == 3 ? View.VISIBLE : View.GONE);
        pages[4].setVisibility(overlay == 4 ? View.VISIBLE : View.GONE);
        syncSensor();
        if (tab == 0 && overlay == 0) refreshList();
        if (tab == 1 && overlay == 0) updateCompass();
        boolean screenOn = keepOn && tab == 1 && overlay == 0 && running;
        if (screenOn) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void closeOverlay() {
        avgActive = false;
        overlay = 0;
        showTab();
    }

    private void updateGpsLines() {
        if (listGpsTv == null) return;
        long now = System.currentTimeMillis();
        String g;
        int c;
        if (!hasPerm()) { g = t("need_perm"); c = RED; }
        else if (!gpsEnabled()) { g = t("gps_off"); c = RED; }
        else if (!hasFix || now - fixMs > 20000) { g = t("gps_wait"); c = AMBER; }
        else {
            g = t("gps") + " \u00B1" + Math.round(fAcc) + " " + t("m") + "   " + fmt.coord(fLat, fLon);
            c = fAcc <= 10 ? GREEN : (fAcc <= 25 ? AMBER : RED);
        }
        listGpsTv.setText("\u25CF " + g);
        listGpsTv.setTextColor(c);
    }

    private View buildList() {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(2), dp(2), dp(2), dp(48));
        sv.addView(box);

        LinearLayout top = card();
        listGpsTv = tv("", 13, GRAY);
        top.addView(listGpsTv);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(8), 0, 0);
        TextView save = button(t("save_here"), GREEN), add = button(t("add"), BLUE);
        save.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { quickSave(); } });
        add.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openEdit(null); } });
        row.addView(save, weight(0, dp(4)));
        row.addView(add, weight(dp(4), 0));
        top.addView(row);
        box.addView(top);

        searchEt = input(t("search_hint"), "");
        searchEt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { refreshList(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        LinearLayout.LayoutParams sl = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0);
        sl.bottomMargin = dp(8);
        box.addView(searchEt, sl);

        HorizontalScrollView h1 = new HorizontalScrollView(this);
        h1.setHorizontalScrollBarEnabled(false);
        sortBox = new LinearLayout(this);
        h1.addView(sortBox);
        box.addView(h1);
        HorizontalScrollView h2 = new HorizontalScrollView(this);
        h2.setHorizontalScrollBarEnabled(false);
        catBox = new LinearLayout(this);
        h2.addView(catBox);
        LinearLayout.LayoutParams hp = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0);
        hp.topMargin = dp(6);
        hp.bottomMargin = dp(8);
        box.addView(h2, hp);

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(listBox);
        return sv;
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams l = lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0);
        l.rightMargin = dp(6);
        return l;
    }

    private void quickSave() {
        if (!hasFix || System.currentTimeMillis() - fixMs > 30000) { toast(t("gps_wait")); return; }
        Waypoint w = new Waypoint();
        w.name = t("wp_default") + " " + (store.count() + 1);
        w.lat = fLat;
        w.lon = fLon;
        w.alt = fAlt;
        w.time = System.currentTimeMillis();
        store.add(w);
        toast(t("saved_toast") + ": " + w.name);
        refreshList();
    }

    private void refreshList() {
        if (listBox == null) return;
        lastListMs = System.currentTimeMillis();
        List<Waypoint> all = store.all();
        final boolean near = sortMode == 0 && hasFix;

        sortBox.removeAllViews();
        String[] sk = {"s_near", "s_new", "s_name", "s_cat"};
        for (int i = 0; i < 4; i++) {
            final int si = i;
            sortBox.addView(chip((i == 0 ? "\u21C5 " : "") + t(sk[i]), sortMode == i, new View.OnClickListener() {
                @Override public void onClick(View v) { sortMode = si; prefs.edit().putInt("sort", si).apply(); refreshList(); }
            }), chipLp());
        }
        catBox.removeAllViews();
        catBox.addView(chip(t("all"), catFilter == -1, new View.OnClickListener() {
            @Override public void onClick(View v) { catFilter = -1; refreshList(); }
        }), chipLp());
        catBox.addView(chip("\u2605", catFilter == -2, new View.OnClickListener() {
            @Override public void onClick(View v) { catFilter = -2; refreshList(); }
        }), chipLp());
        boolean[] used = new boolean[Cats.COUNT];
        for (Waypoint w : all) used[w.cat] = true;
        for (int c = 0; c < Cats.COUNT; c++) {
            if (!used[c] && catFilter != c) continue;
            final int ci = c;
            catBox.addView(chip(Cats.EMOJI[c], catFilter == c, new View.OnClickListener() {
                @Override public void onClick(View v) { catFilter = ci; refreshList(); }
            }), chipLp());
        }

        String q = searchEt.getText().toString().trim().toLowerCase(Locale.getDefault());
        final List<Waypoint> shown = new ArrayList<>();
        for (Waypoint w : all) {
            if (catFilter == -2 && !w.fav) continue;
            if (catFilter >= 0 && w.cat != catFilter) continue;
            if (q.length() > 0 && !w.name.toLowerCase(Locale.getDefault()).contains(q) && !w.notes.toLowerCase(Locale.getDefault()).contains(q)) continue;
            shown.add(w);
        }
        final double[] dist = new double[shown.size()];
        final double[] brg = new double[shown.size()];
        final List<Integer> order = new ArrayList<>();
        for (int i = 0; i < shown.size(); i++) {
            Waypoint w = shown.get(i);
            dist[i] = hasFix ? Geo.distance(fLat, fLon, w.lat, w.lon) : Double.NaN;
            brg[i] = hasFix ? Geo.bearing(fLat, fLon, w.lat, w.lon) : Double.NaN;
            order.add(i);
        }
        Collections.sort(order, new Comparator<Integer>() {
            @Override public int compare(Integer a, Integer b) {
                Waypoint x = shown.get(a), y = shown.get(b);
                if (near) return Double.compare(dist[a], dist[b]);
                if (sortMode == 2) return x.name.compareToIgnoreCase(y.name);
                if (sortMode == 3) { int c = Integer.compare(x.cat, y.cat); return c != 0 ? c : x.name.compareToIgnoreCase(y.name); }
                return Long.compare(y.time, x.time);
            }
        });

        listBox.removeAllViews();
        if (all.isEmpty()) {
            LinearLayout c = card();
            c.addView(tv(t("empty"), 15, Color.WHITE));
            listBox.addView(c);
            return;
        }
        if (shown.isEmpty()) {
            LinearLayout c = card();
            c.addView(tv(t("no_match"), 15, GRAY));
            listBox.addView(c);
            return;
        }
        for (int oi : order) {
            final Waypoint w = shown.get(oi);
            LinearLayout rowCard = card();
            rowCard.setOrientation(LinearLayout.HORIZONTAL);
            rowCard.setGravity(Gravity.CENTER_VERTICAL);
            TextView em = tv(Cats.EMOJI[w.cat], 26, Color.WHITE);
            em.setPadding(0, 0, dp(10), 0);
            rowCard.addView(em);
            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            TextView nm = tv((w.fav ? "\u2605 " : "") + w.name, 16, Color.WHITE);
            nm.setTypeface(Typeface.DEFAULT_BOLD);
            mid.addView(nm);
            mid.addView(tv(t("c_" + Cats.KEY[w.cat]), 12, Cats.COLOR[w.cat]));
            if (hasFix) mid.addView(tv(fmt.dist(dist[oi]) + "  \u00B7  " + fmt.bearing(brg[oi]), 13, GRAY));
            rowCard.addView(mid, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView go = tv("\u27A4", 22, GREEN);
            go.setPadding(dp(14), dp(6), dp(6), dp(6));
            go.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setTarget(w.id, true); } });
            rowCard.addView(go);
            rowCard.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openDetail(w.id); } });
            listBox.addView(rowCard);
        }
    }

    private void openDetail(final long id) {
        final Waypoint w = store.get(id);
        if (w == null) { closeOverlay(); return; }
        overlay = 3;
        showTab();
        detailBox.removeAllViews();

        TextView back = tv("\u2039  " + t("back"), 16, BLUE);
        back.setPadding(dp(4), dp(8), dp(14), dp(8));
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { closeOverlay(); refreshList(); } });
        detailBox.addView(back);
        TextView name = tv((w.fav ? "\u2605 " : "") + w.name, 22, Color.WHITE);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        detailBox.addView(name);
        TextView cat = tv(fmt.catName(w.cat), 14, Cats.COLOR[w.cat]);
        cat.setPadding(0, 0, 0, dp(8));
        detailBox.addView(cat);

        if (hasFix) {
            LinearLayout c = card();
            double d = Geo.distance(fLat, fLon, w.lat, w.lon), b = Geo.bearing(fLat, fLon, w.lat, w.lon);
            c.addView(tv(t("from_you") + ":   " + fmt.dist(d) + "   \u00B7   " + fmt.bearing(b), 16, GREEN));
            if (!Double.isNaN(w.alt) && !Double.isNaN(fAlt)) {
                double dz = w.alt - fAlt;
                c.addView(tv(fmt.alt(Math.abs(dz)) + " " + t(dz >= 0 ? "higher" : "lower"), 13, GRAY));
            }
            detailBox.addView(c);
        }

        LinearLayout cc = card();
        TextView ct = tv(t("coords"), 13, AMBER);
        ct.setTypeface(Typeface.DEFAULT_BOLD);
        cc.addView(ct);
        int[] fm = {Geo.FMT_DD, Geo.FMT_DDM, Geo.FMT_DMS, Geo.FMT_UTM};
        for (int f : fm) {
            final String s = Geo.coord(w.lat, w.lon, f);
            TextView line = tv(s, 14, Color.WHITE);
            line.setPadding(0, dp(7), 0, dp(7));
            line.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { copy(s); } });
            cc.addView(line);
        }
        final String plain = Geo.plain(w.lat, w.lon);
        TextView pl = tv(plain, 14, Color.WHITE);
        pl.setPadding(0, dp(7), 0, dp(7));
        pl.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { copy(plain); } });
        cc.addView(pl);
        cc.addView(tv(t("tap_copy"), 11, GRAY));
        detailBox.addView(cc);

        LinearLayout ic = card();
        if (!Double.isNaN(w.alt)) ic.addView(tv(t("elev") + ":  " + fmt.alt(w.alt), 14, Color.WHITE));
        ic.addView(tv(t("saved_on") + ":  " + fmt.date(w.time), 14, Color.WHITE));
        if (w.notes.length() > 0) {
            TextView nt = tv(t("notes") + ":  " + w.notes, 14, Color.WHITE);
            nt.setPadding(0, dp(4), 0, 0);
            ic.addView(nt);
        }
        detailBox.addView(ic);

        LinearLayout r1 = new LinearLayout(this), r2 = new LinearLayout(this), r3 = new LinearLayout(this);
        TextView nav = button(t("navigate"), GREEN), edit = button(t("edit"), BLUE);
        TextView share = button(t("share"), BLUE), maps = button(t("open_maps"), BLUE);
        TextView upd = button(t("update_pos"), AMBER), del = button(t("delete"), RED);
        nav.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setTarget(w.id, true); } });
        edit.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openEdit(w); } });
        share.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { share(w); } });
        maps.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openMaps(w); } });
        upd.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!hasFix) { toast(t("gps_wait")); return; }
                Waypoint x = store.get(w.id);
                x.lat = fLat; x.lon = fLon; x.alt = fAlt;
                store.update(x);
                toast(t("moved_toast"));
                openDetail(w.id);
            }
        });
        del.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dialog().setTitle(t("delete_q")).setMessage(t("delete_msg"))
                        .setPositiveButton(t("delete"), new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int which) {
                                store.remove(w.id);
                                if (targetId == w.id) setTargetNone();
                                closeOverlay();
                                refreshList();
                            }
                        }).setNegativeButton(t("cancel"), null).show();
            }
        });
        r1.addView(nav, weight(0, dp(4)));
        r1.addView(edit, weight(dp(4), 0));
        r2.addView(share, weight(0, dp(4)));
        r2.addView(maps, weight(dp(4), 0));
        r3.addView(upd, weight(0, dp(4)));
        r3.addView(del, weight(dp(4), 0));
        for (LinearLayout r : new LinearLayout[]{r1, r2, r3}) {
            LinearLayout.LayoutParams l = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0);
            l.bottomMargin = dp(8);
            detailBox.addView(r, l);
        }
    }

    private void share(Waypoint w) {
        String text = w.name + "\n" + Geo.plain(w.lat, w.lon) + "\nhttps://maps.google.com/?q=" + String.format(Locale.US, "%.6f,%.6f", w.lat, w.lon);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, w.name);
        i.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(i, t("share")));
    }

    private void openMaps(Waypoint w) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Locale.US, "geo:%.6f,%.6f?q=%.6f,%.6f(%s)", w.lat, w.lon, w.lat, w.lon, Uri.encode(w.name)))));
        } catch (Exception e) {
            toast("No maps app found");
        }
    }

    private void openEdit(Waypoint w) {
        avgActive = false;
        editing = w;
        overlay = 4;
        showTab();
        editBox.removeAllViews();
        final boolean isNew = w == null;
        editCat = isNew ? 0 : w.cat;
        editFav = !isNew && w.fav;

        TextView back = tv("\u2039  " + t("back"), 16, BLUE);
        back.setPadding(dp(4), dp(8), dp(14), dp(8));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (editing != null) openDetail(editing.id); else closeOverlay();
            }
        });
        editBox.addView(back);
        TextView title = tv(t(isNew ? "new_wp" : "edit_wp"), 20, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        editBox.addView(title);

        editBox.addView(label(t("name")));
        nameEt = input(t("name"), isNew ? t("wp_default") + " " + (store.count() + 1) : w.name);
        editBox.addView(nameEt);

        editBox.addView(label(t("category")));
        final LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        editBox.addView(grid);
        drawCatGrid(grid);

        TextView fav = chip(t("favourite"), editFav, null);
        fav.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                editFav = !editFav;
                TextView c = (TextView) v;
                c.setTextColor(editFav ? Color.WHITE : GRAY);
                c.setBackground(box(editFav ? 0xFF1C1C3A : CARD, editFav ? AMBER : EDGE, 10));
            }
        });
        if (editFav) fav.setBackground(box(0xFF1C1C3A, AMBER, 10));
        LinearLayout.LayoutParams fl = lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0);
        fl.topMargin = dp(8);
        editBox.addView(fav, fl);

        editBox.addView(label(t("lat")));
        latEt = input(t("coord_hint"), isNew ? (hasFix ? String.format(Locale.US, "%.6f", fLat) : "") : String.format(Locale.US, "%.6f", w.lat));
        latEt.setInputType(InputType.TYPE_CLASS_TEXT);
        editBox.addView(latEt);
        editBox.addView(label(t("lon")));
        lonEt = input(t("coord_hint"), isNew ? (hasFix ? String.format(Locale.US, "%.6f", fLon) : "") : String.format(Locale.US, "%.6f", w.lon));
        lonEt.setInputType(InputType.TYPE_CLASS_TEXT);
        editBox.addView(lonEt);
        editBox.addView(label(t("alt")));
        altEt = input(t("alt"), isNew ? (hasFix && !Double.isNaN(fAlt) ? String.format(Locale.US, "%.1f", fAlt) : "") : (Double.isNaN(w.alt) ? "" : String.format(Locale.US, "%.1f", w.alt)));
        altEt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        editBox.addView(altEt);

        TextView use = button(t("use_current"), GREEN), avg = button(t("average"), GREEN), proj = button(t("project"), BLUE);
        use.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!hasFix) { toast(t("gps_wait")); return; }
                setFields(fLat, fLon, fAlt);
            }
        });
        avg.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { startAverage(); } });
        proj.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { projectDialog(); } });
        for (TextView b : new TextView[]{use, avg}) {
            LinearLayout.LayoutParams l = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0);
            l.topMargin = dp(8);
            editBox.addView(b, l);
        }
        avgStatus = tv("", 13, GREEN);
        avgStatus.setPadding(dp(2), dp(4), 0, 0);
        editBox.addView(avgStatus);
        LinearLayout.LayoutParams pl = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0);
        pl.topMargin = dp(8);
        editBox.addView(proj, pl);

        editBox.addView(label(t("notes")));
        notesEt = input(t("notes_hint"), isNew ? "" : w.notes);
        notesEt.setSingleLine(false);
        notesEt.setMinLines(3);
        notesEt.setGravity(Gravity.TOP);
        notesEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editBox.addView(notesEt);

        LinearLayout row = new LinearLayout(this);
        TextView save = button(t("save"), GREEN), cancel = button(t("cancel"), RED);
        save.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { saveEdit(); } });
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (editing != null) openDetail(editing.id); else closeOverlay(); }
        });
        row.addView(save, weight(0, dp(4)));
        row.addView(cancel, weight(dp(4), 0));
        LinearLayout.LayoutParams rl = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0);
        rl.topMargin = dp(14);
        editBox.addView(row, rl);
    }

    private void drawCatGrid(final LinearLayout grid) {
        grid.removeAllViews();
        LinearLayout row = null;
        for (int c = 0; c < Cats.COUNT; c++) {
            if (c % 5 == 0) {
                row = new LinearLayout(this);
                LinearLayout.LayoutParams l = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0);
                l.bottomMargin = dp(6);
                grid.addView(row, l);
            }
            final int ci = c;
            TextView cell = tv(Cats.EMOJI[c], 24, Color.WHITE);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(0, dp(8), 0, dp(8));
            cell.setBackground(box(editCat == c ? 0xFF1C1C3A : CARD, editCat == c ? Cats.COLOR[c] : EDGE, 10));
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { editCat = ci; drawCatGrid(grid); }
            });
            row.addView(cell, weight(dp(2), dp(2)));
        }
        if (row != null) {
            for (int pad = Cats.COUNT % 5; pad != 0 && pad < 5; pad++) {
                View spacer = new View(this);
                row.addView(spacer, weight(dp(2), dp(2)));
            }
        }
        TextView cur = tv(t("c_" + Cats.KEY[editCat]), 13, Cats.COLOR[editCat]);
        cur.setPadding(dp(2), 0, 0, 0);
        grid.addView(cur);
    }

    private void setFields(double la, double lo, double alt) {
        latEt.setText(String.format(Locale.US, "%.6f", la));
        lonEt.setText(String.format(Locale.US, "%.6f", lo));
        altEt.setText(Double.isNaN(alt) ? "" : String.format(Locale.US, "%.1f", alt));
    }

    private void saveEdit() {
        String nm = nameEt.getText().toString().trim();
        if (nm.length() == 0) { toast(t("name_required")); return; }
        double la, lo, alt = Double.NaN;
        try {
            la = Geo.parse(latEt.getText().toString(), true);
            lo = Geo.parse(lonEt.getText().toString(), false);
        } catch (NumberFormatException e) {
            toast(t("bad_coord"));
            return;
        }
        String as = altEt.getText().toString().trim().replace(',', '.');
        if (as.length() > 0) {
            try { alt = Double.parseDouble(as); } catch (NumberFormatException e) { toast(t("bad_value")); return; }
        }
        Waypoint w = editing != null ? store.get(editing.id) : new Waypoint();
        w.name = nm;
        w.notes = notesEt.getText().toString().trim();
        w.lat = la;
        w.lon = lo;
        w.alt = alt;
        w.cat = editCat;
        w.fav = editFav;
        avgActive = false;
        if (editing != null) {
            store.update(w);
            toast(t("saved_toast"));
            openDetail(w.id);
        } else {
            w.time = System.currentTimeMillis();
            store.add(w);
            toast(t("saved_toast"));
            closeOverlay();
            refreshList();
        }
    }

    private void startAverage() {
        if (!hasFix) { toast(t("gps_wait")); return; }
        avgActive = true;
        avgSec = 0;
        avgN = 0;
        sumW = sumLat = sumLon = sumAlt = sumAltW = 0;
        avgAdd();
        handler.postDelayed(avgTick, 1000);
    }

    private void avgAdd() {
        double w = 1.0 / Math.max(1.0, fAcc * fAcc);
        sumW += w;
        sumLat += fLat * w;
        sumLon += fLon * w;
        if (!Double.isNaN(fAlt)) { sumAlt += fAlt * w; sumAltW += w; }
        avgN++;
        if (avgStatus != null) avgStatus.setText(t("averaging") + ": " + avgSec + " / 10 s   (" + avgN + " " + t("fixes") + ", \u00B1" + Math.round(fAcc) + " " + t("m") + ")");
    }

    private final Runnable avgTick = new Runnable() {
        @Override public void run() {
            if (!avgActive) return;
            avgSec++;
            if (avgSec >= 10) {
                avgActive = false;
                if (sumW > 0 && latEt != null) {
                    setFields(sumLat / sumW, sumLon / sumW, sumAltW > 0 ? sumAlt / sumAltW : Double.NaN);
                    if (avgStatus != null) avgStatus.setText("\u2714 " + avgN + " " + t("fixes"));
                }
                return;
            }
            if (avgStatus != null) avgStatus.setText(t("averaging") + ": " + avgSec + " / 10 s   (" + avgN + " " + t("fixes") + ")");
            handler.postDelayed(this, 1000);
        }
    };

    private void projectDialog() {
        final boolean[] fromHere = {hasFix};
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        TextView lab = tv(t("proj_from"), 13, 0xFF555555);
        box.addView(lab);
        final TextView c1 = new TextView(this), c2 = new TextView(this);
        final Runnable style = new Runnable() {
            @Override public void run() {
                c1.setTextColor(fromHere[0] ? Color.WHITE : 0xFF333333);
                c1.setBackground(box(fromHere[0] ? 0xFF2E7D32 : 0xFFE0E0E0, 0xFFBBBBBB, 10));
                c2.setTextColor(!fromHere[0] ? Color.WHITE : 0xFF333333);
                c2.setBackground(box(!fromHere[0] ? 0xFF2E7D32 : 0xFFE0E0E0, 0xFFBBBBBB, 10));
            }
        };
        for (TextView c : new TextView[]{c1, c2}) { c.setTextSize(14); c.setGravity(Gravity.CENTER); c.setPadding(dp(8), dp(10), dp(8), dp(10)); }
        c1.setText(t("proj_here"));
        c2.setText(t("proj_entered"));
        c1.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { fromHere[0] = true; style.run(); } });
        c2.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { fromHere[0] = false; style.run(); } });
        LinearLayout cr = new LinearLayout(this);
        cr.addView(c1, weight(0, dp(4)));
        cr.addView(c2, weight(dp(4), 0));
        box.addView(cr);
        style.run();
        TextView l2 = tv(t("bearing_deg"), 13, 0xFF555555);
        l2.setPadding(0, dp(10), 0, 0);
        box.addView(l2);
        final EditText be = input("0\u2013360", "");
        be.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(be);
        TextView l3 = tv(t(fmt.imperial ? "dist_ft" : "dist_m"), 13, 0xFF555555);
        l3.setPadding(0, dp(10), 0, 0);
        box.addView(l3);
        final EditText de = input("", "");
        de.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(de);
        dialog().setTitle(t("project_title")).setView(box)
                .setPositiveButton(t("apply"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        try {
                            double b = Double.parseDouble(be.getText().toString().trim().replace(',', '.'));
                            double dist = Double.parseDouble(de.getText().toString().trim().replace(',', '.'));
                            if (fmt.imperial) dist = dist / 3.28084;
                            if (dist < 0) throw new NumberFormatException();
                            double sLat, sLon;
                            if (fromHere[0]) {
                                if (!hasFix) { toast(t("gps_wait")); return; }
                                sLat = fLat; sLon = fLon;
                            } else {
                                sLat = Geo.parse(latEt.getText().toString(), true);
                                sLon = Geo.parse(lonEt.getText().toString(), false);
                            }
                            double[] r = Geo.destination(sLat, sLon, b, dist);
                            setFields(r[0], r[1], Double.NaN);
                        } catch (NumberFormatException e) {
                            toast(t("bad_value"));
                        }
                    }
                }).setNegativeButton(t("cancel"), null).show();
    }

    private View buildCompassPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = card();
        tgtNameTv = tv("", 17, Color.WHITE);
        tgtNameTv.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(tgtNameTv);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(6), 0, 0);
        tgtBtnChoose = chip(t("choose"), false, new View.OnClickListener() { @Override public void onClick(View v) { chooseTarget(); } });
        tgtBtnStop = chip(t("stop_nav"), false, new View.OnClickListener() { @Override public void onClick(View v) { setTargetNone(); updateCompass(); } });
        othersChip = chip(t("show_others"), showOthers, new View.OnClickListener() {
            @Override public void onClick(View v) {
                showOthers = !showOthers;
                prefs.edit().putBoolean("others", showOthers).apply();
                styleOthersChip();
                updateCompass();
            }
        });
        row.addView(tgtBtnChoose, chipLp());
        row.addView(tgtBtnStop, chipLp());
        row.addView(othersChip, chipLp());
        top.addView(row);
        page.addView(top);

        compass = new CompassView(this);
        compass.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        page.addView(compass, lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        distTv = tv("--", 38, Color.WHITE);
        distTv.setGravity(Gravity.CENTER);
        distTv.setTypeface(Typeface.DEFAULT_BOLD);
        bearingTv = tv("", 16, BLUE);
        bearingTv.setGravity(Gravity.CENTER);
        hintTv = tv("", 15, GREEN);
        hintTv.setGravity(Gravity.CENTER);
        extraTv = tv("", 13, GRAY);
        extraTv.setGravity(Gravity.CENTER);
        noteTv = tv("", 12, AMBER);
        noteTv.setGravity(Gravity.CENTER);
        page.addView(distTv);
        page.addView(bearingTv);
        page.addView(hintTv);
        page.addView(extraTv);
        page.addView(noteTv);
        LinearLayout.LayoutParams sp = lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(28), 0);
        page.addView(new View(this), sp);       
        styleOthersChip();
        return page;
    }

    private void styleOthersChip() {
        othersChip.setTextColor(showOthers ? Color.WHITE : GRAY);
        othersChip.setBackground(box(showOthers ? 0xFF1C1C3A : CARD, showOthers ? GREEN : EDGE, 10));
    }

    private void setTarget(long id, boolean goCompass) {
        targetId = id;
        arrivedFlag = false;
        prefs.edit().putLong("target", id).apply();
        if (goCompass) {
            overlay = 0;
            tab = 1;
            prefs.edit().putInt("tab", 1).apply();
            showTab();
        }
    }

    private void setTargetNone() {
        targetId = -1;
        arrivedFlag = false;
        prefs.edit().putLong("target", -1).apply();
    }

    private void chooseTarget() {
        final List<Waypoint> all = store.all();
        if (all.isEmpty()) { toast(t("empty")); return; }
        if (hasFix) {
            Collections.sort(all, new Comparator<Waypoint>() {
                @Override public int compare(Waypoint a, Waypoint b) {
                    return Double.compare(Geo.distance(fLat, fLon, a.lat, a.lon), Geo.distance(fLat, fLon, b.lat, b.lon));
                }
            });
        }
        CharSequence[] items = new CharSequence[all.size()];
        for (int i = 0; i < items.length; i++) {
            Waypoint w = all.get(i);
            items[i] = Cats.EMOJI[w.cat] + "  " + w.name + (hasFix ? "   (" + fmt.dist(Geo.distance(fLat, fLon, w.lat, w.lon)) + ")" : "");
        }
        dialog().setTitle(t("choose")).setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) { setTarget(all.get(which).id, false); updateCompass(); }
        }).show();
    }

    private void pushCompass() {
        if (compass == null || tab != 1 || overlay != 0) return;
        double ht = headingTrue();
        if (Double.isNaN(ht)) return;
        double dial = trueNorth ? ht : (ht - declination + 360.0) % 360.0;
        compass.setHeading((float) dial);
        Waypoint tw = targetId >= 0 ? store.get(targetId) : null;
        if (tw != null && hasFix) {
            double b = Geo.bearing(fLat, fLon, tw.lat, tw.lon);
            compass.setTarget(true, (float) (((b - ht) + 360.0) % 360.0), Cats.EMOJI[tw.cat], Cats.COLOR[tw.cat], arrivedFlag);
        }
        if (showOthers && hasFix) updateOthers(ht);
    }

    private void updateOthers(double ht) {
        List<Waypoint> all = store.all();
        List<Float> rels = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();
        for (Waypoint w : all) {
            if (w.id == targetId) continue;
            rels.add((float) (((Geo.bearing(fLat, fLon, w.lat, w.lon) - ht) + 360.0) % 360.0));
            cols.add(Cats.COLOR[w.cat]);
            if (rels.size() >= 40) break;
        }
        float[] r = new float[rels.size()];
        int[] c = new int[rels.size()];
        for (int i = 0; i < r.length; i++) { r[i] = rels.get(i); c[i] = cols.get(i); }
        compass.setOthers(r, c);
    }

    private void updateCompass() {
        if (compass == null) return;
        double ht = headingTrue();
        Waypoint tw = targetId >= 0 ? store.get(targetId) : null;
        if (tw == null && targetId >= 0) setTargetNone();

        if (rot == null) noteTv.setText(t("no_sensor"));
        else noteTv.setText(haveHeading ? "" : t("calib"));

        if (!showOthers) compass.setOthers(new float[0], new int[0]);
        if (!Double.isNaN(ht)) {
            double dial = trueNorth ? ht : (ht - declination + 360.0) % 360.0;
            compass.setHeading((float) dial);
        }

        if (tw == null) {
            tgtNameTv.setText(t("no_target"));
            tgtBtnStop.setVisibility(View.GONE);
            compass.setTarget(false, 0, "", GREEN, false);
            distTv.setText("--");
            bearingTv.setText(Double.isNaN(ht) ? "" : t("heading") + ":  " + fmt.bearing(trueNorth ? ht : (ht - declination + 360.0) % 360.0));
            hintTv.setText("");
            extraTv.setText(hasFix ? "" : t("gps_wait"));
            if (showOthers && hasFix && !Double.isNaN(ht)) updateOthers(ht);
            return;
        }
        tgtNameTv.setText(Cats.EMOJI[tw.cat] + "  " + tw.name);
        tgtBtnStop.setVisibility(View.VISIBLE);
        if (!hasFix) {
            compass.setTarget(false, 0, "", GREEN, false);
            distTv.setText("--");
            bearingTv.setText("");
            hintTv.setText("");
            extraTv.setText(t("gps_wait"));
            return;
        }
        double d = Geo.distance(fLat, fLon, tw.lat, tw.lon);
        double b = Geo.bearing(fLat, fLon, tw.lat, tw.lon);
        double shownB = trueNorth ? b : (b - declination + 360.0) % 360.0;

        int limit = ARRIVAL[Math.max(0, Math.min(4, arrivalIdx))];
        if (limit > 0) {
            if (d <= limit && !arrivedFlag) {
                arrivedFlag = true;
                toast(t("arrived"));
                try {
                    Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    if (v != null) v.vibrate(new long[]{0, 300, 150, 300, 150, 500}, -1);
                } catch (Exception e) { }
            } else if (d > limit * 1.6 && arrivedFlag) arrivedFlag = false;
        }

        distTv.setText(fmt.dist(d));
        bearingTv.setText(t("bearing_to") + ":  " + fmt.bearing(shownB) + "   \u00B7   " + t(trueNorth ? "true_n" : "mag_n"));
        if (arrivedFlag) { hintTv.setText("\u2714 " + t("arrived")); hintTv.setTextColor(0xFFFFD54F); }
        else if (!Double.isNaN(ht)) { hintTv.setText(fmt.turnHint(b - ht)); hintTv.setTextColor(GREEN); }
        else { hintTv.setText(""); }
        StringBuilder ex = new StringBuilder();
        if (!Double.isNaN(ht)) ex.append(t("heading")).append(": ").append(fmt.bearing(trueNorth ? ht : (ht - declination + 360.0) % 360.0));
        ex.append("   ").append(t("eta")).append(": ").append(fmt.eta(d, fSpd));
        if (!Double.isNaN(tw.alt) && !Double.isNaN(fAlt)) {
            double dz = tw.alt - fAlt;
            ex.append("\n").append(fmt.alt(Math.abs(dz))).append(" ").append(t(dz >= 0 ? "higher" : "lower"));
        }
        extraTv.setText(ex.toString());
        pushCompass();
        if (Double.isNaN(ht)) compass.setTarget(true, 0, Cats.EMOJI[tw.cat], Cats.COLOR[tw.cat], arrivedFlag);
    }

    private void put(String k, int v) { prefs.edit().putInt(k, v).apply(); recreate(); }
    private void put(String k, boolean v) { prefs.edit().putBoolean(k, v).apply(); recreate(); }

    private View buildSettings() {
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(2), dp(2), dp(2), dp(48));
        sv.addView(box);

        box.addView(label(t("lang")));
        LinearLayout lr = new LinearLayout(this);
        String[] ln = {"English", "اردو", "العربية"};
        for (int i = 0; i < 3; i++) {
            final int li = i;
            lr.addView(chip(ln[i], fmt.lang == i, new View.OnClickListener() { @Override public void onClick(View v) { put("lang", li); } }), chipLp());
        }
        box.addView(lr);

        box.addView(label(t("units")));
        LinearLayout ur = new LinearLayout(this);
        ur.addView(chip(t("metric"), !fmt.imperial, new View.OnClickListener() { @Override public void onClick(View v) { put("imperial", false); } }), chipLp());
        ur.addView(chip(t("imperial"), fmt.imperial, new View.OnClickListener() { @Override public void onClick(View v) { put("imperial", true); } }), chipLp());
        box.addView(ur);

        box.addView(label(t("coord_fmt")));
        LinearLayout fr = new LinearLayout(this);
        String[] fk = {"f_dd", "f_ddm", "f_dms", "f_utm"};
        for (int i = 0; i < 4; i++) {
            final int fi = i;
            fr.addView(chip(t(fk[i]), fmt.coordFmt == i, new View.OnClickListener() { @Override public void onClick(View v) { put("coordFmt", fi); } }), chipLp());
        }
        HorizontalScrollView fh = new HorizontalScrollView(this);
        fh.setHorizontalScrollBarEnabled(false);
        fh.addView(fr);
        box.addView(fh);

        box.addView(label(t("north_ref")));
        LinearLayout nr = new LinearLayout(this);
        nr.addView(chip(t("true"), trueNorth, new View.OnClickListener() { @Override public void onClick(View v) { put("trueNorth", true); } }), chipLp());
        nr.addView(chip(t("magnetic"), !trueNorth, new View.OnClickListener() { @Override public void onClick(View v) { put("trueNorth", false); } }), chipLp());
        box.addView(nr);

        box.addView(label(t("arrival")));
        LinearLayout ar = new LinearLayout(this);
        for (int i = 0; i < ARRIVAL.length; i++) {
            final int ai = i;
            ar.addView(chip(ARRIVAL[i] == 0 ? t("off") : fmt.dist(ARRIVAL[i]), arrivalIdx == i, new View.OnClickListener() { @Override public void onClick(View v) { put("arrival", ai); } }), chipLp());
        }
        HorizontalScrollView ah = new HorizontalScrollView(this);
        ah.setHorizontalScrollBarEnabled(false);
        ah.addView(ar);
        box.addView(ah);

        box.addView(label(t("keep_on")));
        LinearLayout kr = new LinearLayout(this);
        kr.addView(chip(t("on"), keepOn, new View.OnClickListener() { @Override public void numpy(View v) { } @Override public void onClick(View v) { put("keepOn", true); } }), chipLp());
        kr.addView(chip(t("off"), !keepOn, new View.OnClickListener() { @Override public void onClick(View v) { put("keepOn", false); } }), chipLp());
        box.addView(kr);

        box.addView(label(t("data")));
        TextView eg = button(t("export_gpx"), BLUE), ec = button(t("export_csv"), BLUE), im = button(t("import_gpx"), GREEN), da = button(t("delete_all"), RED);
        eg.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { startExport(REQ_GPX); } });
        ec.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { startExport(REQ_CSV); } });
        im.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try { startActivityForResult(i, REQ_IMPORT); } catch (Exception e) { toast(t("file_fail")); }
            }
        });
        da.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dialog().setTitle(t("delete_all_q")).setMessage(t("delete_msg"))
                        .setPositiveButton(t("delete"), new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int which) {
                                store.clear();
                                setTargetNone();
                                refreshList();
                            }
                        }).setNegativeButton(t("cancel"), null).show();
            }
        });
        for (TextView b : new TextView[]{eg, ec, im, da}) {
            LinearLayout.LayoutParams l = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0);
            l.topMargin = dp(6);
            box.addView(b, l);
        }
        TextView about = tv(t("about"), 12, GRAY);
        about.setPadding(dp(4), dp(16), dp(4), 0);
        box.addView(about);
        TextView credit = tv("Brought to you By: Black Falcon \uD83E\uDD85", 13, GRAY);
        credit.setPadding(dp(4), dp(12), 0, 0);
        box.addView(credit);
        return sv;
    }

    private void startExport(int req) {
        if (store.count() == 0) { toast(t("nothing")); return; }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(req == REQ_GPX ? "application/gpx+xml" : "text/csv");
        i.putExtra(Intent.EXTRA_TITLE, req == REQ_GPX ? "waypoints.gpx" : "waypoints.csv");
        try { startActivityForResult(i, req); } catch (Exception e) { toast(t("file_fail")); }
    }

    @Override
    protected void onActivityResult(final int req, int res, final Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        io.execute(new Runnable() {
            @Override public void run() {
                String msg;
                try {
                    if (req == REQ_IMPORT) {
                        InputStream in = getContentResolver().openInputStream(uri);
                        int n = store.importGpx(in);
                        in.close();
                        msg = n + " " + t("imported");
                    } else if (req == REQ_GPX || req == REQ_CSV) {
                        OutputStream os = getContentResolver().openOutputStream(uri);
                        if (req == REQ_GPX) WaypointStore.exportGpx(store.all(), os); else WaypointStore.exportCsv(store.all(), os);
                        os.close();
                        msg = t("file_saved");
                    } else return;
                } catch (Exception e) {
                    msg = t("file_fail");
                }
                final String m = msg;
                handler.post(new Runnable() {
                    @Override public void run() {
                        toast(m);
                        refreshList();
                    }
                });
            }
        });
    }
}
