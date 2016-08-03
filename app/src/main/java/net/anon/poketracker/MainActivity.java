package net.anon.poketracker;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

//locationPermission
import android.support.v4.content.ContextCompat;


public class MainActivity extends Activity {
    private static final String TAG = "Pokemap";

    ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        int permissionCheck = ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION);
        if(permissionCheck == PackageManager.PERMISSION_GRANTED) {
            Log.d("PK-PERM", "The app has location permission");
        }
        else{
            //This is completely useless without location permission
            Log.d("PK-PERM", "no permissions");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 4);

        }

        super.onCreate(savedInstanceState);
        g().mainActivity = findViewById(android.R.id.content);  //this.findViewById(R.id.main_view);
        g().init();
        setContentView(R.layout.activity_main);

        // Check whether we're recreating a previously destroyed instance
        if (savedInstanceState != null) {
            Log.d("PK-SAVE", "Attempting a load");
            StateMaintainer.loadActive(savedInstanceState);
            StateMaintainer.loadPokemon(savedInstanceState);
            StateMaintainer.loadCaught(savedInstanceState);

        } else {
            // Probably initialize members with default values for a new instance
            Log.d("PK-SAVE", "there was NOT a save");


        }

        ((EditText)findViewById(R.id.lg_username)).setText(g().pref.getString("username", "DummyAccount"));
        ((EditText)findViewById(R.id.lg_password)).setText(g().pref.getString("password", "Password"));

        createDirectionDropdown(R.id.pk_dir_spinner, R.array.direction_array);
        g().showDirection = g().pref.getInt("show_direction", 0);
        g().preciseDirection = g().pref.getBoolean("precise_direction", false);
        g().preciseDistance = g().pref.getBoolean("precise_distance", false);
        g().scan_sleep = g().pref.getInt("scan_sleep", 10);
        ((Spinner)findViewById(R.id.pk_dir_spinner)).setSelection(g().showDirection);
        ((CheckBox)findViewById(R.id.pk_dir_type)).setChecked(g().preciseDirection);
        ((CheckBox)findViewById(R.id.pk_dist_type)).setChecked(g().preciseDistance);
        ((EditText)findViewById(R.id.pk_sleep)).setText("" + g().scan_sleep);
        Spinner.OnItemSelectedListener dirSpinner = new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                g().showDirection = pos;
                SharedPreferences.Editor pref_editor = g().pref.edit();
                pref_editor.putInt("show_direction", pos);
                pref_editor.commit();
                Log.d("PREF", "toggled show direction to " + g().showDirection);
                g().refreshPokemonList();
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {}
        };
        ((Spinner)findViewById(R.id.pk_dir_spinner)).setOnItemSelectedListener(dirSpinner);
        ((TextView)findViewById(R.id.pk_logger)).setMovementMethod(new ScrollingMovementMethod());
        Log.d("PK-SCROLL", "Adding scroll bar.");

        listView = (ListView)findViewById(R.id.pk_list_view);
        //listView.setOnClickListener();
        g().pokemonListAdapter = new PDataViewAdapter(this.getBaseContext(), (View)listView.getParent());
        listView.setAdapter(g().pokemonListAdapter);
        g().refreshPokemonList();
    }

    @Override
    public void onResume(){
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    //region Menu Methods

    @Override
    public void onBackPressed() {
        this.finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // TODO: test all this shit on a 6.0+ phone lmfao
        switch (requestCode) {
            case 703:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "permission granted");
                }
                break;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save the user's current game state
        StateMaintainer.clear(savedInstanceState);
        StateMaintainer.savePokemon(savedInstanceState, g().plist);
        StateMaintainer.saveActive(savedInstanceState, g().active);
        StateMaintainer.saveCaught(savedInstanceState, g().caughtList);

        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

    public void hideSignin()
    {

    }

    public void snapshot(View view)
    {
        //g().taskSeconds = 0;
        g().snapshotLocation();
    }
    public void signin(View v)
    {
        Log.d("PK-LOG", "Sign-in");
        g().scheduleLogin((View)v.getParent());
    }
    public void clearPokemon(View v)
    {
        View parent = (View)v.getParent();
        long clicked = Long.parseLong(((TextView)parent.findViewById(R.id.pk_enc_id)).getText() + "");
        g().removePokemon(clicked);

    }
    public void toggleHighlight(View v)
    {
        long clicked = Long.parseLong(((TextView)v.findViewById(R.id.pk_enc_id)).getText() + "");
        if (g().highlight_enc == clicked)
        {
            g().highlight_enc = -1;
        }
        else
        {
            g().highlight_enc = clicked;
            // Save this for the widget to access
            SharedPreferences.Editor pref_editor = g().pref.edit();
            pref_editor.putLong("highlight_enc", g().highlight_enc);
            GlobalVars.PData pokemon = g().getTrackedEncounter();
            pref_editor.putFloat("pk_latitude", (float)pokemon.latitude);
            pref_editor.putFloat("pk_longitude", (float)pokemon.longitude);
            pref_editor.putInt("pk_id", (int)pokemon.pokID);
            pref_editor.putString("pk_type", pokemon.pokType);
            pref_editor.putString("pk_field", pokemon.direction); // Text version of the direction in case only scan field was known
            pref_editor.putLong("pk_despawn", pokemon.despawn);
            pref_editor.commit();
            Log.d("PK-LIST", "Saved: " + g().highlight_enc + " " + (float)pokemon.latitude + "/" + (float)pokemon.longitude + " #" + (int)pokemon.pokID);
        }
        Log.d("PK-LIST", "toggleHighlight " + g().highlight_enc);
        /*try {
            (v.findViewById(R.id.pk_list_view)).invalidate();
        } catch (NullPointerException e) { Log.d("PK-LIST", "Toggle FAIL!", e); }
        catch (Exception e) { Log.d("PK-LIST", "Toggle FAIL!", e); }*/
        g().refreshPokemonList();
    }
    public void toggleDistanceType(View v)
    {
        Log.d("PREF", "toggled distance type to " + g().preciseDistance);
        g().preciseDistance = ((CheckBox)v).isChecked();
        SharedPreferences.Editor pref_editor = g().pref.edit();
        pref_editor.putBoolean("precise_distance", g().preciseDistance);
        pref_editor.commit();
        Log.d("PREF", "toggled");
        g().refreshPokemonList();
    }
    public void toggleDirectionType(View v)
    {
        Log.d("PREF", "toggled direction type to " + g().preciseDirection);
        g().preciseDirection = ((CheckBox)v).isChecked();
        SharedPreferences.Editor pref_editor = g().pref.edit();
        pref_editor.putBoolean("precise_direction", g().preciseDirection);
        pref_editor.commit();
        Log.d("PREF", "toggled");
        g().refreshPokemonList();
    }
    private void createDirectionDropdown(int d_list_id, int d_array)
    {
        Spinner dropdown = (Spinner)findViewById(d_list_id);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                d_array, android.R.layout.simple_spinner_item);
        dropdown.setAdapter(adapter);
    }

    public GlobalVars g()
    {
        return ((GlobalVars)this.getApplication());
        //return (GlobalVars)GlobalVars.getContext();
        //return ((GlobalVars)(((Activity)GlobalVars.getContext()).getApplication()));
        //return (GlobalVars)GlobalVars.this;
    }
}
