package com.nova.ai.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ImageLoader {

    public static Bitmap load(String path, int targetWidth) {
        if (path == null || path.isEmpty()) return null;
        File f = new File(path);
        if (!f.exists()) return null;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, opts);

        int sample = 1;
        while (opts.outWidth / sample > targetWidth * 2) sample *= 2;

        opts.inJustDecodeBounds = false;
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(path, opts);
    }

    public static String toDataUrl(String path, int targetMaxDim) {
        if (path == null || path.isEmpty()) return null;
        File f = new File(path);
        if (!f.exists()) return null;

        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, opts);

            int sample = 1;
            int maxDim = Math.max(opts.outWidth, opts.outHeight);
            while (maxDim / sample > targetMaxDim) sample *= 2;

            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeFile(path, opts);
            if (bmp == null) return null;

            String format = inferFormat(path);
            Bitmap.CompressFormat cf;
            if ("png".equals(format)) {
                cf = Bitmap.CompressFormat.PNG;
            } else {
                cf = Bitmap.CompressFormat.JPEG;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(cf, 85, baos);
            bmp.recycle();
            String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
            return "data:image/" + format + ";base64," + b64;
        } catch (Exception e) {
            return null;
        }
    }

    private static String inferFormat(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".png")) return "png";
        if (lower.endsWith(".webp")) return "webp";
        if (lower.endsWith(".gif")) return "gif";
        return "jpeg";
    }
}
