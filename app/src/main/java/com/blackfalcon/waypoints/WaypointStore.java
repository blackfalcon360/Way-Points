package com.blackfalcon.waypoints;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Waypoints kept in one small text file on the phone (tab-separated, safe for any language), plus GPX / CSV import and export. */
public final class WaypointStore {
    private final File file;
    private final List<Waypoint> list = new ArrayList<>();

    public WaypointStore(File file) {
        this.file = file;
        load();
    }

    // ------------------------------------------------------------------ access
    public synchronized List<Waypoint> all() {
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint w : list) out.add(w.copy());
        return out;
    }

    public synchronized Waypoint get(long id) {
        for (Waypoint w : list) if (w.id == id) return w.copy();
        return null;
    }

    public synchronized int count() { return list.size(); }

    private long newId() {
        long id = System.currentTimeMillis();
        boolean clash = true;
        while (clash) {
            clash = false;
            for (Waypoint w : list) if (w.id == id) { id++; clash = true; break; }
        }
        return id;
    }

    public synchronized long add(Waypoint w) {
        Waypoint c = w.copy();
        c.id = newId();
        if (c.time == 0) c.time = System.currentTimeMillis();
        list.add(c);
        save();
        return c.id;
    }

    public synchronized void update(Waypoint w) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == w.id) { list.set(i, w.copy()); save(); return; }
        }
    }

    public synchronized void remove(long id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == id) { list.remove(i); save(); return; }
        }
    }

    public synchronized void clear() {
        list.clear();
        save();
    }

    // ------------------------------------------------------------------ file
    private static String esc(String s) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') b.append("\\\\");
            else if (c == '\t') b.append("\\t");
            else if (c == '\n') b.append("\\n");
            else if (c == '\r') { /* dropped */ }
            else b.append(c);
        }
        return b.toString();
    }

    private static String unesc(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char d = s.charAt(++i);
                b.append(d == 't' ? '\t' : (d == 'n' ? '\n' : d));
            } else b.append(c);
        }
        return b.toString();
    }

    private void load() {
        list.clear();
        if (!file.exists()) return;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                String[] s = line.split("\t", -1);
                if (s.length < 9) continue;
                try {
                    Waypoint w = new Waypoint();
                    w.id = Long.parseLong(s[0]);
                    w.name = unesc(s[1]);
                    w.lat = Double.parseDouble(s[2]);
                    w.lon = Double.parseDouble(s[3]);
                    w.alt = s[4].length() == 0 ? Double.NaN : Double.parseDouble(s[4]);
                    w.time = Long.parseLong(s[5]);
                    w.cat = Math.max(0, Math.min(Cats.COUNT - 1, Integer.parseInt(s[6])));
                    w.fav = s[7].equals("1");
                    w.notes = unesc(s[8]);
                    list.add(w);
                } catch (NumberFormatException e) { /* skip a damaged line */ }
            }
        } catch (IOException e) {
            /* keep what was read */
        } finally {
            try { if (br != null) br.close(); } catch (IOException e) { }
        }
    }

    private void save() {
        StringBuilder sb = new StringBuilder();
        for (Waypoint w : list) {
            sb.append(w.id).append('\t').append(esc(w.name)).append('\t')
                    .append(String.format(Locale.US, "%.8f", w.lat)).append('\t').append(String.format(Locale.US, "%.8f", w.lon)).append('\t')
                    .append(Double.isNaN(w.alt) ? "" : String.format(Locale.US, "%.1f", w.alt)).append('\t')
                    .append(w.time).append('\t').append(w.cat).append('\t').append(w.fav ? "1" : "0").append('\t')
                    .append(esc(w.notes)).append('\n');
        }
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        FileOutputStream fo = null;
        try {
            fo = new FileOutputStream(tmp);
            fo.write(sb.toString().getBytes("UTF-8"));
            fo.getFD().sync();
            fo.close();
            fo = null;
            if (!tmp.renameTo(file)) {            // some systems will not rename over an existing file
                file.delete();
                tmp.renameTo(file);
            }
        } catch (IOException e) {
            /* nothing more can be done here */
        } finally {
            try { if (fo != null) fo.close(); } catch (IOException e) { }
        }
    }

    // ------------------------------------------------------------------ GPX
    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static SimpleDateFormat iso() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f;
    }

    public static void exportGpx(List<Waypoint> l, OutputStream os) throws IOException {
        SimpleDateFormat f = iso();
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<gpx version=\"1.1\" creator=\"Waypoints by Black Falcon\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        for (Waypoint w : l) {
            sb.append(String.format(Locale.US, "<wpt lat=\"%.8f\" lon=\"%.8f\">", w.lat, w.lon));
            if (!Double.isNaN(w.alt)) sb.append(String.format(Locale.US, "<ele>%.1f</ele>", w.alt));
            sb.append("<time>").append(f.format(new Date(w.time))).append("</time>");
            sb.append("<name>").append(xml(w.name)).append("</name>");
            if (w.notes.length() > 0) sb.append("<desc>").append(xml(w.notes)).append("</desc>");
            sb.append("<sym>").append(Cats.KEY[w.cat]).append("</sym><type>").append(Cats.KEY[w.cat]).append("</type>");
            sb.append("</wpt>\n");
        }
        sb.append("</gpx>\n");
        os.write(sb.toString().getBytes("UTF-8"));
        os.flush();
    }

    private static String childText(Element e, String tag) {
        NodeList n = e.getElementsByTagName(tag);
        if (n.getLength() == 0) return "";
        Node c = n.item(0);
        String t = c.getTextContent();
        return t == null ? "" : t.trim();
    }

    private static long parseTime(String t) {
        if (t.length() == 0) return 0;
        try {
            String s = t.replaceAll("\\.\\d+", "");
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(s).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Reads waypoints from a GPX file and adds the new ones. Returns how many were added. */
    public synchronized int importGpx(InputStream in) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception e) { /* not supported everywhere */ }
        try { f.setExpandEntityReferences(false); } catch (Exception e) { }
        DocumentBuilder b = f.newDocumentBuilder();
        Document doc = b.parse(in);
        NodeList nodes = doc.getElementsByTagName("wpt");
        int added = 0;
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            double la, lo;
            try {
                la = Double.parseDouble(e.getAttribute("lat"));
                lo = Double.parseDouble(e.getAttribute("lon"));
            } catch (NumberFormatException ex) {
                continue;
            }
            if (la < -90 || la > 90 || lo < -180 || lo > 180) continue;
            Waypoint w = new Waypoint();
            w.lat = la;
            w.lon = lo;
            w.name = childText(e, "name");
            if (w.name.length() == 0) w.name = "Waypoint " + (list.size() + added + 1);
            String d = childText(e, "desc");
            w.notes = d.length() > 0 ? d : childText(e, "cmt");
            String ele = childText(e, "ele");
            try { w.alt = ele.length() == 0 ? Double.NaN : Double.parseDouble(ele); } catch (NumberFormatException ex) { w.alt = Double.NaN; }
            long t = parseTime(childText(e, "time"));
            w.time = t > 0 ? t : System.currentTimeMillis();
            String ty = childText(e, "type");
            w.cat = Cats.fromKey(ty.length() > 0 ? ty : childText(e, "sym"));
            boolean dup = false;
            for (Waypoint x : list) {
                if (x.name.equals(w.name) && Geo.distance(x.lat, x.lon, w.lat, w.lon) < 2.0) { dup = true; break; }
            }
            if (dup) continue;
            w.id = newId();
            list.add(w);
            added++;
        }
        if (added > 0) save();
        return added;
    }

    // ------------------------------------------------------------------ CSV
    private static String csv(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    public static void exportCsv(List<Waypoint> l, OutputStream os) throws IOException {
        SimpleDateFormat f = iso();
        StringBuilder sb = new StringBuilder("\uFEFF");          // BOM so Excel reads Urdu / Arabic correctly
        sb.append("name,latitude,longitude,elevation_m,category,notes,saved_utc\n");
        for (Waypoint w : l) {
            sb.append(csv(w.name)).append(',')
                    .append(String.format(Locale.US, "%.8f,%.8f", w.lat, w.lon)).append(',')
                    .append(Double.isNaN(w.alt) ? "" : String.format(Locale.US, "%.1f", w.alt)).append(',')
                    .append(Cats.KEY[w.cat]).append(',').append(csv(w.notes)).append(',').append(f.format(new Date(w.time))).append('\n');
        }
        os.write(sb.toString().getBytes("UTF-8"));
        os.flush();
    }
}
