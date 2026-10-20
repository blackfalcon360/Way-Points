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

    private static final int GREEN = 0xFF2ECC71, GRAY = 0xFF9E9E9E, AMBER = 0xFFFFB300, BLUE = 0xFF4FC3F7, RED = 0xFFE57373;
    private static final int REQ_PERM = 1, REQ_GPX = 2, REQ_CSV = 3, REQ_IMPORT = 4;
    private static final int[] ARRIVAL = {0, 10, 25, 50, 100};

    // Dynamic Theme Colors
    private boolean isDarkMode = true;
    private int bgColor = Color.BLACK;
    private int cardColor = 0xFF0C0C1E;
    private int edgeColor = 0xFF3A3A60;
    private int textColor = Color.WHITE;
    private int btnBgColor = 0xFF1C1C3A;

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
    private FrameLayout rootLayout;
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

        isDarkMode = prefs.getBoolean("darkMode", true);
        updateThemeColors();

        fmt.lang = prefs.getInt("lang", Lang.EN);                 // English unless you pick another language
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
    }

    private void updateThemeColors() {
        if (isDarkMode) {
            bgColor = Color.BLACK;
            cardColor = 0xFF0C0C1E;
            edgeColor = 0xFF3A3A60;
            textColor = Color.WHITE;
            btnBgColor = 0xFF1C1C3A;
        } else {
            bgColor = 0xFFF5F5F5;
            cardColor = Color.WHITE;
            edgeColor = 0xFFCCCCCC;
            textColor = Color.BLACK;
            btnBgColor = 0xFFE0E0E0;
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
        super.onBackPressed();
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
        } catch (SecurityException | IllegalArgumentException e) { /* the status line explains */ }
    }

    private void onFix(Location l) {
        boolean gps = LocationManager.GPS_PROVIDER.equals(l.getProvider());
        long now = System.currentTimeMillis();
        if (!gps && hasFix && now - lastGpsMs < 20000) return;       // the network position is only a fallback
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
        if (Math.abs(rotMat[8]) < 0.7f) az = Math.atan2(-rotMat[2], -rotMat[5]);     // phone held upright: the way the camera points
        else az = Math.atan2(rotMat[1], rotMat[4]);                                   // phone flat: the way the top edge points
        if (firstSample) { sinAvg = (float) Math.sin(az); cosAvg = (float) Math.cos(az); firstSample = false; }
        else { sinAvg += 0.18f * ((float) Math.sin(az) - sinAvg); cosAvg += 0.18f * ((float) Math.cos(az) - cosAvg); }
        magHeading = (float) ((Math.toDegrees(Math.atan2(sinAvg, cosAvg)) + 360.0) % 360.0);
        haveHeading = true;
        pushCompass();
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    /** true-north heading of the top of the phone, or NaN if unknown */
    private double headingTrue() {
        if (rot != null && haveHeading) return (magHeading + declination + 360.0) % 360.0;
        if (hasFix && fSpd > 1.0f) return fBrg;
        return Double.NaN;
    }

    // ------------------------------------------------------------- helpers
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
        c.setBackground(box(cardColor, edgeColor, 14));
        c.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams l = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0);
        l.bottomMargin = dp(8);
        c.setLayoutParams(l);
        return c;
    }

    private TextView button(String text, int edge) {
        TextView b = tv(text, 15, textColor);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(box(btnBgColor, edge, 12));
        b.setPadding(dp(10), dp(12), dp(10), dp(12));
        return b;
    }

    private TextView chip(String text, boolean selected, View.OnClickListener l) {
        TextView c = tv(text, 14, selected ? textColor : GRAY);
        c.setGravity(Gravity.CENTER);
        c.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        c.setBackground(box(selected ? btnBgColor : cardColor, selected ? GREEN : edgeColor, 10));
        c.setPadding(dp(12), dp(9), dp(12), dp(9));
        c.setOnClickListener(l);
        return c;
    }

    /** A text box whose typed text is BLACK (on a white box). */
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
        return new AlertDialog.Builder(new ContextThemeWrapper(this, isDarkMode ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert));
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void copy(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("waypoint", text));
        toast(t("copied"));
    }

    // ------------------------------------------------------------- UI shell
    private void buildUi() {
        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(bgColor);
        rootLayout.setLayoutDirection(fmt.lang == Lang.EN ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(8), dp(6), dp(8), dp(4));

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setPadding(0, 0, 0, dp(8));
        String[] keys = {"tab_list", "tab_compass", "tab_set"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView x = tv(t(keys[i]), 14, textColor);
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
        rootLayout.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView credit = tv("By: Black Falcon \uD83E\uDD85", 12, GRAY);
        credit.setPadding(dp(8), dp(3), dp(8), dp(3));
        credit.setBackground(box(isDarkMode ? 0xCC000000 : 0xCCFFFFFF, edgeColor, 8));
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT);
        cl.setMargins(0, 0, dp(8), dp(6));
        rootLayout.addView(credit, cl);

        setContentView(rootLayout);
        showTab();
        refreshList();
        updateGpsLines();
    }

    private void showTab() {
        for (int i = 0; i < 3; i++) {
            pages[i].setVisibility(overlay == 0 && i == tab ? View.VISIBLE : View.GONE);
            tabs[i].setBackground(box(i == tab ? btnBgColor : cardColor, i == tab ? BLUE : edgeColor, 10));
            tabs[i].setTypeface(i == tab ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tabs[i].setTextColor(textColor);
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

    // ------------------------------------------------------------- waypoint list
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

        // sort chips
        sortBox.removeAllViews();
        String[] sk = {"s_near", "s_new", "s_name", "s_cat"};
        for (int i = 0; i < 4; i++) {
            final int si = i;
            sortBox.addView(chip((i == 0 ? "\u21C5 " : "") + t(sk[i]), sortMode == i, new View.OnClickListener() {
                @Override public void onClick(View v) { sortMode = si; prefs.edit().putInt("sort", si).apply(); refreshList(); }
            }), chipLp());
        }
        // category chips
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

        // filter
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
            c.addView(tv(t("empty"), 15, textColor));
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
            TextView em = tv(Cats.EMOJI[w.cat], 26, textColor);
            em.setPadding(0, 0, dp(10), 0);
            rowCard.addView(em);
            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            TextView nm = tv((w.fav ? "\u2605 " : "") + w.name, 16, textColor);
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

    // ------------------------------------------------------------- detail
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
        TextView name = tv((w.fav ? "\u2605 " : "") + w.name, 22, textColor);
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
            TextView line = tv(s, 14, textColor);
            line.setPadding(0, dp(7), 0, dp(7));
            line.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { copy(s); } });
            cc.addView(line);
        }
        final String plain = Geo.plain(w.lat, w.lon);
        TextView pl = tv(plain, 14, textColor);
        pl.setPadding(0, dp(7), 0, dp(7));
        pl.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { copy(plain); } });
        cc.addView(pl);
        cc.addView(tv(t("tap_copy"), 11, GRAY));
        detailBox.addView(cc);

        LinearLayout ic = card();
        if (!Double.isNaN(w.alt)) ic.addView(tv(t("elev") + ":  " + fmt.alt(w.alt), 14, textColor));
        ic.addView(tv(t("saved_on") + ":  " + fmt.date(w.time), 14, textColor));
        if (w.notes.length() > 0) {
            TextView nt = tv(t("notes") + ":  " + w.notes, 14, textColor);
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

    // ------------------------------------------------------------- add / edit
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
        TextView title = tv(t(isNew ? "new_wp" : "edit_wp"), 20, textColor);
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
                c.setTextColor(editFav ? textColor : GRAY);
                c.setBackground(box(editFav ? btnBgColor : cardColor, editFav ? AMBER : edgeColor, 10));
            }
        });
        if (editFav) fav.setBackground(box(btnBgColor, AMBER, 10));
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
            TextView cell = tv(Cats.EMOJI[c], 24, textColor);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(0, dp(8), 0, dp(8));
            cell.setBackground(box(editCat == c ? btnBgColor : cardColor, editCat == c ? Cats.COLOR[c] : edgeColor, 10));
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

    // ---- averaging
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

    // ---- project a point
    private void projectDialog() {
        final boolean[] fromHere = {hasFix};
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        TextView lab = tv(t("proj_from"), 13, 0xFF555555);
        box.addView(
