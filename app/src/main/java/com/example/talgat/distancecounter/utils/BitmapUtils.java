package com.example.talgat.distancecounter.utils;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;

public class BitmapUtils {

    public static Bitmap createSmallMarkersIcon(int iconRes, Activity activity) {
        int height = 100;
        int width = 100;
        BitmapDrawable bitmapdraw=(BitmapDrawable)activity.getResources().getDrawable(iconRes);
        Bitmap b=bitmapdraw.getBitmap();
        return Bitmap.createScaledBitmap(b, width, height, false);
    }
}
