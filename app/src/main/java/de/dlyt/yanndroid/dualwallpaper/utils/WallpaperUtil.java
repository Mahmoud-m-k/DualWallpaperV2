package de.dlyt.yanndroid.dualwallpaper.utils;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import de.dlyt.yanndroid.dualwallpaper.Preferences;
import de.dlyt.yanndroid.dualwallpaper.trigger.ThemeTrigger;
import de.dlyt.yanndroid.dualwallpaper.trigger.TimeTrigger;

public class WallpaperUtil {

    public enum WallpaperType {
        HOME_LIGHT(true, true, 0), HOME_DARK(true, false, 1), LOCK_LIGHT(false, true, 2), LOCK_DARK(false, false, 3);
        public final boolean home;
        public final boolean light;
        public final String fileName;
        public final int index;

        WallpaperType(boolean home, boolean light, int index) {
            this.index = index;
            this.home = home;
            this.light = light;
            this.fileName = String.format("/wallpaper_%s_%s", home ? "home" : "lock", light ? "light" : "dark");
        }
    }

    public interface SaveCallback {
        void onSuccess(WallpaperType type);

        void onError(Exception e);
    }

    private Context mContext;
    private WallpaperManager mWallpaperManager;

    public WallpaperUtil(Context context) {
        this.mContext = context;
        this.mWallpaperManager = WallpaperManager.getInstance(context);
    }

    public String getWallpaperTypePath(WallpaperType type) {
        return mContext.getFilesDir().getPath() + type.fileName;
    }

    public void loadWallpapers(boolean darkMode) {
        for (WallpaperType wallpaperType : WallpaperType.values()) {
            if (wallpaperType.light != darkMode) loadWallpaper(wallpaperType);
        }
    }

    private void loadWallpaper(WallpaperType type) {
        try {
            InputStream inputStream = Files.newInputStream(Paths.get(getWallpaperTypePath(type)));
            mWallpaperManager.setStream(inputStream, null, false, type.home ? WallpaperManager.FLAG_SYSTEM : WallpaperManager.FLAG_LOCK);
            //TODO notify system ?
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public interface ImportCallback {
        void onImported(WallpaperType type);

        void onError(Exception e);
    }

    /** Original, uncropped image the user picked. Used to (re)position the wallpaper. */
    public String getOriginalPath(WallpaperType type) {
        return getWallpaperTypePath(type) + "_original";
    }

    public boolean hasOriginal(WallpaperType type) {
        return new File(getOriginalPath(type)).exists();
    }

    /** Deletes the wallpaper, its original and its saved position. */
    public boolean deleteWallpaper(WallpaperType type) {
        new File(getOriginalPath(type)).delete();
        new Preferences(mContext).setCrop(type, null);
        return new File(getWallpaperTypePath(type)).delete();
    }

    /** Copies the current system wallpaper as the original image for this type. */
    public void importFromCurrent(WallpaperType type, ImportCallback callback) {
        try {
            ParcelFileDescriptor fileDescriptor = mWallpaperManager.getWallpaperFile(type.home ? WallpaperManager.FLAG_SYSTEM : WallpaperManager.FLAG_LOCK);
            if (fileDescriptor == null) throw new FileNotFoundException();
            importOriginal(new ParcelFileDescriptor.AutoCloseInputStream(fileDescriptor), type, callback);
        } catch (SecurityException | FileNotFoundException e) {
            e.printStackTrace();
            callback.onError(e);
        }
    }

    /** Copies the image from a picker / share intent as the original image for this type. */
    public void importFromIntent(Intent intent, WallpaperType type, ImportCallback callback) {
        try {
            Uri data = intent == null ? null : intent.getData();
            if (data == null) throw new FileNotFoundException();
            importOriginal(mContext.getContentResolver().openInputStream(data), type, callback);
        } catch (SecurityException | FileNotFoundException e) {
            e.printStackTrace();
            callback.onError(e);
        }
    }

    private void importOriginal(InputStream in, WallpaperType type, ImportCallback callback) {
        Handler handler = new Handler();
        new Thread(() -> {
            File tmp = new File(getOriginalPath(type) + "_tmp");
            try (InputStream input = in; FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) out.write(buffer, 0, read);
            } catch (IOException e) {
                e.printStackTrace();
                tmp.delete();
                handler.post(() -> callback.onError(e));
                return;
            }
            if (BitmapFactory.decodeFile(tmp.getPath(), boundsOptions()).outWidth <= 0) {
                tmp.delete();
                handler.post(() -> callback.onError(new IOException("Not an image")));
                return;
            }
            File original = new File(getOriginalPath(type));
            original.delete();
            tmp.renameTo(original);
            new Preferences(mContext).setCrop(type, null);
            handler.post(() -> callback.onImported(type));
        }).start();
    }

    private static BitmapFactory.Options boundsOptions() {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        return o;
    }

    /**
     * Loads the original image for editing: downsampled so neither side exceeds maxSide,
     * and rotated according to its EXIF orientation. Call from a background thread.
     */
    public Bitmap loadOriginalBitmap(WallpaperType type, int maxSide) {
        String path = getOriginalPath(type);
        BitmapFactory.Options bounds = boundsOptions();
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeFile(path, opts);
        if (bitmap == null) return null;

        int rotation = 0;
        boolean flip = false;
        try {
            int orientation = new ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: rotation = 90; break;
                case ExifInterface.ORIENTATION_ROTATE_180: rotation = 180; break;
                case ExifInterface.ORIENTATION_ROTATE_270: rotation = 270; break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: flip = true; break;
                case ExifInterface.ORIENTATION_TRANSPOSE: rotation = 90; flip = true; break;
                case ExifInterface.ORIENTATION_TRANSVERSE: rotation = 270; flip = true; break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL: rotation = 180; flip = true; break;
            }
        } catch (IOException ignored) {
        }
        if (rotation == 0 && !flip) return bitmap;

        Matrix m = new Matrix();
        if (flip) m.postScale(-1, 1);
        m.postRotate(rotation);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
        if (rotated != bitmap) bitmap.recycle();
        return rotated;
    }

