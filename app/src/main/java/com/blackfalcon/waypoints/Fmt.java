package com.blackfalcon.waypoints;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Numbers, directions and coordinates as text in the chosen language and units. */
public final class Fmt {
    public int lang = Lang.EN, coordFmt = Geo.FMT_DD;
    public boolean imperial = false;

    public String t(String k) { return Lang.t(lang, k); }

    public String dist(double m) {
        if (Double.isNaN(m)) return "--";
        if (imperial) {
            double mi = m / 1609.344;
            if (mi >= 100) return String.format(Locale.US, "%d %s", Math.round(mi), t("mi"));
            return mi >= 0.1 ? String.format(Locale.US, "%.2f %s", mi, t("mi")) : String.format(Locale.US, "%d %s", Math.round(m * 3.28084), t("ft"));
        }
        if (m >= 100000) return String.format(Locale.US, "%d %s", Math.round(m / 1000), t("km"));
        return m >= 1000 ? String.format(Locale.US, "%.2f %s", m / 1000.0, t("km")) : String.format(Locale.US, "%d %s", Math.round(m), t("m"));
    }

    public String alt(double m) {
        if (Double.isNaN(m)) return "--";
        return imperial ? String.format(Locale.US, "%d %s", Math.round(m * 3.28084), t("ft")) : String.format(Locale.US, "%d %s", Math.round(m), t("m"));
    }

    public String dir(double deg) {
        String[] d = Lang.dirs(lang);
        return d[(int) (Math.round(Geo.norm360(deg) / 45.0) % 8)];
    }

    public String bearing(double deg) {
        return String.format(Locale.US, "%d\u00B0 %s", Math.round(Geo.norm360(deg)) % 360, dir(deg));
    }

    public String coord(double lat, double lon) { return Geo.coord(lat, lon, coordFmt); }

    public String eta(double distM, double speedMs) {
        if (speedMs < 0.5 || Double.isNaN(distM)) return "--";
        long min = Math.round(distM / speedMs / 60.0);
        if (min < 1) return "< 1 " + t("min");
        if (min < 60) return min + " " + t("min");
        return (min / 60) + " " + t("h") + " " + String.format(Locale.US, "%02d", min % 60) + " " + t("min");
    }

    public String date(long ms) { return new SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.US).format(new Date(ms)); }

    public String catName(int cat) { return Cats.EMOJI[cat] + " " + t("c_" + Cats.KEY[cat]); }

    /** which way to turn, from the angle between where the phone points and where the target is */
    public String turnHint(double rel) {
        double r = ((rel + 540.0) % 360.0) - 180.0;
        if (Math.abs(r) <= 10) return t("ahead");
        if (Math.abs(r) >= 150) return t("behind");
        return r > 0 ? t("turn_right") : t("turn_left");
    }
}
