package de.dlyt.yanndroid.dualwallpaper.ui.activity;

import static de.dlyt.yanndroid.dualwallpaper.utils.WallpaperUtil.WallpaperType;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.FragmentManager;

import de.dlyt.yanndroid.dualwallpaper.Preferences;
import de.dlyt.yanndroid.dualwallpaper.R;
import de.dlyt.yanndroid.dualwallpaper.trigger.ThemeTrigger;
import de.dlyt.yanndroid.dualwallpaper.ui.adapter.ViewPagerAdapter;
import de.dlyt.yanndroid.dualwallpaper.ui.fragment.PreferencesFragment;
import de.dlyt.yanndroid.dualwallpaper.utils.DeviceUtil;
import de.dlyt.yanndroid.dualwallpaper.utils.TriggerUtil;
import de.dlyt.yanndroid.dualwallpaper.utils.WallpaperUtil;
import dev.oneuiproject.oneui.layout.ToolbarLayout;
import dev.oneuiproject.oneui.utils.ActivityUtils;

public class MainActivity extends AppCompatActivity {

    public static final int PICKER_REQUEST_CODE = 5000;
    public static final int CROP_REQUEST_CODE = 6000;

    private WallpaperUtil mWallpaperUtil;
    private ViewPagerAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mWallpaperUtil = new WallpaperUtil(this);
        mAdapter = new ViewPagerAdapter(this, mWallpaperUtil);

        if (!DeviceUtil.hasStoragePermission(this)) DeviceUtil.requestStoragePermission(this);
        NotificationManagerCompat.from(this).createNotificationChannel(new NotificationChannel(ThemeTrigger.NOTIFICATION_CHANNEL_ID, getString(R.string.notification_title), NotificationManager.IMPORTANCE_MIN));

        ToolbarLayout toolbarLayout = findViewById(R.id.toolbarLayout);
        toolbarLayout.setNavigationButtonAsBack();

        Preferences preferences = new Preferences(this);
        if (preferences.isEnabled()) {
            if (preferences.changeWithTheme()) {
                TriggerUtil.startThemeTrigger(this);
            } else {
                TriggerUtil.startTimeTrigger(this);
            }
        }

        PreferencesFragment fragment = new PreferencesFragment();
        fragment.initFields(mAdapter, mWallpaperUtil);

        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.main_preferences, fragment).commit();
        fragmentManager.executePendingTransactions();

        if (getIntent().getAction().equals(Intent.ACTION_ATTACH_DATA))
            handleSetWallpaperIntent(getIntent());
    }

    private void handleSetWallpaperIntent(Intent intent) {
        WallpaperType[] types = WallpaperType.values();
        CharSequence[] items = new CharSequence[types.length];
        for (int i = 0; i < types.length; i++) {
            WallpaperType type = types[i];
            items[i] = getString(R.string.dialog_type_name_separator,
                    type.home ? getString(R.string.home_screen) : getString(R.string.lock_screen),
                    type.light ? getString(R.string.theme_light) : getString(R.string.theme_dark));
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_set_wallpaper_as)
                .setNegativeButton(dev.oneuiproject.oneui.design.R.string.oui_common_cancel, null)
                .setItems(items, (dialog1, which) -> saveWallpaperFromIntent(intent, types[which]))
                .create();
        dialog.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        int count = WallpaperType.values().length;
        if (requestCode >= PICKER_REQUEST_CODE && requestCode < PICKER_REQUEST_CODE + count) {
            saveWallpaperFromIntent(data, WallpaperType.values()[requestCode - PICKER_REQUEST_CODE]);
        } else if (requestCode >= CROP_REQUEST_CODE && requestCode < CROP_REQUEST_CODE + count) {
            WallpaperType type = WallpaperType.values()[requestCode - CROP_REQUEST_CODE];
            mAdapter.notifyItemChanged(type.light ? 0 : 1);
        }
    }

    /** Opens the screen where the user positions the original image of this wallpaper. */
    public void openPositionEditor(WallpaperType type) {
        Intent intent = new Intent(this, CropActivity.class);
        intent.putExtra(CropActivity.EXTRA_TYPE_INDEX, type.index);
        startActivityForResult(intent, CROP_REQUEST_CODE + type.index);
    }

    private void saveWallpaperFromIntent(Intent intent, WallpaperType type) {
        mWallpaperUtil.importFromIntent(intent, type, new WallpaperUtil.ImportCallback() {
            @Override
            public void onImported(WallpaperType type) {
                openPositionEditor(type);
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(MainActivity.this, R.string.error_saving_wallpaper, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(dev.oneuiproject.oneui.design.R.menu.app_info_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == dev.oneuiproject.oneui.design.R.id.menu_app_info) {
            ActivityUtils.startPopOverActivity(this,
                    new Intent(this, AboutActivity.class),
                    null,
                    ActivityUtils.POP_OVER_POSITION_RIGHT | ActivityUtils.POP_OVER_POSITION_TOP);
            return true;
        }
        return false;
    }
}