package com.blackfalcon.waypoints;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Distances, bearings, coordinate formats (decimal, degrees-minutes, DMS, UTM) and coordinate parsing. */
public final class Geo {
    private Geo() { }

    public static final double R = 6371008.8;
    public static final int FMT_DD = 0, FMT_DDM = 1, FMT_DMS = 2, FMT_UTM = 3;

    private static double rad(double d) { return Math.toRadians(d); }
    public static double norm360(double a) { a %= 360.0; return a < 0 ? a + 360.0 : a; }

    public static double distance(double la1, double lo1, double la2, double lo2) {
        double p1 = rad(la1), p2 = rad(la2), dp = p2 - p1, dl = rad(lo2 - lo1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * R * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /** initial bearing from point 1 to point 2, degrees clockwise from true north */
    public static double bearing(double la1, double lo1, double la2, double lo2) {
        double p1 = rad(la1), p2 = rad(la2), dl = rad(lo2 - lo1);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        return norm360(Math.toDegrees(Math.atan2(y, x)));
    }

    /** the point reached from (lat, lon) by travelling distM metres on a bearing */
    public static double[] destination(double lat, double lon, double bearingDeg, double distM) {
        double d = distM / R, b = rad(bearingDeg), p1 = rad(lat), l1 = rad(lon);
        double p2 = Math.asin(Math.sin(p1) * Math.cos(d) + Math.cos(p1) * Math.sin(d) * Math.cos(b));
        double l2 = l1 + Math.atan2(Math.sin(b) * Math.sin(d) * Math.cos(p1), Math.cos(d) - Math.sin(p1) * Math.sin(p2));
        return new double[]{Math.toDegrees(p2), ((Math.toDegrees(l2) + 540.0) % 360.0) - 180.0};
    }

    // ------------------------------------------------------------------ formatting
    private static String hemi(double v, boolean lat) { return lat ? (v >= 0 ? "N" : "S") : (v >= 0 ? "E" : "W"); }

    public static String dd(double v, boolean lat) {
        return String.format(Locale.US, "%.6f\u00B0 %s", Math.abs(v), hemi(v, lat));
    }

    public static String ddm(double v, boolean lat) {
        double a = Math.abs(v);
        int d = (int) a;
        double m = (a - d) * 60.0;
        if (m >= 59.99995) { d++; m = 0; }
        return String.format(Locale.US, "%d\u00B0 %06.3f' %s", d, m, hemi(v, lat));
    }

    public static String dms(double v, boolean lat) {
        double a = Math.abs(v);
        int d = (int) a;
        double rest = (a - d) * 60.0;
        int m = (int) rest;
        double s = (rest - m) * 60.0;
        if (s >= 59.995) { s = 0; m++; }
        if (m >= 60) { m = 0; d++; }
        return String.format(Locale.US, "%d\u00B0 %02d' %05.2f\" %s", d, m, s, hemi(v, lat));
    }

    public static String coord(double lat, double lon, int fmt) {
        switch (fmt) {
            case FMT_DDM: return ddm(lat, true) + ",  " + ddm(lon, false);
            case FMT_DMS: return dms(lat, true) + ",  " + dms(lon, false);
            case FMT_UTM: {
                Utm u = toUtm(lat, lon);
                return u == null ? "UTM n/a" : u.toString();
            }
            default: return dd(lat, true) + ",  " + dd(lon, false);
        }
    }

    /** plain "lat, lon" decimal text for sharing / maps */
    public static String plain(double lat, double lon) { return String.format(Locale.US, "%.6f, %.6f", lat, lon); }

    // ------------------------------------------------------------------ UTM (WGS84, Krueger series)
    public static final class Utm {
        public int zone;
        public char band;
        public double easting, northing;
        @Override public String toString() {
            return String.format(Locale.US, "%d%c  %d E  %d N", zone, band, Math.round(easting), Math.round(northing));
        }
    }

    public static Utm toUtm(double lat, double lon) {
        if (lat < -80 || lat > 84) return null;
        int zone = (int) Math.floor((lon + 180.0) / 6.0) + 1;
        if (zone > 60) zone = 60;
        if (zone < 1) zone = 1;
        if (lat >= 56 && lat < 64 && lon >= 3 && lon < 12) zone = 32;
        if (lat >= 72 && lat < 84) {
            if (lon >= 0 && lon < 9) zone = 31;
            else if (lon >= 9 && lon < 21) zone = 33;
            else if (lon >= 21 && lon < 33) zone = 35;
            else if (lon >= 33 && lon < 42) zone = 37;
        }
        double lon0 = Math.toRadians((zone - 1) * 6 - 180 + 3);
        double a = 6378137.0, f = 1 / 298.257223563, k0 = 0.9996;
        double n = f / (2 - f), n2 = n * n, n3 = n2 * n, n4 = n3 * n;
        double A = a / (1 + n) * (1 + n2 / 4 + n4 / 64);
        double[] al = {n / 2 - 2 * n2 / 3 + 5 * n3 / 16, 13 * n2 / 48 - 3 * n3 / 5, 61 * n3 / 240};
        double e = Math.sqrt(f * (2 - f));
        double phi = Math.toRadians(lat), dl = Math.toRadians(lon) - lon0;
        double sinp = Math.sin(phi);
        double t = Math.sinh(atanh(sinp) - e * atanh(e * sinp));
        double xi0 = Math.atan2(t, Math.cos(dl));
        double eta0 = atanh(Math.sin(dl) / Math.sqrt(1 + t * t));
        double xi = xi0, eta = eta0;
        for (int j = 1; j <= 3; j++) {
            xi += al[j - 1] * Math.sin(2 * j * xi0) * Math.cosh(2 * j * eta0);
            eta += al[j - 1] * Math.cos(2 * j * xi0) * Math.sinh(2 * j * eta0);
        }
        Utm u = new Utm();
        u.zone = zone;
        u.band = "CDEFGHJKLMNPQRSTUVWXX".charAt(Math.max(0, Math.min(20, (int) Math.floor((lat + 80.0) / 8.0))));
        u.easting = 500000.0 + k0 * A * eta;
        u.northing = k0 * A * xi + (lat < 0 ? 10000000.0 : 0.0);
        return u;
    }

    private static double atanh(double x) { return 0.5 * Math.log((1 + x) / (1 - x)); }

    // ------------------------------------------------------------------ parsing
    private static final Pattern NUM = Pattern.compile("\\d+(?:\\.\\d+)?");

    /**
     * Reads a latitude or longitude written in almost any style: 33.6844, -73.05, 33.6844 N, N 33 41.064,
     * 33°41'3.8"N, 33 41 3.8 S ... Throws NumberFormatException when it cannot be understood or is out of range.
     */
    public static double parse(String text, boolean isLat) {
        if (text == null) throw new NumberFormatException("empty");
        String s = text.trim().toUpperCase(Locale.US);
        if (s.length() == 0) throw new NumberFormatException("empty");
        boolean neg = false;
        if (s.indexOf('-') >= 0 || s.indexOf('\u2212') >= 0) neg = true;
        int hn = 0, hs = 0, he = 0, hw = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 'N') hn++; else if (c == 'S') hs++; else if (c == 'E') he++; else if (c == 'W') hw++;
        }
        if (isLat && (he > 0 || hw > 0)) throw new NumberFormatException("lat has E/W");
        if (!isLat && (hn > 0 || hs > 0)) throw new NumberFormatException("lon has N/S");
        if (hn + hs + he + hw > 1) throw new NumberFormatException("two hemispheres");
        if (hs > 0 || hw > 0) neg = true;
        if (s.indexOf(',') >= 0 && s.indexOf('.') < 0) s = s.replace(',', '.'); else s = s.replace(",", "");
        s = s.replaceAll("[NSEW\u00B0\u00BA\u02DA'\u2032\u2019\u00B4\"\u2033\u201D\u2212+-]", " ");
        Matcher m = NUM.matcher(s);
        double[] parts = new double[3];
        int k = 0;
        while (m.find()) {
            if (k >= 3) throw new NumberFormatException("too many numbers");
            parts[k++] = Double.parseDouble(m.group());
        }
        if (k == 0) throw new NumberFormatException("no number");
        if (k > 1 && parts[1] >= 60) throw new NumberFormatException("minutes");
        if (k > 2 && parts[2] >= 60) throw new NumberFormatException("seconds");
        double v = parts[0] + parts[1] / 60.0 + parts[2] / 3600.0;
        if (v > (isLat ? 90.0 : 180.0)) throw new NumberFormatException("range");
        return neg ? -v : v;
    }
}
