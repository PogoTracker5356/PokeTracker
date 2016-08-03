package net.anon.poketracker;

import android.os.HandlerThread;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;

import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.map.MapObjects;
import com.pokegoapi.api.map.pokemon.NearbyPokemon;
import com.pokegoapi.auth.PtcLogin;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import POGOProtos.Map.Pokemon.WildPokemonOuterClass;
import POGOProtos.Networking.Envelopes.RequestEnvelopeOuterClass.RequestEnvelope.AuthInfo;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static com.pokegoapi.auth.PtcLogin.CLIENT_ID;
import static com.pokegoapi.auth.PtcLogin.CLIENT_SECRET;
import static com.pokegoapi.auth.PtcLogin.LOGIN_OAUTH;
import static com.pokegoapi.auth.PtcLogin.LOGIN_URL;
import static com.pokegoapi.auth.PtcLogin.REDIRECT_URI;

/**
 * Created by vanshilshah on 20/07/16.
 */
public class NianticManager {
    private static final String TAG = "NianticManager";

    private static final String BASE_URL = "https://sso.pokemon.com/sso/";

    private static final NianticManager instance = new NianticManager();

    private Handler mHandler;
    private AuthInfo mAuthInfo;
    private NianticService mNianticService;
    private final OkHttpClient mClient;
    private final OkHttpClient mPoGoClient;

    public static NianticManager getInstance(){
        return instance;
    }

    private NianticManager(){
        mPoGoClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        HandlerThread thread = new HandlerThread("Niantic Manager Thread");
        thread.start();
        mHandler = new Handler(thread.getLooper());

                  /*
		This is a temporary, in-memory cookie jar.
		We don't require any persistence outside of the scope of the login,
		so it being discarded is completely fine
		*/
        CookieJar tempJar = new CookieJar() {
            private final HashMap<String, List<Cookie>> cookieStore = new HashMap<String, List<Cookie>>();

            @Override
            public void saveFromResponse(okhttp3.HttpUrl url, List<Cookie> cookies) {
                cookieStore.put(url.host(), cookies);
            }

            @Override
            public List<Cookie> loadForRequest(okhttp3.HttpUrl url) {
                List<Cookie> cookies = cookieStore.get(url.host());
                return cookies != null ? cookies : new ArrayList<Cookie>();
            }
        };

        Gson gson = new GsonBuilder()
                .setLenient()
                .create();

        mClient = new OkHttpClient.Builder()
                .cookieJar(tempJar)
                .addInterceptor(new NetworkRequestLoggingInterceptor())
                .build();

        mNianticService = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(mClient)
                .build()
                .create(NianticService.class);
    }

    public void login(final String username, final String password, final LoginListener loginListener){
        Callback<NianticService.LoginValues> valuesCallback = new Callback<NianticService.LoginValues>() {
            @Override
            public void onResponse(Call<NianticService.LoginValues> call, Response<NianticService.LoginValues> response) {
                if(response.body() != null) {
                    loginPTC(username, password, response.body(), loginListener);
                }else{
                    loginListener.authFailed("Fetching Pokemon Trainer Club's Login Url Values Failed");
                }

            }

            @Override
            public void onFailure(Call<NianticService.LoginValues> call, Throwable t) {
                loginListener.authFailed("Fetching Pokemon Trainer Club's Login Url Values Failed");
            }
        };
        Call<NianticService.LoginValues> call = mNianticService.getLoginValues();
        call.enqueue(valuesCallback);
    }

    private void loginPTC(final String username, final String password, NianticService.LoginValues values, final LoginListener loginListener){
        HttpUrl url = HttpUrl.parse(LOGIN_URL).newBuilder()
                .addQueryParameter("lt", values.getLt())
                .addQueryParameter("execution", values.getExecution())
                .addQueryParameter("_eventId", "submit")
                .addQueryParameter("username", username)
                .addQueryParameter("password", password)
                .build();

        OkHttpClient client = mClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build();

        NianticService service = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()
                .create(NianticService.class);

        Callback<NianticService.LoginResponse> loginCallback = new Callback<NianticService.LoginResponse>() {
            @Override
            public void onResponse(Call<NianticService.LoginResponse> call, Response<NianticService.LoginResponse> response) {
                String location = response.headers().get("location");
                if (location != null) {
                    String ticket = location.split("ticket=")[1];
                    requestToken(ticket, loginListener);
                } else {
                    loginListener.authFailed("Pokemon Trainer Club Login Failed");
                }
            }

            @Override
            public void onFailure(Call<NianticService.LoginResponse> call, Throwable t) {
                loginListener.authFailed("Pokemon Trainer Club Login Failed");
            }
        };
        Call<NianticService.LoginResponse> call = service.login(url.toString());
        call.enqueue(loginCallback);
    }

