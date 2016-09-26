package Model.Data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

public class UrlToByteArrayConvertor {

    public interface ConversionInterface {
        // null for failure
        void done(byte[] byteData);
    }

    private static final String Convertor_TAG = "Convertor_TAG";

    public static void convert(final String url, final ConversionInterface listener) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    if (url != null) {
                        final Bitmap bitmap = loadImageFromUrl(url);
                        ByteArrayOutputStream blob = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.PNG, 0 /*ignored for PNG*/, blob);
                        listener.done(blob.toByteArray());
                    } else {
                        Log.d(Convertor_TAG, "Url is empty");
                        listener.done(null);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    Log.d(Convertor_TAG, "Failed to convert");
                    listener.done(null);
                }
            }
        }).start();
    }

    private static Bitmap loadImageFromUrl(String url) {
        Bitmap bm;
        try {

            URL aURL = new URL(url);
            URLConnection conn = aURL.openConnection();

            conn.connect();
            InputStream is = conn.getInputStream();

            BufferedInputStream bis = new BufferedInputStream(is);
            bm = BitmapFactory.decodeStream(bis);

            bis.close();
            is.close();

        } catch (Exception e) {
            return null;
        }

        return Bitmap.createScaledBitmap(bm, 50, 50, true);
    }
}
