package de.dlyt.yanndroid.dualwallpaper.ui.activity;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import de.dlyt.yanndroid.dualwallpaper.Preferences;
import de.dlyt.yanndroid.dualwallpaper.R;
import de.dlyt.yanndroid.dualwallpaper.ui.view.CropImageView;
import de.dlyt.yanndroid.dualwallpaper.utils.DeviceUtil;
import de.dlyt.yanndroid.dualwallpaper.utils.WallpaperUtil;
import de.dlyt.yanndroid.dualwallpaper.utils.WallpaperUtil.WallpaperType;

/**
 * Lets the user choose which part of the picked image becomes the wallpaper
 * (drag to move, pinch to zoom, double tap to reset).
 */
public class CropActivity extends AppCompatActivity {

    public static final String EXTRA_TYPE_INDEX = "type_index";
    private static final String STATE_CROP = "crop";

    private WallpaperUtil mWallpaperUtil;
    private WallpaperType mType;
    private CropImageView mCropView;
    private Bitmap mBitmap;
    private boolean mSaving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);

        int index = getIntent().getIntExtra(EXTRA_TYPE_INDEX, -1);
        if (index < 0 || index >= WallpaperType.values().length) {
            finish();
            return;
        }
        mType = WallpaperType.values()[index];
        mWallpaperUtil = new WallpaperUtil(this);

        ((TextView) findViewById(R.id.crop_title)).setText(getString(R.string.dialog_type_name_separator,
                mType.home ? getString(R.string.home_screen) : getString(R.string.lock_screen),
                mType.light ? getString(R.string.theme_light) : getString(R.string.theme_dark)));

        Point screen = DeviceUtil.getDisplaySize(this);
        mCropView = findViewById(R.id.crop_view);
        mCropView.setTargetAspect((float) screen.x / (float) screen.y);

        RectF crop = savedInstanceState != null ? savedInstanceState.getParcelable(STATE_CROP) : null;
        if (crop == null) crop = new Preferences(this).getCrop(mType);
        mCropView.setCrop(crop);

        Button done = findViewById(R.id.crop_done);
        done.setEnabled(false);
        done.setOnClickListener(v -> save());
        findViewById(R.id.crop_reset).setOnClickListener(v -> mCropView.resetPosition());
        findViewById(R.id.crop_cancel).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        // Enough resolution to zoom in a good amount while keeping memory reasonable.
        int maxSide = Math.min(4096, Math.max(screen.x, screen.y) * 3);
        new Thread(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = mWallpaperUtil.loadOriginalBitmap(mType, maxSide);
            } catch (OutOfMemoryError e) {
                e.printStackTrace();
            }
            Bitmap result = bitmap;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                findViewById(R.id.crop_progress).setVisibility(View.GONE);
                if (result == null) {
                    Toast.makeText(this, R.string.error_saving_wallpaper, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                mBitmap = result;
                mCropView.setBitmap(result);
                done.setEnabled(true);
            });
        }).start();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mBitmap != null) outState.putParcelable(STATE_CROP, mCropView.getNormalizedCrop());
    }

    private void save() {
        if (mBitmap == null || mSaving) return;
        mSaving = true;
        findViewById(R.id.crop_done).setEnabled(false);
        findViewById(R.id.crop_progress).setVisibility(View.VISIBLE);

        mWallpaperUtil.saveCropped(mBitmap, mCropView.getCropRect(), mCropView.getNormalizedCrop(), mType, new WallpaperUtil.SaveCallback() {
            @Override
            public void onSuccess(WallpaperType type) {
                setResult(RESULT_OK, getIntent());
                finish();
            }

            @Override
            public void onError(Exception e) {
                mSaving = false;
                findViewById(R.id.crop_done).setEnabled(true);
                findViewById(R.id.crop_progress).setVisibility(View.GONE);
                Toast.makeText(CropActivity.this, R.string.error_saving_wallpaper, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
