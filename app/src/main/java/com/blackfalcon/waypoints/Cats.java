package com.blackfalcon.waypoints;

/** Waypoint categories: an emoji, a colour and a language key each. */
public final class Cats {
    private Cats() { }

    public static final String[] EMOJI = {"\uD83D\uDCCD", "\uD83C\uDFE0", "\u26FA", "\uD83D\uDCA7", "\uD83D\uDE97", "\uD83D\uDD2D", "\u26A0\uFE0F",
            "\uD83C\uDF74", "\u26FD", "\uD83D\uDD4C", "\uD83C\uDFE5", "\u26F0\uFE0F", "\uD83D\uDECF\uFE0F", "\uD83D\uDED2"};
    public static final int[] COLOR = {0xFF4FC3F7, 0xFF81C784, 0xFFFFB74D, 0xFF29B6F6, 0xFFB0BEC5, 0xFFBA68C8, 0xFFE57373,
            0xFFFF8A65, 0xFFFFD54F, 0xFF4DB6AC, 0xFFEF5350, 0xFF90A4AE, 0xFF9575CD, 0xFFA1887F};
    /** language keys (also used as the GPX "type") */
    public static final String[] KEY = {"general", "home", "camp", "water", "vehicle", "viewpoint", "danger", "food", "fuel", "mosque", "hospital", "peak", "rest", "shop"};
    public static final int COUNT = KEY.length;

    public static int fromKey(String k) {
        if (k == null) return 0;
        String s = k.trim().toLowerCase(java.util.Locale.US);
        for (int i = 0; i < COUNT; i++) if (KEY[i].equals(s)) return i;
        return 0;
    }
}
