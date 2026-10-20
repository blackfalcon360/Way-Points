package com.blackfalcon.waypoints;

import java.util.HashMap;
import java.util.Map;

/** All texts in English (default), Urdu and Arabic. */
public final class Lang {
    private Lang() { }

    public static final int EN = 0, UR = 1, AR = 2;
    private static final Map<String, String[]> T = new HashMap<>();

    private static void a(String k, String en, String ur, String ar) { T.put(k, new String[]{en, ur, ar}); }

    static {
        // tabs
        a("tab_list", "Waypoints", "ویپوائنٹس", "النقاط");
        a("tab_compass", "Compass", "قطب نما", "البوصلة");
        a("tab_set", "Settings", "ترتیبات", "الإعدادات");
        // list
        a("search_hint", "Search waypoints", "ویپوائنٹ تلاش کریں", "ابحث في النقاط");
        a("sort", "Sort", "ترتیب", "الفرز");
        a("s_near", "Nearest", "قریب ترین", "الأقرب");
        a("s_new", "Newest", "تازہ ترین", "الأحدث");
        a("s_name", "Name", "نام", "الاسم");
        a("s_cat", "Category", "زمرہ", "الفئة");
        a("all", "All", "سب", "الكل");
        a("favs", "Favourites", "پسندیدہ", "المفضلة");
        a("add", "+ Add", "+ شامل کریں", "+ إضافة");
        a("save_here", "\uD83D\uDCCD Save here", "\uD83D\uDCCD یہاں محفوظ کریں", "\uD83D\uDCCD احفظ هنا");
        a("empty", "No waypoints yet. Tap \"Save here\" to store your current position.", "ابھی کوئی ویپوائنٹ نہیں۔ اپنی موجودہ جگہ محفوظ کرنے کے لیے \"یہاں محفوظ کریں\" دبائیں۔", "لا توجد نقاط بعد. اضغط «احفظ هنا» لحفظ موقعك الحالي.");
        a("no_match", "Nothing matches your search", "تلاش سے کچھ نہیں ملا", "لا نتائج مطابقة");
        a("gps_wait", "Waiting for GPS\u2026", "GPS کا انتظار ہے\u2026", "بانتظار GPS\u2026");
        a("gps_off", "GPS is off \u2014 turn on Location", "GPS بند ہے \u2014 Location آن کریں", "GPS متوقف \u2014 فعّل الموقع");
        a("need_perm", "Location permission is needed", "مقام کی اجازت ضروری ہے", "إذن الموقع مطلوب");
        a("gps", "GPS", "GPS", "GPS");
        a("wp_default", "WP", "ویپوائنٹ", "نقطة");
        a("saved_toast", "Saved", "محفوظ ہو گیا", "تم الحفظ");
        // detail
        a("coords", "Coordinates", "کوآرڈینیٹس", "الإحداثيات");
        a("tap_copy", "Tap a line to copy it", "کاپی کرنے کے لیے لائن دبائیں", "اضغط على سطر لنسخه");
        a("copied", "Copied", "کاپی ہو گیا", "تم النسخ");
        a("navigate", "\u27A4 Navigate", "\u27A4 رہنمائی", "\u27A4 توجيه");
        a("edit", "Edit", "ترمیم", "تعديل");
        a("share", "Share", "شیئر", "مشاركة");
        a("open_maps", "Open in Maps", "نقشے میں کھولیں", "فتح في الخرائط");
        a("delete", "Delete", "حذف کریں", "حذف");
        a("update_pos", "Update to my position", "میری موجودہ جگہ پر اپ ڈیٹ کریں", "تحديث إلى موقعي");
        a("from_you", "From you", "آپ سے", "منك");
        a("elev", "Elevation", "بلندی", "الارتفاع");
        a("saved_on", "Saved", "محفوظ کیا گیا", "تاريخ الحفظ");
        a("notes", "Notes", "نوٹس", "ملاحظات");
        a("category", "Category", "زمرہ", "الفئة");
        a("delete_q", "Delete this waypoint?", "کیا یہ ویپوائنٹ حذف کریں؟", "حذف هذه النقطة؟");
        a("delete_msg", "It will be removed from this phone for good.", "یہ اس فون سے ہمیشہ کے لیے ہٹ جائے گا۔", "ستُزال من هذا الهاتف نهائيًا.");
        a("moved_toast", "Position updated", "جگہ اپ ڈیٹ ہو گئی", "تم تحديث الموقع");
        a("back", "Back", "واپس", "رجوع");
        // edit
        a("new_wp", "New waypoint", "نیا ویپوائنٹ", "نقطة جديدة");
        a("edit_wp", "Edit waypoint", "ویپوائنٹ میں ترمیم", "تعديل النقطة");
        a("name", "Name", "نام", "الاسم");
        a("notes_hint", "Notes (optional)", "نوٹس (اختیاری)", "ملاحظات (اختياري)");
        a("lat", "Latitude", "عرض بلد", "خط العرض");
        a("lon", "Longitude", "طول بلد", "خط الطول");
        a("alt", "Elevation in metres (optional)", "بلندی میٹر میں (اختیاری)", "الارتفاع بالمتر (اختياري)");
        a("coord_hint", "e.g. 33.6844   or   33\u00B041'3.8\"N", "مثال: 33.6844   یا   33\u00B041'3.8\"N", "مثال: 33.6844   أو   33\u00B041'3.8\"N");
        a("use_current", "Use my position", "میری موجودہ جگہ استعمال کریں", "استخدم موقعي");
        a("average", "Average for 10 s (more accurate)", "10 سیکنڈ کا اوسط (زیادہ درست)", "متوسط 10 ثوانٍ (أدق)");
        a("averaging", "Averaging", "اوسط لیا جا رہا ہے", "جارٍ أخذ المتوسط");
        a("fixes", "readings", "ریڈنگز", "قراءات");
        a("project", "Project a point from a bearing and distance\u2026", "سمت اور فاصلے سے نیا نقطہ\u2026", "إسقاط نقطة من اتجاه ومسافة\u2026");
        a("project_title", "Project a point", "نقطہ بنائیں", "إسقاط نقطة");
        a("proj_from", "Start from", "شروع کریں", "ابدأ من");
        a("proj_here", "My position", "میری جگہ", "موقعي");
        a("proj_entered", "Entered coordinates", "درج کردہ کوآرڈینیٹس", "الإحداثيات المدخلة");
        a("bearing_deg", "Bearing in degrees (0\u2013360)", "سمت ڈگری میں (0\u2013360)", "الاتجاه بالدرجات (0\u2013360)");
        a("dist_m", "Distance in metres", "فاصلہ میٹر میں", "المسافة بالمتر");
        a("dist_ft", "Distance in feet", "فاصلہ فٹ میں", "المسافة بالقدم");
        a("apply", "Apply", "لاگو کریں", "تطبيق");
        a("save", "Save", "محفوظ کریں", "حفظ");
        a("cancel", "Cancel", "منسوخ", "إلغاء");
        a("name_required", "Enter a name", "نام لکھیں", "أدخل اسمًا");
        a("bad_coord", "The coordinates are not valid", "کوآرڈینیٹس درست نہیں", "الإحداثيات غير صحيحة");
        a("bad_value", "Please check the numbers", "براہ کرم اعداد دیکھ لیں", "يرجى التحقق من الأرقام");
        a("favourite", "\u2605 Favourite", "\u2605 پسندیدہ", "\u2605 مفضلة");
        // compass
        a("no_target", "Choose a waypoint to navigate to", "رہنمائی کے لیے ویپوائنٹ چنیں", "اختر نقطة للتوجيه");
        a("choose", "Choose waypoint", "ویپوائنٹ چنیں", "اختر نقطة");
        a("stop_nav", "Stop", "بند کریں", "إيقاف");
        a("dist_to", "Distance", "فاصلہ", "المسافة");
        a("bearing_to", "Bearing", "سمت", "الاتجاه");
        a("heading", "Heading", "آپ کا رخ", "اتجاهك");
        a("eta", "ETA", "متوقع وقت", "الوقت المتوقع");
        a("arrived", "You have arrived", "آپ پہنچ گئے ہیں", "لقد وصلت");
        a("ahead", "straight ahead", "سیدھے چلیں", "للأمام مباشرة");
        a("turn_right", "turn right", "دائیں مڑیں", "انعطف يمينًا");
        a("turn_left", "turn left", "بائیں مڑیں", "انعطف يسارًا");
        a("behind", "turn around", "پیچھے مڑیں", "استدر للخلف");
        a("true_n", "True north", "حقیقی شمال", "الشمال الحقيقي");
        a("mag_n", "Magnetic north", "مقناطیسی شمال", "الشمال المغناطيسي");
        a("show_others", "Other waypoints", "دوسرے ویپوائنٹس", "نقاط أخرى");
        a("calib", "Move the phone in a figure 8 to calibrate the compass", "کمپاس درست کرنے کے لیے فون کو 8 کی شکل میں گھمائیں", "حرّك الهاتف على شكل 8 لمعايرة البوصلة");
        a("no_sensor", "No compass sensor \u2014 direction comes from GPS while you move", "کمپاس سینسر نہیں \u2014 چلتے ہوئے رخ GPS سے ملتا ہے", "لا مستشعر بوصلة \u2014 الاتجاه من GPS أثناء الحركة");
        a("higher", "higher", "اوپر", "أعلى");
        a("lower", "lower", "نیچے", "أدنى");
        a("on", "On", "آن", "تشغيل");
        a("off", "Off", "آف", "إيقاف");
        // settings
        a("lang", "Language", "زبان", "اللغة");
        a("units", "Units", "اکائیاں", "الوحدات");
        a("metric", "Metric (m, km)", "میٹرک (میٹر، کلومیٹر)", "متري (م، كم)");
        a("imperial", "Imperial (ft, mi)", "امپیریل (فٹ، میل)", "إمبراطوري (قدم، ميل)");
        a("coord_fmt", "Coordinate format", "کوآرڈینیٹ کا انداز", "صيغة الإحداثيات");
        a("f_dd", "Decimal degrees", "اعشاری ڈگری", "درجات عشرية");
        a("f_ddm", "Degrees + minutes", "ڈگری + منٹ", "درجات + دقائق");
        a("f_dms", "Degrees, minutes, seconds", "ڈگری، منٹ، سیکنڈ", "درجات ودقائق وثوانٍ");
        a("f_utm", "UTM", "UTM", "UTM");
        a("north_ref", "North for the compass and bearings", "کمپاس اور سمت کے لیے شمال", "الشمال للبوصلة والاتجاهات");
        a("true", "True", "حقیقی", "حقيقي");
        a("magnetic", "Magnetic", "مقناطیسی", "مغناطيسي");
        a("arrival", "Arrival alert (vibrates when you get this close)", "پہنچنے کا الارٹ (اتنے قریب آنے پر وائبریٹ)", "تنبيه الوصول (يهتز عند الاقتراب)");
        a("keep_on", "Keep the screen on in the compass", "کمپاس میں اسکرین چالو رکھیں", "إبقاء الشاشة مضاءة في البوصلة");
        a("data", "Data", "ڈیٹا", "البيانات");
        a("export_gpx", "Export all as GPX", "سب GPX میں برآمد کریں", "تصدير الكل بصيغة GPX");
        a("export_csv", "Export all as CSV", "سب CSV میں برآمد کریں", "تصدير الكل بصيغة CSV");
        a("import_gpx", "Import GPX file", "GPX فائل درآمد کریں", "استيراد ملف GPX");
        a("delete_all", "Delete all waypoints", "تمام ویپوائنٹس حذف کریں", "حذف جميع النقاط");
        a("delete_all_q", "Delete ALL waypoints?", "کیا تمام ویپوائنٹس حذف کریں؟", "حذف جميع النقاط؟");
        a("imported", "waypoints imported", "ویپوائنٹس درآمد ہوئے", "نقاط تم استيرادها");
        a("file_saved", "File saved", "فائل محفوظ ہو گئی", "تم حفظ الملف");
        a("file_fail", "Could not read or save the file", "فائل پڑھی یا محفوظ نہیں ہو سکی", "تعذّر قراءة الملف أو حفظه");
        a("nothing", "No waypoints to export", "برآمد کرنے کے لیے کوئی ویپوائنٹ نہیں", "لا نقاط للتصدير");
        a("about", "Everything works offline. Your waypoints stay on this phone unless you export or share them. Export a GPX file now and then as a backup \u2014 it opens in Google Earth, Garmin, Strava, Komoot and most GPS apps.",
                "سب کچھ آف لائن چلتا ہے۔ آپ کے ویپوائنٹس اسی فون میں رہتے ہیں جب تک آپ برآمد یا شیئر نہ کریں۔ بیک اپ کے طور پر وقتاً فوقتاً GPX فائل برآمد کر لیں \u2014 یہ Google Earth، Garmin، Strava، Komoot اور زیادہ تر GPS ایپس میں کھلتی ہے۔",
                "كل شيء يعمل دون إنترنت. تبقى نقاطك على هذا الهاتف ما لم تصدّرها أو تشاركها. صدّر ملف GPX من حين لآخر كنسخة احتياطية \u2014 يفتح في Google Earth وGarmin وStrava وKomoot ومعظم تطبيقات GPS.");
        a("km", "km", "کلومیٹر", "كم");
        a("m", "m", "میٹر", "م");
        a("mi", "mi", "میل", "ميل");
        a("ft", "ft", "فٹ", "قدم");
        a("h", "h", "گھنٹے", "س");
        a("min", "min", "منٹ", "د");
        // categories
        a("c_general", "General", "عمومی", "عام");
        a("c_home", "Home", "گھر", "المنزل");
        a("c_camp", "Camp", "کیمپ", "مخيم");
        a("c_water", "Water", "پانی", "ماء");
        a("c_vehicle", "Vehicle / parking", "گاڑی / پارکنگ", "مركبة / موقف");
        a("c_viewpoint", "Viewpoint", "نظارے کی جگہ", "نقطة مشاهدة");
        a("c_danger", "Danger", "خطرہ", "خطر");
        a("c_food", "Food", "کھانا", "طعام");
        a("c_fuel", "Fuel", "پیٹرول / ایندھن", "وقود");
        a("c_mosque", "Mosque", "مسجد", "مسجد");
        a("c_hospital", "Hospital", "ہسپتال", "مستشفى");
        a("c_peak", "Peak", "چوٹی", "قمة");
        a("c_rest", "Hotel / rest", "ہوٹل / آرام", "فندق / استراحة");
        a("c_shop", "Shop", "دکان", "متجر");
    }

    public static final String[] DIR_EN = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    public static final String[] DIR_UR = {"شمال", "شمال مشرق", "مشرق", "جنوب مشرق", "جنوب", "جنوب مغرب", "مغرب", "شمال مغرب"};
    public static final String[] DIR_AR = {"شمال", "شمال شرق", "شرق", "جنوب شرق", "جنوب", "جنوب غرب", "غرب", "شمال غرب"};

    public static String t(int lang, String key) {
        String[] v = T.get(key);
        return v == null ? key : v[lang];
    }

    public static String[] dirs(int lang) { return lang == UR ? DIR_UR : (lang == AR ? DIR_AR : DIR_EN); }
}