    /**
     * Saves the given area (in source bitmap pixels) of source, scaled to the screen size,
     * as the wallpaper for this type and remembers the position for later adjustment.
     */
    public void saveCropped(Bitmap source, RectF cropRect, RectF normalizedCrop, WallpaperType type, SaveCallback callback) {
        Point size = DeviceUtil.getDisplaySize(mContext);
        int left = Math.max(0, Math.round(cropRect.left));
        int top = Math.max(0, Math.round(cropRect.top));
        int width = Math.max(1, Math.min(source.getWidth() - left, Math.round(cropRect.width())));
        int height = Math.max(1, Math.min(source.getHeight() - top, Math.round(cropRect.height())));

        Bitmap cropped = Bitmap.createBitmap(source, left, top, width, height);
        Bitmap scaled = Bitmap.createScaledBitmap(cropped, size.x, size.y, true);
        new Preferences(mContext).setCrop(type, normalizedCrop);
        saveFromBitmap(scaled, type, false, false, callback);
    }

    public void saveFromBitmap(Bitmap bitmap, WallpaperType type, boolean scaleToScreen, boolean png, SaveCallback callback) {
        Handler handler = new Handler();
        new Thread(() -> {

            Bitmap input = scaleToScreen ? scaleToScreenSize(bitmap) : bitmap;
            FileOutputStream output = null;

            try {
                output = new FileOutputStream(getWallpaperTypePath(type));
                input.compress(png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 100, output);
                handler.post(() -> callback.onSuccess(type));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                handler.post(() -> callback.onError(e));
            } finally {
                try {
                    if (output != null) {
                        output.flush();
                        output.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            onFileChanged(type);

        }).start();
    }

    private Bitmap scaleToScreenSize(Bitmap bitmap) {
        Point size = DeviceUtil.getDisplaySize(mContext);
        double scale = Math.max((double) size.x / (double) bitmap.getWidth(), (double) size.y / (double) bitmap.getHeight());
        return Bitmap.createScaledBitmap(bitmap, (int) (bitmap.getWidth() * scale), (int) (bitmap.getHeight() * scale), true);
    }

    private void onFileChanged(WallpaperType type) {
        Preferences preferences = new Preferences(mContext);
        if (preferences.isEnabled()) {
            boolean isNowDark = preferences.changeWithTheme() ? ThemeTrigger.isNowDark(mContext.getResources().getConfiguration()) : TimeTrigger.isNowDark(preferences);

            if (type.light != isNowDark) {
                loadWallpaper(type);
            }
        }
    }
}
