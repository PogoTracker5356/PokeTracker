package net.anon.poketracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;

/**
 * Created by Owner on 2016-08-01.
 */

public class CountdownView extends View {
    long time_remaining = -1;
    int bgColour;
    public CountdownView(Context c, AttributeSet attrs)
    {
        super(c, attrs);
    }
    public CountdownView(Context c)
    {
        super(c);
    }
    public void setData(long despawn, int colour)
    {
        bgColour = colour;
        if (despawn > 0) {
            time_remaining = (despawn - System.currentTimeMillis()) / 1000;
        } else {
            time_remaining = -1;
        }
        //Log.d("PK-DESPAWN", "Setting time_remaining to " + time_remaining);
        invalidate();
    }
    public String getDespawn()
    {
        String textDespawn = (time_remaining / 60) + ":" + g().padDigits((int)(time_remaining % 60), 2);
        //Log.d("PK-DESPAWN", "Despawn text: " + textDespawn);
        return (time_remaining > 0 ? textDespawn : "");
    }
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (time_remaining > 0) {
            drawCountdown(canvas, time_remaining);
        }
    }
    private void drawLine(Path path, int x, int y)
    {
        path.lineTo(x, y);
    }
    public void drawCountdown(Canvas c, long time_remaining)
    {
        int SIZE = c.getWidth(); // dpToPx(20)
        int MAX_COUNTDOWN = 15 * 60;
        int degrees = 360 - (int)(time_remaining * 360 / MAX_COUNTDOWN);
        int left = 0;
        int top = 0;
        int right = SIZE;
        int bottom = SIZE;
        int mid_x = left + (right - left) / 2;
        int mid_y = top + (bottom - top) / 2;

        int colour = Color.rgb(55, 255, 0); // Green
        if (time_remaining < 150) colour = Color.rgb(183, 0, 0); // 2.5 minutes, red
        else if (time_remaining < 300) colour = Color.rgb(255, 160, 0); // 5 minutes, dark orange
        else if (time_remaining < 600) colour = Color.rgb(230, 225, 0); // 10 minutes, yellow

        // Draw a circle with a border - the next part will fill in the "unused" portion
        Paint p = new Paint();
        //p.setStrokeWidth(5);
        //p.setStyle(Paint.Style.STROKE);
        //p.setColor(Color.BLACK);
        //c.drawCircle(mid_x, mid_y, SIZE / 2, p);
        p.setStrokeWidth(1);
        p.setColor(colour);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(left + mid_x, top + mid_y, SIZE / 2, p);

        Path path = new Path();
        path.setFillType(Path.FillType.EVEN_ODD);

        path.moveTo(mid_x, mid_y);
        drawLine(path, mid_x, top);
        if (degrees >= 45) drawLine(path, left, top);
        if (degrees >= 135) drawLine(path, left, bottom);
        if (degrees >= 225) drawLine(path, right, bottom);
        if (degrees >= 315) drawLine(path, right, top);
        // The final point is based on degrees
        // Portion is the number of pixels along the incomplete side
        int portion = ((degrees+45) % 90) * (right-left) / 90;
        int final_x, final_y;
        if (45 < degrees && degrees < 135) final_x = left;
        else if (135 < degrees && degrees < 225) final_x = left + portion;
        else if (225 < degrees && degrees < 315) final_x = right;
        else final_x = right - portion;
        if (45 < degrees && degrees < 135) final_y = top + portion;
        else if (135 < degrees && degrees < 225) final_y = bottom;
        else if (225 < degrees && degrees < 315) final_y = bottom - portion;
        else final_y = top;
        if (portion > 0) path.lineTo(final_x, final_y);
        drawLine(path, mid_x, mid_y);
        path.close();

        p.setColor(bgColour);
        c.drawPath(path, p);

        //String textDespawn = (time_remaining / 60) + ":" + (time_remaining % 60);
        //Log.d("PK-DESPAWN", "Despawn text: " + textDespawn);
        //p.setColor(Color.BLACK);
        //p.setTextSize(10);//SIZE);
        //c.drawText(textDespawn, left, top, p);
    }
    public GlobalVars g()
    {
        return (GlobalVars)GlobalVars.getContext();
    }
}