    private void requestToken(String code, final LoginListener loginListener){
        Log.d(TAG, "requestToken() called with: code = [" + code + "]");
        HttpUrl url = HttpUrl.parse(LOGIN_OAUTH).newBuilder()
                .addQueryParameter("client_id", CLIENT_ID)
                .addQueryParameter("redirect_uri", REDIRECT_URI)
                .addQueryParameter("client_secret", CLIENT_SECRET)
                .addQueryParameter("grant_type", "refresh_token")
                .addQueryParameter("code", code)
                .build();

        Callback<ResponseBody> authCallback = new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String token = response.body().string().split("token=")[1];

                    if (token != null) {
                        token = token.split("&")[0];

                        loginListener.authSuccessful(token);
                    } else {
                        loginListener.authFailed("Pokemon Trainer Club Login Failed");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    loginListener.authFailed("Pokemon Trainer Club Authentication Failed");
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                t.printStackTrace();
                loginListener.authFailed("Pokemon Trainer Club Authentication Failed");
            }
        };
        Call<ResponseBody> call = mNianticService.requestToken(url.toString());
        call.enqueue(authCallback);
    }

    public interface LoginListener {
        void authSuccessful(String authToken);
        void authFailed(String message);
    }

    public void login(@NonNull final String username, @NonNull final String password) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.d("PK-LOG", "Trying.");
                try {
                    mAuthInfo = new PtcLogin(mPoGoClient).login(username, password);
                    if (mAuthInfo == null) {
                        g().loginFail = true;
                        Log.d("PK-LOG", "Failed to log in (auth info null).");
                    }
                    else {
                        Log.d("PK-LOG", "mPokemonGo");
                        g().mPokemonGo = new PokemonGo(mAuthInfo, mPoGoClient);
                        if (g().mPokemonGo == null) g().loginFail = true;
                        Log.d("PK-LOG", "mPokemonGo =" + g().mPokemonGo);
                    }
                } catch (NullPointerException|LoginFailedException|RemoteServerException e) {
                    Log.d("PK-LOG", "Failed to log in.", e);
                    g().loginFail = true;
                }
                catch (Exception e) {
                    Log.d("PK-LOG", "Failed to log in.", e);
                    g().loginFail = true;
                }
                Log.d("PK-LOG", "Tried.");
                if (g().loginFail) g().handler.postDelayed(g().outputLoginTask, 100);
            }
        });
    }

    public void getCatchablePokemon(final double latitude, final double longitude, final double alt)
    {
        g().currentlyScanning = true;
        Log.d("PK-REMOVES", "set currentlyScanning to true");
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.d("PK-FIND", "Tried getCatchablePokemon");
                if (g().mPokemonGo != null) {

                    Log.d("PK-FIND", "mPokemonGo wasn't null, at least");
                    Log.d("PK-FIND", "Current loc: " + latitude + " " + longitude);

                    g().locationArray = new ArrayList<android.location.Location>();
                    fillLocationArray(latitude, longitude);

                    g().removeNearby();
                    int successes = 0;

                    g().lastScannedField = -1;
                    g().lastReport = -1;
                    int i = -1; // Center location is -1 so that it's ignored while estimating direction

                    g().handler.postDelayed(g().outputScanTask, 100);

                    for (android.location.Location l : g().locationArray) {
                        try {
                            g().mPokemonGo.setLocation(l.getLatitude(), l.getLongitude(), alt);
                            com.pokegoapi.api.map.Map map = g().mPokemonGo.getMap();
                            MapObjects mapObj = map.getMapObjects();


                            Collection<WildPokemonOuterClass.WildPokemon> wild = mapObj.getWildPokemons();

                            //This was making a secondary, technically avoidable call to getMapObjects, which is now throttled at server-side:
                            //Collection<NearbyPokemon> nearby = g().mPokemonGo.getMap().getNearbyPokemon();

                            //This doesn't make an extra call, but it returns some kind of weird outer class proto array thing:
                            Collection<POGOProtos.Map.Pokemon.NearbyPokemonOuterClass.NearbyPokemon> protoNearby = mapObj.getNearbyPokemons();
                            //This turns it into the useful NearbyPokemon object:
                            Collection<NearbyPokemon> nearby = getNearbyPokemon(protoNearby);

                            for (NearbyPokemon pk : nearby) {
                                double lat = g().BADLOC;
                                double lon = g().BADLOC;
                                long despawn = -1;
                                long pkid = -1;
                                for (WildPokemonOuterClass.WildPokemon w : wild) {
                                    if (pk.getEncounterId() == w.getEncounterId()) {
                                        lat = w.getLatitude();
                                        lon = w.getLongitude();
                                        pkid = w.getPokemonData().getPokemonIdValue();
                                        Log.d("PK-DESPAWN", "Check it, despawn is " + w.getTimeTillHiddenMs());
                                        despawn = System.currentTimeMillis() + w.getTimeTillHiddenMs();
                                    }
                                }
                                Log.d("PK-POKES", "Attempting to add " + pk.getPokemonId() + " at " + lat + "," + lon + " from field " + i);
                                g().addPokemon(pk.getEncounterId(), pk.getPokemonId().toString(), lat, lon, despawn, pkid, i);
                            }
                            g().successArray[i + 1] = true;
                            Log.d("PK-RETRY", "succeeded scan of field " + i);
                            successes += 1;
                        } catch (Exception e) {
                            Log.d("PK-RETRY", "failed scan of field " + i);
                            Log.d("PK-POKES", "Exception while adding pokemon", e);
                        }
                        g().lastScannedField = i;
                        i = i + 1;
                        if (i < g().locationArray.size() - 1) {
                            try {
                                Thread.sleep(g().scan_sleep * 1000 + 100); // Add a handful of milliseconds just to be safe
                            } catch (Exception e) {
                                Log.d("PK-SLEEP", "I don't know, man.");
                            }
                        }
                    }
                    g().currentlyScanning = false;
                    Log.d("PK-REMOVES", "set currentlyScanning to false");
                    g().scanned = successes;
                    g().logPokemonList();
                }
                else
                {
                    /*long now = System.currentTimeMillis();
                    g().addPokemon(101, "x WEEDLE", latitude, longitude + 0.001, now + 240000, 13, 0);
                    g().addPokemon(102, "x DIGLETT", latitude - 0.003, longitude, now + 500000, 50, 1);
                    g().addPokemon(100, "x RATTATA", GlobalVars.BADLOC, GlobalVars.BADLOC, now + 900000, 19, 2);
                    g().scanned = 7;
                    g().logPokemonList();
                    g().loggedIn = true;
                    g().active = true;*/
                }
            }
        });
    }


    public void fillLocationArray(double sLat, double sLong) {
        //Create a baseline android location and location manager
        android.location.Location coreLoc = new android.location.Location(g().aProvider);
        coreLoc.setLatitude(sLat);
        coreLoc.setLongitude(sLong);
        g().locationArray.add(coreLoc);

        //Based on complete "close" coverage, hexes, circles, six scans... there's some trig invovled.
        //distance of 121 assumes precise scan radius is 70
        int distanceToSecondary = 121;

        int i = 0;
        while(i < 6) {
            android.location.Location tLoc = calculateLocation(coreLoc, i * 60, distanceToSecondary /* Radius from location to secondary scans (hex shape) */);
            g().locationArray.add(tLoc);
            i = i + 1;
        }
    }

    public android.location.Location calculateLocation(android.location.Location startPoint, int bearing, int meters) {
        android.location.Location newLocation = new android.location.Location(g().aProvider);

        double startLat = Math.toRadians(startPoint.getLatitude());
        double startLong = Math.toRadians(startPoint.getLongitude());
        double rBearing = Math.toRadians(bearing);

        double aD = ((double)meters/6371000.0);

        double newLat = Math.asin((Math.sin(startLat) * Math.cos(aD) + (Math.cos(startLat) * Math.sin(aD) * Math.cos(rBearing)) ));
        double newLong = startLong + Math.atan2(Math.sin(rBearing) * Math.sin(aD) * Math.cos(startLat), Math.cos(aD) - (Math.sin(startLat) * Math.sin(newLat)));

        double dLat = Math.toDegrees(newLat);
        double dLong = Math.toDegrees(newLong);

        newLocation.setLatitude(dLat);
        newLocation.setLongitude(dLong);

        return newLocation;
    }
    public void resetSuccessArray() {
        int i = 0;
        while(i < 7) {
            g().successArray[i] = false;
            i = i + 1;
        }
    }

    public GlobalVars g()
    {
        return (GlobalVars)GlobalVars.getContext();
    }

    //This shouldn't have been making additional calls to getMapObjects, so I'm not using the pre-compiled version.
    public Collection<NearbyPokemon> getNearbyPokemon(Collection<POGOProtos.Map.Pokemon.NearbyPokemonOuterClass.NearbyPokemon> outer) {
        Collection<NearbyPokemon> rv = new ArrayList<NearbyPokemon>();

        for (POGOProtos.Map.Pokemon.NearbyPokemonOuterClass.NearbyPokemon proto : outer) {
            rv.add(new NearbyPokemon(proto));
        }

        return rv;
    }


}