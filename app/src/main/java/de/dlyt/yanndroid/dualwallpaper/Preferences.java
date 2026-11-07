package de.dlyt.yanndroid.dualwallpaper;

import android.content.Context;
import android.content.SharedPreferences;

import android.graphics.RectF;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import de.dlyt.yanndroid.dualwallpaper.utils.WallpaperUtil;

public class Preferences {

    private Context mContext;
    private SharedPreferences mSharedPreferences;

    public Preferences(Context context) {
        this.mContext = context;
        this.mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    public boolean isEnabled() {
        return mSharedPreferences.getBoolean("service_enabled", false);
    }

    public boolean changeWithTheme() {
        return mSharedPreferences.getString("service_mode", "0").equals("0");
    }

    public int getScheduleStart() {
        return mSharedPreferences.getInt("schedule_start", 1140);
    }

    public int getScheduleEnd() {
        return mSharedPreferences.getInt("schedule_end", 420);
    }

    public void setScheduleTime(int startTime, int endTime) {
        mSharedPreferences.edit().putInt("schedule_start", startTime).putInt("schedule_end", endTime).apply();
    }

    /** Last chosen position of a wallpaper, as normalized (0..1) coordinates of the original image. */
    @Nullable
    public RectF getCrop(WallpaperUtil.WallpaperType type) {
        String value = mSharedPreferences.getString("crop_" + type.name(), null);
        if (value == null) return null;
        try {
            String[] p = value.split(",");
            return new RectF(Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public void setCrop(WallpaperUtil.WallpaperType type, @Nullable RectF crop) {
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        if (crop == null) {
            editor.remove("crop_" + type.name());
        } else {
            editor.putString("crop_" + type.name(), crop.left + "," + crop.top + "," + crop.right + "," + crop.bottom);
        }
        editor.apply();
    }
}
