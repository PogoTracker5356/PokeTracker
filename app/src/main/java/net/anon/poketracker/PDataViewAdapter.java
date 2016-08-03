package net.anon.poketracker;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class PDataViewAdapter extends BaseAdapter
{
    private Context mContext;
    View parent;
    public PDataViewAdapter(Context c, View v) {
        //plist = items;
        mContext = c;
        parent = v;
    }

    public View getView(int position, View convertView, final ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.fragment_pdata, parent, false);
        }
        // Update fields in the layout
        GlobalVars.PData pokemon = g().getPokemonData(position);
        ((TextView)convertView.findViewById(R.id.pk_id)).setText(pokemon.pokID + "");
        ((TextView)convertView.findViewById(R.id.pk_enc_id)).setText(pokemon.encID + "");
        ((TextView)convertView.findViewById(R.id.pk_type)).setText(pokemon.pokType);
        String distanceText = g().getDistanceText(pokemon, g().getPrefDistance(mContext));
        String directionText = g().getDirectionText(pokemon, g().getPrefShowDir(mContext), g().getPrefDirection(mContext), pokemon.direction);
        ((TextView)convertView.findViewById(R.id.pk_distance)).setText(distanceText);
        ((TextView)convertView.findViewById(R.id.pk_direction)).setText(directionText);
        int colour = (pokemon.encID == g().highlight_enc ? Color.rgb(0, 210, 210) : Color.rgb(105, 255, 255));
        convertView.setBackgroundColor(colour);
        CountdownView countdown = (CountdownView)convertView.findViewById(R.id.pk_despawn);
        countdown.setData(pokemon.despawn, colour);
        ((TextView)convertView.findViewById(R.id.pk_despawn_text)).setText(countdown.getDespawn());
        // Schedule the image update (since this can take a while)
        final View imgView = convertView;
        final long pokID = pokemon.pokID;
        Runnable setImg = new Runnable() {
            @Override
            public void run() {
                ImageView img = (ImageView)imgView.findViewById(R.id.pk_icon);
                if (pokID == -1) img.setVisibility(ImageView.INVISIBLE);
                else
                {
                    img.setVisibility(ImageView.VISIBLE);
                    img.setImageDrawable(g().getDrawableFromString("p" + pokID));
                }
            }
        };
        Handler h = new Handler();
        h.postDelayed(setImg, 100);
        return convertView;
    }

    // Require for structure, not used.
    public Object getItem(int position) {
        return null;
    }
    public long getItemId(int position) {
        return position;
    }

    public int getCount() {
        return g().getPokemonListSize();
    }

    @Override
    public String toString() {
        return super.toString();// + " '" + mContentView.getText() + "'";
    }
    public GlobalVars g()
    {
        return (GlobalVars)GlobalVars.getContext();
    }

}
