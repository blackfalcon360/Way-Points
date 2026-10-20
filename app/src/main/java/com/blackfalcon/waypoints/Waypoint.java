package com.blackfalcon.waypoints;

/** One saved place. */
public final class Waypoint {
    public long id;
    public String name = "", notes = "";
    public double lat, lon;
    public double alt = Double.NaN;     // metres, NaN if unknown
    public long time;                   // when it was saved (epoch ms)
    public int cat;                     // index into Cats
    public boolean fav;

    public Waypoint copy() {
        Waypoint w = new Waypoint();
        w.id = id; w.name = name; w.notes = notes; w.lat = lat; w.lon = lon; w.alt = alt; w.time = time; w.cat = cat; w.fav = fav;
        return w;
    }
}
