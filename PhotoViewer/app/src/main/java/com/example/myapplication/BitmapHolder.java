package com.example.myapplication;

import android.graphics.Bitmap;

public class BitmapHolder {
    private static Bitmap bitmap;
    public static void set(Bitmap b) { bitmap = b; }
    public static Bitmap get() { return bitmap; }
    public static void clear() { bitmap = null; }
}
