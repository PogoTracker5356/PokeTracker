package net.anon.poketracker;

import android.content.Context;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

import android.app.Application;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.location.*;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.pokegoapi.api.PokemonGo;

/**
 * Created by Owner on 2016-07-26.
 */
public class GlobalVars extends Application {

    private static Context appContext;
    public NianticManager mNianticManager;
    public PokemonGo mPokemonGo;
    PDataViewAdapter pokemonListAdapter;
    View mainActivity;

    boolean[] successArray = new boolean[7];

    // region Initialization
    @Override
    public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();
    }

    public static Context getContext() {
        return appContext;
    }

    SharedPreferences pref;
    SharedPreferences.Editor pref_editor;

    public void init() {
        Log.d("PK-INIT", "Initialize");
        pref = PreferenceManager.getDefaultSharedPreferences(appContext);
        plist = new ArrayList<PData>();

        Log.d("PK-LOC", "Creating loc listener");
        try {
            locList = new MyLocationListener();
            aLocationManager = (LocationManager) getSystemService(android.content.Context.LOCATION_SERVICE);
            aLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 1, locList);
            Criteria aCriteria = new android.location.Criteria();
            aProvider = aLocationManager.getBestProvider(aCriteria, false);
            Location aLocation = aLocationManager.getLastKnownLocation(aProvider);
            TellUser("Connected to GPS.");
            try {
                user_lat = aLocation.getLatitude();
                user_lon = aLocation.getLongitude();
            } catch (NullPointerException npe) {
                TellUser("Location not found - try moving.");
            }
            handler = new Handler();
            handler.postDelayed(locationTask, 100);
        } catch (SecurityException se) {
            try {
                Thread.sleep(5000);
                init();
            }
            catch(InterruptedException e) {
                //At this point there's no salvaging things
                TellUser("GPS permissions off.");
            }
        }
        catch (Exception e) {
            Log.d("PK-INIT", "Uh, that was unhandled.  I dunno");
            TellUser("GPS permissions off.");
        }
        mNianticManager = NianticManager.getInstance();
    }

    public void scheduleLogin(View v) {
        try {
            Log.d("PK-LOG", "OK");
            String user = getEditText(v, R.id.lg_username);
            Log.d("PK-LOG", user);
            if (user.equals("DummyAccount")) {
                TellUser("Please create a Club Pokemon account for use with this app.  (Do not use your real account).");
            } else {
                mNianticManager.login(getEditText(v, R.id.lg_username), getEditText(v, R.id.lg_password));
            }
        } catch (Exception e) {
            // This error never seems to come up, but I might as well check for it anyway, just in case.
            TellUser("Failed login.  Are the servers down?");
        }
    }
    // endregion

    // region Tasks
    public void showSignIn(boolean show)
    {
        showView(R.id.lg_username, show);
        showView(R.id.lg_password, show);
        showButton(R.id.lg_signin, show);
    }
    public void showView(int v, boolean show) {
        EditText text = (EditText) mainActivity.findViewById(v);
        text.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        text.setLayoutParams(new LinearLayout.LayoutParams(text.getWidth(), (show ? dpToPx(50) : 0)));
    }
    public void showButton(int v, boolean show) {
        Button b = (Button) mainActivity.findViewById(v);
        b.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        b.setLayoutParams(new LinearLayout.LayoutParams(b.getWidth(), (show ? dpToPx(50) : 0)));
    }

    public boolean loggedIn = false;
    public boolean active = false;
    public boolean loginFail = false;
    public int scanned = -1;
    public int taskSeconds = 0;
    public int lastReorder = 0;
    public int DISTANCE_RECALC_FACTOR = 1;
    public int TASK_RESET = 3600; // 1 hour
    public int SCAN_COOLDOWN = 45;
    public long lastScan = -1;//-SCAN_COOLDOWN;
    public int lastScannedField = -1;
    public int lastReport = 10;
    public boolean currentlyScanning = true;
    int delay = 5;
    private Runnable locationTask = new Runnable() {
        @Override
        public void run() {
            if (!loggedIn) {
                if (mPokemonGo != null) {
                    TellUser("Logged in.");
                    SharedPreferences.Editor pref_editor = pref.edit();
                    pref_editor.putString("username", getEditText(mainActivity, R.id.lg_username));
                    pref_editor.putString("password", getEditText(mainActivity, R.id.lg_password));
                    pref_editor.commit();
                    showSignIn(false);
                    // And hide the keyboard unless the user requested it
                    InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(mainActivity.getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);
                    snapshotLocation();
                    loggedIn = true;
                    active = true;
                }
            }
            if (active) {
                if (taskSeconds - lastReorder >= DISTANCE_RECALC_FACTOR) {
                    lastReorder = taskSeconds;
                    //Log.d("PK-SAVE", "recalc is running");
                    compareLocation();
                }
                taskSeconds += 1;
                if (taskSeconds >= TASK_RESET) {
                    taskSeconds = 0;
                }
            }
            handler.postDelayed(this, delay * 1000);
        }
    };
    // endregion

    // region PData
    public long highlight_enc = -1;
    public ArrayList<PData> plist = new ArrayList<PData>();
    public ArrayList<PData> caughtList = new ArrayList<PData>();
    public final static int BADLOC = -1000;

    public static class PData implements Comparable<PData> {
        long encID;
        long pokID;
        String pokType;
        double latitude;
        double longitude;
        float distance; // For sorting
        long despawn;
        float bearing;
        int footsteps;
        boolean n;
        boolean ne;
        boolean se;
        boolean s;
        boolean sw;
        boolean nw;
        String direction;

        public PData(long enc, String type, double lat, double lon, long despawn, long pkid) {
            encID = enc;
            pokID = pkid;
            pokType = type;
            latitude = lat;
            longitude = lon;
            bearing = 1000;
            this.despawn = despawn;
        }

        public void calcDistance(double lat, double lon) {
            direction = "";

            if (latitude == BADLOC || longitude == BADLOC){
                distance = 9999;
                //estimate rough direction for pokemon whose exact location isn't known.
                boolean north = false;
                boolean east = false;
                boolean south = false;
                boolean west = false;
                if (n || ne || nw) north = true;
                if (s || se || sw) south = true;
                if (ne || se) east = true;
                if (nw || sw) west = true;
                if (east && west) {
                    east = false;
                    west = false;
                }
                if(north && south) {
                    north = false;
                    south = false;
                }
                if(north) direction += "N";
                if(south) direction += "S";
                if(east) direction += "E";
                if(west) direction += "W";
            }
            else {
                //find bearing and precise distance for pokemon whose direction is known
                float[] diff = new float[3];
                android.location.Location.distanceBetween(lat, lon, latitude, longitude, diff);
                distance = diff[0];
                bearing = (diff[1] + 360) % 360;
            }
            if (distance >= 170) footsteps = 3;
            else if (distance >= 120) footsteps = 2;
            else if (distance >= 70) footsteps = 1;
            else footsteps = 0;
        }

        @Override
        public int compareTo(PData cmp) {

            if (distance > cmp.distance) {
                return 1;
            } else if (distance < cmp.distance) {
                return -1;
            } else {
                if (encID > cmp.encID) {
                    return 1;
                } else if (encID < cmp.encID) {
                    return -1;
                }
                return 0;
            }
        }

        @Override
        public boolean equals(Object cmp) {
            if (cmp == null) {
                return false;
            }
            if (!PData.class.isAssignableFrom(cmp.getClass())) {
                return false;
            }
            PData p = (PData) cmp;
            if (this.encID == p.encID) {
                return true;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return (int) encID;
        }
    }

    public void removeNearby() {
        ArrayList<PData> removeList = new ArrayList<PData>();
        for(PData p : plist) {
            if(p.despawn == (long)-1){
                removeList.add(p);
            }
        }
        for(PData x : removeList){
            plist.remove(x);
        }
    }
    public void removePokemon(long enc)
    {
        PData delTarget = new PData(enc, "", 0.0, 0.0, -1, 0); // Only enc matters here because equals() has been overridden
        int targetPos = plist.indexOf(delTarget);
        delTarget = plist.get(targetPos);

        try {
            plist.remove(delTarget);
        }
        catch(Exception e) {Log.d("PK-DELETE", "Couldn't find it");}

        if(caughtList.indexOf(delTarget) == -1){
            caughtList.add(delTarget);
        }

        refreshPokemonList();
    }
    public void catchPokemon(PData caughtMon) {
        int position = caughtList.indexOf(caughtMon);
        if(position == -1) {
            caughtList.add(caughtMon);
        }
    }
    public void refreshPokemonList()
    {
        /*ListView obj = (ListView)mainActivity.findViewById(R.id.pk_list_view);
        synchronized (obj)
        {
            obj.notify();
        }*/
        pokemonListAdapter.notifyDataSetChanged();
    }

    public void addPokemon(long enc, String type, double lat, double lon, long despawn, long pkid, int fieldNum) {
        PData p = new PData(enc, type, lat, lon, despawn, pkid);
        //Don't add "caught" pokemon
        int cPos = caughtList.indexOf(p);
        if(cPos != -1){
            return;
        }
        //if actual position is unknown, signify field it came from.
        if (p.latitude == BADLOC || p.longitude == BADLOC) {
            if (fieldNum == 0) {
                p.n = true;
            } else if (fieldNum == 1) {
                p.ne = true;
            } else if (fieldNum == 2) {
                p.se = true;
            } else if (fieldNum == 3) {
                p.s = true;
            } else if (fieldNum == 4) {
                p.sw = true;
            } else if (fieldNum == 5) {
                p.nw = true;
            }
        }

        int position = plist.indexOf(p);

        //simplest case, if it doesn't exist, add it.
        Log.d("PK-AL", "position was " + position);
        if (position == -1) {
            p.calcDistance(user_lat, user_lon);
            plist.add(p);
        }
        //This part is a bit tricky
        else {
            PData oldPoke = plist.get(position);

            //If we've got a wild Pokemon... stick it in at the old position
            if (p.latitude != BADLOC && p.longitude != BADLOC) {
                p.calcDistance(user_lat, user_lon);
                plist.set(position, p);
            }
            //If we've got a "nearby" Pokemon, fork on status of the on already in there
            //For an prevoius "nearby" entry, update the fields
            else if (oldPoke.latitude == BADLOC && oldPoke.longitude == BADLOC) {
                if (p.n) {
                    oldPoke.n = true;
                } else if (p.ne) {
                    oldPoke.n = true;
                } else if (p.se) {
                    oldPoke.n = true;
                } else if (p.s) {
                    oldPoke.n = true;
                } else if (p.sw) {
                    oldPoke.n = true;
                } else if (p.nw) {
                    oldPoke.n = true;
                }
                plist.set(position, oldPoke);
            }
            //for a "wild" existing pokemon, do nothing when attempting to insert a nearby pokemon.
        }
    }

    //for loading saved pokemon by StateMaintainer
    public void addPokemon(PData newPoke) {
        int cPos = caughtList.indexOf(newPoke);
        if(cPos != -1){
            return;
        }

        int position = plist.indexOf(newPoke);
        Log.d("PK-AL", "position was " + position);
        if (position == -1) {
            plist.add(newPoke);
        }

    }

    public int getPokemonListSize() {
        return plist.size();
    }

    public PData getPokemonData(int i) {
        return plist.get(i);
    }

    public PData getEncounter(long enc)
    {
        PData found = null;
        for (PData p : plist) {
            if (p.encID == enc) found = p;
        }
        return found;
    }
    public PData getTrackedEncounter()
    {
        return getEncounter(highlight_enc);
    }
    public void sortPokemonList(boolean recalc)
    {
        if (recalc)
        {
            long current = System.currentTimeMillis();

            ArrayList<PData> removeList = new ArrayList<PData>();

            for (PData p : plist) {
                p.calcDistance(user_lat, user_lon);
                if((p.despawn - current) < 0 && p.despawn != (long)-1 && !currentlyScanning) {
                    Log.d("PK-DESPAWN", "Removing something");
                    removeList.add(p);
                }
                else {} // Nothing
            }
            for(PData x : removeList){
                plist.remove(x);
            }
        }
        Collections.sort(plist);
        pokemonListAdapter.notifyDataSetChanged();
    }
    public void logPokemonList()
    {
        Log.d("PK-FIND", "Nearby pokemon:");
        for (PData p : plist)
        {
            Log.d("PK-FIND", p.pokID + ": " + p.encID + " " + p.pokType + ": " + round(p.distance, 1) + "m " + p.direction);
        }
    }
    // endregion

    // region Location Stuff
    public double user_lat;
    public double user_lon;
    public static int NEVER = 0;
    public static int ALWAYS = 1;
    public static int WHEN_LOC_UNKNOWN = 2;
    public int showDirection = NEVER;
    public boolean preciseDistance = false;
    public boolean preciseDirection = false;
    public int scan_sleep = 10;
    public LocationListener locList;
    public android.location.LocationManager aLocationManager;
    //public android.location.Location aLocation;
    //public android.location.Criteria aCriteria;
    public String aProvider;
    public Handler handler;
    public ArrayList<android.location.Location> locationArray;
    public class MyLocationListener implements LocationListener
    {
        @Override
        public void onLocationChanged(Location l)
        {
            user_lat = l.getLatitude();
            user_lon = l.getLongitude();
        }
        public void onStatusChanged(String s, int i, Bundle b) {}
        public void onProviderEnabled(String s) {}
        public void onProviderDisabled(String s) {}
    }
    public void snapshotLocation()
    {
        // First check scan sleep

        EditText sleep = (EditText)mainActivity.findViewById(R.id.pk_sleep);
        SharedPreferences.Editor pref_editor = pref.edit();
        scan_sleep = Integer.parseInt(sleep.getText().toString());
        Log.d("PK-SLEEP", "Sleeping " + scan_sleep);
        pref_editor.putInt("scan_sleep", scan_sleep);
        pref_editor.commit();
        // Then scan
        long currTime = System.currentTimeMillis() / (long)1000;
        int timeToWait = SCAN_COOLDOWN - (int)(currTime - lastScan);
        if (mPokemonGo == null)
        {
            TellUser("You need to log in first.");
            showSignIn(true);
        }
        else {
            if (timeToWait <= 0) {
                TellUser("Beginning the scan.");
                scanned = -1; // When this changes, we'll report how many successes.  (Thread cannot report directly to TellUser).
                lastScan = currTime;
                mNianticManager.getCatchablePokemon(user_lat, user_lon, 0D); //maps.google.com/?q=50.4565,-104.679
            } else {
                TellUser("You may scan in " + timeToWait + "s.");
            }
        }
    }
    public void compareLocation()
    {
        sortPokemonList(true);
    }
    public static String getDirectionFromBearing(float deg)
    {
        float degPositive = (deg + 360) % 360;
        String dir = "";
        if (292.5 < degPositive || degPositive < 67.5)      dir += "N";     // A little less or more than 0
        if (112.5 < degPositive && degPositive < 247.5)     dir += "S";     // Everything else is simpler, just check if between
        if (22.5 < degPositive && degPositive < 157.5)      dir += "E";
        if (202.5 < degPositive && degPositive < 337.5)     dir += "W";
        return dir;
    }
    public static boolean getPrefDistance(Context c)
    {
        SharedPreferences wpref = PreferenceManager.getDefaultSharedPreferences(c);
        return wpref.getBoolean("precise_distance", false);
    }
    public static boolean getPrefDirection(Context c)
    {
        SharedPreferences wpref = PreferenceManager.getDefaultSharedPreferences(c);
        return wpref.getBoolean("precise_direction", false);
    }
    public static int getPrefShowDir(Context c)
    {
        SharedPreferences wpref = PreferenceManager.getDefaultSharedPreferences(c);
        return wpref.getInt("show_direction", NEVER);
    }
    public static String getDirectionText(PData pokemon, int showWhen, boolean precise, String field)
    {
        String text = "??";
        if (pokemon.latitude == BADLOC || pokemon.longitude == BADLOC) { // Exact location unknown
            if (showWhen == ALWAYS || showWhen == WHEN_LOC_UNKNOWN)
            {
                text = field;
            }
            else text = "";
        } else {
            if (showWhen == ALWAYS)
            {
                if (precise) text = Math.round(pokemon.bearing) + "`";
                else text = getDirectionFromBearing(pokemon.bearing);
            }
            else text = "";
        }
        return text;
    }
    public static String getDistanceText(PData pokemon, boolean precise)
    {
        String text = "??";
        if (pokemon.latitude == BADLOC || pokemon.longitude == BADLOC) { // Exact location unknown
            if (precise) text = "??";
            else text = "***";
        } else {
            if (precise) text = Math.round(pokemon.distance) + "m";
            else text = padText("", '*', pokemon.footsteps, true);
        }
        return text;
    }
    // endregion

    // region General Functions
    public static double round(double n, int decimals)
    {
        double tens = Math.pow(10, decimals);
        return Math.round(n * tens) / tens;
    }
    public static String padText(String s, char pad, int size, boolean before)
    {
        String ret = s;
        while (ret.length() < size)
            ret = (before ? pad : "") + ret + (before ? "" : pad);
        return ret;
    }
    public static String padDigits(int s, int size)
    {
        return padText(""+s, '0', size, true);
    }
    public Drawable getDrawableFromString(String s)
    {
        return ContextCompat.getDrawable(this, getResourceId(s, R.drawable.class));
    }
    /** http://stackoverflow.com/questions/4427608/android-getting-resource-id-from-string */
    public static int getResourceId(String resourceName, Class<?> c) {
        try {
            Field idField = c.getDeclaredField(resourceName);
            return idField.getInt(idField);
        } catch (Exception e) {
            throw new RuntimeException("No resource ID found for: "
                    + resourceName + " / " + c, e);
        }
    }
    public String getViewText(View v, int name)
    {
        return "" + ((TextView)v.findViewById(name)).getText();
    }
    public String getEditText(View v, int name)
    {
        return ((EditText)v.findViewById(name)).getText().toString();
    }
    public void setViewText(View v, int name, String s)
    {
        ((TextView)v.findViewById(name)).setText(s);
    }
    // http://stackoverflow.com/questions/8309354/formula-px-to-dp-dp-to-px-android
    public static int dpToPx(int dp) {
        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        int px = Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
        return px;
    }
    private String user_log = "";
    public void TellUser(String text)
    {
        Log.d("PK-LOG", "TellUser");
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        String time = sdf.format(new Date());
        String oldText = user_log;
        String addText = oldText + (oldText.length() > 0 ? "\n" : "") + time + " " + text;
        String newText = addText;
        if (newText.split("\n").length > 20)
        {
            newText = newText.substring(newText.indexOf("\n") + 1);
        }
        user_log = newText;
        Log.d("PK-LOG", "Text = " + newText);
        try {
            TextView v = (TextView) (mainActivity.findViewById(R.id.pk_logger));
            v.setText(newText);
            v.invalidate();
        } catch (NullPointerException npe) {
            Log.d("PK-LOG", "[LOG] " + addText); // Can't show on screen I guess, but at least log it.
        } catch (Exception e) {
            Log.d("PK-LOG", "Exception on TellUser", e);
        } // It's probably just not loaded yet... right?
    }
    /*public void ScheduleTell(String text)
    {
        Handler tellHandler;
        ScheduledTell r = new
        r
        handler.postDelayed(outputTask, 300);
    }
    public class ScheduledTell extends Runnable
    {
        public String nextTell = "";
        public void setTell(String s)
        {
            nextTell = s;
        }
        @Override
        public void run()
        {
            if (!nextTell.equals("")) TellUser(nextTell);
            nextTell = "";
        }
    }*/
    public Runnable outputLoginTask = new Runnable() {
        @Override
        public void run() {
            Log.d("PK-LOGIN", "loginFail");
            if (loginFail)
            {
                TellUser("Failed to log in.");
                loginFail = false;
            }
        }
    };
    public Runnable outputScanTask = new Runnable() {
        @Override
        public void run() {
            Log.d("PK-SLEEP", "Checking for scan output.");
            boolean reschedule = true;
            if (active) {
                while (lastReport <= lastScannedField) {
                    TellUser("Scanned " + fieldNum_to_direction(lastReport) + "...");
                    lastReport = lastReport + 1;
                }
                if (scanned > -1 && loggedIn) {
                    if (scanned >= 7) {
                        TellUser("Scanned " + scanned + " of 7 locations.");
                        reschedule = false;
                    }
                    int i = -1;
                    for (boolean success : successArray) {
                        String report = "Failed scan of ";

                        if (!success) {
                            if (i == -1) {
                                report = report + "immediate area";
                            } else {
                                report = report + fieldNum_to_direction(i) + "field";
                            }
                            TellUser(report);
                        }
                        i = i + 1;
                    }
                    scanned = -1;
                }
            }
            if (reschedule) handler.postDelayed(this, 1000);
        }
    };
    // endregion

    //public final ArrayList<String> = { "BULBASAUR", "IVYSAUR", "VENUSAUR" };

    public String fieldNum_to_direction(int fieldNum){
        if(fieldNum == -1) {
            return "immediate area";
        }
        else if(fieldNum == 0) {
            return "North";
        }
        else if(fieldNum == 1) {
            return "Northeast";
        }
        else if(fieldNum == 2) {
            return "Southeast";
        }
        else if(fieldNum == 3) {
            return "South";
        }
        else if(fieldNum == 4) {
            return "Southwest";
        }
        else if(fieldNum == 5) {
            return "Northwest";
        }
        return "some kinda error";
    }

    // region Pokemon Array
    public static int getIDFromName(String name)
    {
        int count = 1;  // The pokemon list starts at 1, e.g. Bulbasaur is #1 and the image is p1.
        int found_id = 0;
        Log.d("PK-GETID", "Name: " + name);
        for (String pok : pokarray)
        {
            if (name.toUpperCase().equals(pok.toUpperCase()))
            {
                //Log.d("PK-GETID", "Checking vs " + found_id);
                found_id = count;
                Log.d("PK-GETID", "Matching!  ID= " + found_id);
            }
            count += 1;
        }
        return found_id;
    }
    // https://www.reddit.com/r/pokemon/comments/1qrnw8/i_made_a_few_plain_text_printer_friendly_pokemon/
    public static String pokarray[] =
            { "Bulbasaur","Ivysaur","Venusaur","Charmander","Charmeleon","Charizard","Squirtle","Wartortle","Blastoise","Caterpie","Metapod",
            "Butterfree","Weedle","Kakuna","Beedrill","Pidgey","Pidgeotto","Pidgeot","Rattata","Raticate","Spearow","Fearow","Ekans","Arbok","Pikachu",
            "Raichu","Sandshrew","Sandslash","Nidoran_female","Nidorina","Nidoqueen","Nidoran_male","Nidorino","Nidoking","Clefairy","Clefable",
            "Vulpix","Ninetales","Jigglypuff","Wigglytuff","Zubat","Golbat","Oddish","Gloom","Vileplume","Paras","Parasect","Venonat","Venomoth",
            "Diglett","Dugtrio","Meowth","Persian","Psyduck","Golduck","Mankey","Primeape","Growlithe","Arcanine","Poliwag","Poliwhirl","Poliwrath",
            "Abra","Kadabra","Alakazam","Machop","Machoke","Machamp","Bellsprout","Weepinbell","Victreebel","Tentacool","Tentacruel","Geodude",
            "Graveler","Golem","Ponyta","Rapidash","Slowpoke","Slowbro","Magnemite","Magneton","Farfetch'd","Doduo","Dodrio","Seel","Dewgong",
            "Grimer","Muk","Shellder","Cloyster","Gastly","Haunter","Gengar","Onix","Drowzee","Hypno","Krabby","Kingler","Voltorb","Electrode",
            "Exeggcute","Exeggutor","Cubone","Marowak","Hitmonlee","Hitmonchan","Lickitung","Koffing","Weezing","Rhyhorn","Rhydon","Chansey",
            "Tangela","Kangaskhan","Horsea","Seadra","Goldeen","Seaking","Staryu","Starmie","Mr. Mime","Scyther","Jynx","Electabuzz",
            "Magmar","Pinsir","Tauros","Magikarp","Gyarados","Lapras","Ditto","Eevee","Vaporeon","Jolteon","Flareon","Porygon",
            "Omanyte","Omastar","Kabuto","Kabutops","Aerodactyl","Snorlax","Articuno","Zapdos","Moltres","Dratini","Dragonair","Dragonite",
            "Mewtwo","Mew","Chikorita","Bayleef","Meganium","Cyndaquil","Quilava","Typhlosion","Totodile","Croconaw","Feraligatr","Sentret",
            "Furret","Hoothoot","Noctowl","Ledyba","Ledian","Spinarak","Ariados","Crobat","Chinchou","Lanturn","Pichu","Cleffa",
            "Igglybuff","Togepi","Togetic","Natu","Xatu","Mareep","Flaaffy","Ampharos","Bellossom","Marill","Azumarill","Sudowoodo",
            "Politoed","Hoppip","Skiploom","Jumpluff","Aipom","Sunkern","Sunflora","Yanma","Wooper","Quagsire","Espeon","Umbreon",
            "Murkrow","Slowking","Misdreavus","Unown","Wobbuffet","Girafarig","Pineco","Forretress","Dunsparce","Gligar","Steelix","Snubbull",
            "Granbull","Qwilfish","Scizor","Shuckle","Heracross","Sneasel","Teddiursa","Ursaring","Slugma","Magcargo","Swinub","Piloswine",
            "Corsola","Remoraid","Octillery","Delibird","Mantine","Skarmory","Houndour","Houndoom","Kingdra","Phanpy","Donphan","Porygon2",
            "Stantler","Smeargle","Tyrogue","Hitmontop","Smoochum","Elekid","Magby","Miltank","Blissey","Raikou","Entei","Suicune",
            "Larvitar","Pupitar","Tyranitar","Lugia","Ho-Oh","Celebi","Treecko","Grovyle","Sceptile","Torchic","Combusken","Blaziken",
            "Mudkip","Marshtomp","Swampert","Poochyena","Mightyena","Zigzagoon","Linoone","Wurmple","Silcoon","Beautifly","Cascoon","Dustox",
            "Lotad","Lombre","Ludicolo","Seedot","Nuzleaf","Shiftry","Taillow","Swellow","Wingull","Pelipper","Ralts","Kirlia",
            "Gardevoir","Surskit","Masquerain","Shroomish","Breloom","Slakoth","Vigoroth","Slaking","Nincada","Ninjask","Shedinja","Whismur",
            "Loudred","Exploud","Makuhita","Hariyama","Azurill","Nosepass","Skitty","Delcatty","Sableye","Mawile","Aron","Lairon",
            "Aggron","Meditite","Medicham","Electrike","Manectric","Plusle","Minun","Volbeat","Illumise","Roselia","Gulpin","Swalot",
            "Carvanha","Sharpedo","Wailmer","Wailord","Numel","Camerupt","Torkoal","Spoink","Grumpig","Spinda","Trapinch","Vibrava",
            "Flygon","Cacnea","Cacturne","Swablu","Altaria","Zangoose","Seviper","Lunatone","Solrock","Barboach","Whiscash","Corphish",
            "Crawdaunt","Baltoy","Claydol","Lileep","Cradily","Anorith","Armaldo","Feebas","Milotic","Castform","Kecleon","Shuppet",
            "Banette","Duskull","Dusclops","Tropius","Chimecho","Absol","Wynaut","Snorunt","Glalie","Spheal","Sealeo","Walrein",
            "Clamperl","Huntail","Gorebyss","Relicanth","Luvdisc","Bagon","Shelgon","Salamence","Beldum","Metang","Metagross","Regirock",
            "Regice","Registeel","Latias","Latios","Kyogre","Groudon","Rayquaza","Jirachi","Deoxys","Turtwig","Grotle","Torterra",
            "Chimchar","Monferno","Infernape","Piplup","Prinplup","Empoleon","Starly","Staravia","Staraptor","Bidoof","Bibarel","Kricketot",
            "Kricketune","Shinx","Luxio","Luxray","Budew","Roserade","Cranidos","Rampardos","Shieldon","Bastiodon","Burmy","Wormadam",
            "Mothim","Combee","Vespiquen","Pachirisu","Buizel","Floatzel","Cherubi","Cherrim","Shellos","Gastrodon","Ambipom","Drifloon",
            "Drifblim","Buneary","Lopunny","Mismagius","Honchkrow","Glameow","Purugly","Chingling","Stunky","Skuntank","Bronzor","Bronzong",
            "Bonsly","Mime Jr.","Happiny","Chatot","Spiritomb","Gible","Gabite","Garchomp","Munchlax","Riolu","Lucario","Hippopotas",
            "Hippowdon","Skorupi","Drapion","Croagunk","Toxicroak","Carnivine","Finneon","Lumineon","Mantyke","Snover","Abomasnow","Weavile",
            "Magnezone","Lickilicky","Rhyperior","Tangrowth","Electivire","Magmortar","Togekiss","Yanmega","Leafeon","Glaceon","Gliscor","Mamoswine",
            "Porygon-Z","Gallade","Probopass","Dusknoir","Froslass","Rotom","Uxie","Mesprit","Azelf","Dialga","Palkia","Heatran",
            "Regigigas","Giratina","Cresselia","Phione","Manaphy","Darkrai","Shaymin","Arceus","Victini","Snivy","Servine","Serperior",
            "Tepig","Pignite","Emboar","Oshawott","Dewott","Samurott","Patrat","Watchog","Lillipup","Herdier","Stoutland","Purrloin",
            "Liepard","Pansage","Simisage","Pansear","Simisear","Panpour","Simipour","Munna","Musharna","Pidove","Tranquill","Unfezant",
            "Blitzle","Zebstrika","Roggenrola","Boldore","Gigalith","Woobat","Swoobat","Drilbur","Excadrill","Audino","Timburr","Gurdurr",
            "Conkeldurr","Tympole","Palpitoad","Seismitoad","Throh","Sawk","Sewaddle","Swadloon","Leavanny","Venipede","Whirlipede","Scolipede",
            "Cottonee","Whimsicott","Petilil","Lilligant","Basculin","Sandile","Krokorok","Krookodile","Darumaka","Darmanitan","Maractus","Dwebble",
            "Crustle","Scraggy","Scrafty","Sigilyph","Yamask","Cofagrigus","Tirtouga","Carracosta","Archen","Archeops","Trubbish","Garbodor",
            "Zorua","Zoroark","Minccino","Cinccino","Gothita","Gothorita","Gothitelle","Solosis","Duosion","Reuniclus","Ducklett","Swanna",
            "Vanillite","Vanillish","Vanilluxe","Deerling","Sawsbuck","Emolga","Karrablast","Escavalier","Foongus","Amoonguss","Frillish","Jellicent",
            "Alomomola","Joltik","Galvantula","Ferroseed","Ferrothorn","Klink","Klang","Klinklang","Tynamo","Eelektrik","Eelektross","Elgyem",
            "Beheeyem","Litwick","Lampent","Chandelure","Axew","Fraxure","Haxorus","Cubchoo","Beartic","Cryogonal","Shelmet","Accelgor",
            "Stunfisk","Mienfoo","Mienshao","Druddigon","Golett","Golurk","Pawniard","Bisharp","Bouffalant","Rufflet","Braviary","Vullaby",
            "Mandibuzz","Heatmor","Durant","Deino","Zweilous","Hydreigon","Larvesta","Volcarona","Cobalion","Terrakion","Virizion","Tornadus",
            "Thundurus","Reshiram","Zekrom","Landorus","Kyurem","Keldeo","Meloetta","Genesect","Chespin","Quilladin","Chesnaught","Fennekin",
            "Braixen","Delphox","Froakie","Frogadier","Greninja","Bunnelby","Diggersby","Fletchling","Fletchinder","Talonflame","Scatterbug","Spewpa",
            "Vivillon","Litleo","Pyroar","Flabébé","Floette","Florges","Skiddo","Gogoat","Pancham","Pangoro","Furfrou","Espurr",
            "Meowstic","Honedge","Doublade","Aegislash","Spritzee","Aromatisse","Swirlix","Slurpuff","Inkay","Malamar","Binacle","Barbaracle",
            "Skrelp","Dragalge","Clauncher","Clawitzer","Helioptile","Heliolisk","Tyrunt","Tyrantrum","Amaura","Aurorus","Sylveon","Hawlucha",
            "Dedenne","Carbink","Goomy","Sliggoo","Goodra","Klefki","Phantump","Trevenant","Pumpkaboo","Gourgeist","Bergmite","Avalugg",
            "Noibat","Noivern","Xerneas","Yveltal","Zygarde","Diancie" };
    // endregion

}

