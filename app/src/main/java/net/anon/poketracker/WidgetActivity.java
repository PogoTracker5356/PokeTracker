package net.anon.poketracker;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.*;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.TextView;

import java.security.Provider;

import javax.microedition.khronos.opengles.GL;

public class WidgetActivity extends AppWidgetProvider {
    SharedPreferences wpref;
    RemoteViews remote;
    double user_lat;
    double user_lon;
    public final static int BADLOC = -1000;
    public static int NEVER = 0;
    public static int ALWAYS = 1;
    public static int WHEN_LOC_UNKNOWN = 2;
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d("PK-WIDGET", "onUpdate");
        // Get saved data
        wpref = PreferenceManager.getDefaultSharedPreferences(context);
        long tracking_enc = wpref.getLong("highlight_enc", -1);
        double pk_lat = wpref.getFloat("pk_latitude", -1);
        double pk_lon = wpref.getFloat("pk_longitude", -1);
        int pk_id =  wpref.getInt("pk_id", 0);
        String pk_type = wpref.getString("pk_type", "??");
        long pk_despawn = wpref.getLong("pk_despawn", -1);
        String field = wpref.getString("pk_field", "??");
        // Location
        try {
            LocationManager aLocationManager = (LocationManager) context.getSystemService(android.content.Context.LOCATION_SERVICE);
            Criteria aCriteria = new android.location.Criteria();
            String aProvider = aLocationManager.getBestProvider(aCriteria, false);
            Location aLocation = aLocationManager.getLastKnownLocation(aProvider);
            user_lat = aLocation.getLatitude();
            user_lon = aLocation.getLongitude();
        } catch (SecurityException se) { TellUser("ERROR: No location"); }
        // Register an onClickListener
        Intent intent = new Intent(context, WidgetActivity.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        remote = new RemoteViews(context.getPackageName(), R.layout.activity_widget);
        remote.setOnClickPendingIntent(R.id.w_widget, pendingIntent);
        ComponentName thisWidget = new ComponentName(context, WidgetActivity.class);
        int[] allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
        // Calculations
        float[] distanceArr = new float[3];
        Location.distanceBetween(user_lat, user_lon, pk_lat, pk_lon, distanceArr);
        GlobalVars.PData pokemon = new GlobalVars.PData(tracking_enc, "x", pk_lat, pk_lon, pk_despawn, pk_id);
        pokemon.calcDistance(user_lat, user_lon);
        String distanceText = GlobalVars.getDistanceText(pokemon, GlobalVars.getPrefDistance(context));
        String directionText = GlobalVars.getDirectionText(pokemon, GlobalVars.getPrefShowDir(context), GlobalVars.getPrefDirection(context), field);
        // Update fields in the layout
        remote.setTextViewText(R.id.w_distance, distanceText);
        remote.setTextViewText(R.id.w_direction, directionText);
        CountdownView countdown = new CountdownView(context);
        countdown.setData(pokemon.despawn, Color.rgb(0, 210, 210));
        Bitmap bmp = Bitmap.createBitmap(GlobalVars.dpToPx(50), GlobalVars.dpToPx(50), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        if (countdown.time_remaining > 0) countdown.drawCountdown(canvas, countdown.time_remaining);
        remote.setImageViewBitmap(R.id.pk_despawn, bmp);
        int resID = context.getResources().getIdentifier("p" + GlobalVars.getIDFromName(pk_type), "drawable", context.getPackageName());
        remote.setImageViewResource(R.id.w_icon, resID);
        for (int widgetId : allWidgetIds)
        {
            appWidgetManager.updateAppWidget(widgetId, remote);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        Log.d("PK-WIDGET", "onDeleted");
    }
    @Override
    public void onDisabled(Context context) {
        Log.d("PK-WIDGET", "onDisabled");
    }
    @Override
    public void onEnabled(Context context) {
        Log.d("PK-WIDGET", "onEnabled");
    }
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
    }

    public void TellUser(String s)
    {
        remote.setTextViewText(R.id.w_feedback, s);
    }
    /*public GlobalVars g()
    {
        return (GlobalVars)GlobalVars.getContext();
    }*/
}