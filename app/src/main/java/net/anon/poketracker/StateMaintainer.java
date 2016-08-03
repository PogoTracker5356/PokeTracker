package net.anon.poketracker;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;

public class StateMaintainer {

    public static void clear(Bundle savedInstanceState) {
        savedInstanceState.clear();
    }

    public static void savePokemon(Bundle state, ArrayList<GlobalVars.PData> plist){
        int i = 0;
        for(GlobalVars.PData pokemon : plist) {
            String baseKey = "pokemon" + i;
            String encIdKey = baseKey + "encID";
            String pokIdKey = baseKey + "pokID";
            String pokeTypeKey = baseKey + "pokType";
            String latitudeKey = baseKey + "latitude";
            String longitudeKey = baseKey + "longitude";
            String distanceKey = baseKey + "distance";
            String bearingKey = baseKey + "bearing";
            String directionKey = baseKey + "direction";
            String despawnKey = baseKey + "despawn";

            String nKey = baseKey + "n";
            String neKey = baseKey + "ne";
            String seKey = baseKey + "se";
            String sKey = baseKey + "s";
            String swKey = baseKey + "sw";
            String nwKey = baseKey + "nw";

            state.putLong(encIdKey, pokemon.encID);
            state.putLong(pokIdKey, pokemon.pokID);
            state.putString(pokeTypeKey, pokemon.pokType);
            state.putDouble(latitudeKey, pokemon.latitude);
            state.putDouble(longitudeKey, pokemon.longitude);
            state.putFloat(distanceKey, pokemon.distance);
            state.putFloat(bearingKey, pokemon.bearing);
            state.putString(directionKey, pokemon.direction);
            state.putLong(despawnKey, pokemon.despawn);

            state.putBoolean(nKey, pokemon.n);
            state.putBoolean(neKey, pokemon.ne);
            state.putBoolean(seKey, pokemon.se);
            state.putBoolean(sKey, pokemon.s);
            state.putBoolean(swKey, pokemon.sw);
            state.putBoolean(nwKey, pokemon.nw);

            i = i + 1;
        }
    }

    public static void saveCaught(Bundle state, ArrayList<GlobalVars.PData> caughtList){
        int i = 0;
        for(GlobalVars.PData caught : caughtList) {
            String baseKey = "caught" + i;
            String encIDKey = baseKey + "encID";
            Log.d("PK-SAVE", "Saving caught " + caught.encID);
            state.putLong(encIDKey, caught.encID);
            i = i + 1;
        }
    }

    public static void saveActive(Bundle state, boolean active) {
        state.putBoolean("active", active);
    }

    public static void loadPokemon(Bundle state) {
        Log.d("PK-SAVE", "loadPokemon");
        boolean gotPokemon = true;
        try {
            int i = 0;
            while(gotPokemon) {

                Log.d("PK-SAVE", "Loading pokemon " + i);
                String baseKey = "pokemon" + i;
                String encIDKey = baseKey + "encID";
                String pokIDKey = baseKey + "pokID";
                String pokeTypeKey = baseKey + "pokType";
                String latitudeKey = baseKey + "latitude";
                String longitudeKey = baseKey + "longitude";
                String distanceKey = baseKey + "distance";
                String bearingKey = baseKey + "bearing";
                String directionKey = baseKey + "direction";
                String despawnKey = baseKey + "despawn";

                String nKey = baseKey + "n";
                String neKey = baseKey + "ne";
                String seKey = baseKey + "se";
                String sKey = baseKey + "s";
                String swKey = baseKey + "sw";
                String nwKey = baseKey + "nw";

                long encID = state.getLong(encIDKey);
                long pokID = state.getLong(pokIDKey);
                String type = state.getString(pokeTypeKey);
                double latitude = state.getDouble(latitudeKey);
                double longitude = state.getDouble(longitudeKey);
                float distance = state.getFloat(distanceKey);
                float bearing = state.getFloat(bearingKey);
                String direction = state.getString(directionKey);
                long despawn = state.getLong(despawnKey);

                Boolean n = state.getBoolean(nKey);
                Boolean ne = state.getBoolean(neKey);
                Boolean se = state.getBoolean(seKey);
                Boolean s = state.getBoolean(sKey);
                Boolean sw = state.getBoolean(swKey);
                Boolean nw = state.getBoolean(nwKey);

                GlobalVars.PData pokemon = new GlobalVars.PData(encID, type, latitude, longitude, despawn, pokID);
                if(encID != (long)0 && type != null){
                    pokemon.distance = distance;
                    pokemon.bearing = bearing;
                    pokemon.direction = direction;
                    pokemon.despawn = despawn;
                    pokemon.n = n;
                    pokemon.ne = ne;
                    pokemon.se = se;
                    pokemon.s = s;
                    pokemon.sw = sw;
                    pokemon.nw = nw;
                    g().addPokemon(pokemon);
                }
                else {
                    gotPokemon = false;
                }
                i = i + 1;
            }
        }
        catch (Exception e) {
            //don't actually do anything for now.  If an error occurs, that's when it's time to stop.
            Log.d("PK-SAVE", "Whoops! guess load is done");
        }

    }

    public static void loadCaught(Bundle state) {
        Boolean remaining = true;
        int i = 0;
        while(remaining) {
            String baseKey = "caught" + i;
            String encIDKey = baseKey + "encID";

            long encID = state.getLong(encIDKey);
            if(encID != (long)0) {
                Log.d("PK-SAVE", "YUP");
                GlobalVars.PData caught = new GlobalVars.PData(encID, "", 0.0, 0.0, -1, 0); // Only enc matters here because equals() has been overridden
                g().caughtList.add(caught);
            }
            else {
                Log.d("PK-SAVE", "NOPE");
                remaining = false;
            }
            i = i + 1;
        }
    }

    public static void loadActive(Bundle state) {
        boolean active = state.getBoolean("active");
        g().active = active;
    }

    public static GlobalVars g()
    {
        //return ((GlobalVars)this.getApplication());
        return (GlobalVars)GlobalVars.getContext();
        //return ((GlobalVars)(((Activity)GlobalVars.getContext()).getApplication()));
        //return (GlobalVars)GlobalVars.this;
    }

}