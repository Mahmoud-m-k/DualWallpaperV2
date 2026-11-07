package de.dlyt.yanndroid.dualwallpaper.ui.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import de.dlyt.yanndroid.dualwallpaper.R;
import de.dlyt.yanndroid.dualwallpaper.ui.activity.MainActivity;
import de.dlyt.yanndroid.dualwallpaper.utils.BitmapUtil;
import de.dlyt.yanndroid.dualwallpaper.utils.DeviceUtil;
import de.dlyt.yanndroid.dualwallpaper.utils.WallpaperUtil;
import dev.oneuiproject.oneui.utils.DialogUtils;

public class WallpaperActionsDialog {

    public interface Callback {
        void onDone();

        void onDelete();
    }

    private final Context mContext;
    private final WallpaperUtil mWallpaperUtil;
    private final WallpaperUtil.WallpaperType mType;
    private final AlertDialog mDialog;
    private final Callback mCallback;

    public WallpaperActionsDialog(Context context, WallpaperUtil wallpaperUtil, WallpaperUtil.WallpaperType type, boolean deleteButton, Callback callback) {
        this.mContext = context;
        this.mWallpaperUtil = wallpaperUtil;
        this.mType = type;
        this.mCallback = callback;

        List<CharSequence> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (mWallpaperUtil.hasOriginal(type)) {
            items.add(mContext.getString(R.string.dialog_adjust_position));
            actions.add(() -> ((MainActivity) mContext).openPositionEditor(mType));
        }
        items.add(mContext.getString(R.string.dialog_pick_new_image));
        actions.add(this::actionPickNew);
        items.add(mContext.getString(R.string.dialog_use_current));
        actions.add(this::actionUseCurrent);
        items.add(mContext.getString(R.string.dialog_plain_color));
        actions.add(this::actionPlainColor);
        items.add(mContext.getString(R.string.dialog_gradient_color));
        actions.add(this::actionGradientColor);

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(mContext)
                .setTitle(mContext.getString(R.string.dialog_type_name_separator,
                        type.home ? mContext.getString(R.string.home_screen) : mContext.getString(R.string.lock_screen),
                        type.light ? mContext.getString(R.string.theme_light) : mContext.getString(R.string.theme_dark)))
                .setNegativeButton(dev.oneuiproject.oneui.design.R.string.oui_common_cancel, null)
                .setItems(items.toArray(new CharSequence[0]), (dialog, which) -> actions.get(which).run());

        if (deleteButton) {
            dialogBuilder.setPositiveButton(R.string.dialog_delete, (dialog, which) -> mCallback.onDelete());
        }

        mDialog = dialogBuilder.create();
    }

    public void show() {
        mDialog.show();
        if (mDialog.getButton(DialogInterface.BUTTON_POSITIVE) != null) {
            DialogUtils.setDialogButtonTextColor(mDialog, AlertDialog.BUTTON_POSITIVE, mContext.getColor(dev.oneuiproject.oneui.design.R.color.oui_functional_red_color));
        }
    }

    private void actionUseCurrent() {
        mWallpaperUtil.importFromCurrent(mType, new WallpaperUtil.ImportCallback() {
            @Override
            public void onImported(WallpaperUtil.WallpaperType type) {
                ((MainActivity) mContext).openPositionEditor(type);
            }

            @Override
            public void onError(Exception e) {
                int errorMsg = R.string.error_saving_wallpaper;
                if (e instanceof SecurityException) {
                    DeviceUtil.requestStoragePermission((MainActivity) mContext);
                    errorMsg = R.string.error_storage_permission_not_granted;
                } else if (e instanceof FileNotFoundException) {
                    errorMsg = R.string.error_wallpaper_not_supported;
                }
                Toast.makeText(mContext, errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void actionPickNew() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        ((MainActivity) mContext).startActivityForResult(intent, MainActivity.PICKER_REQUEST_CODE + mType.index);
    }

    /** Colors have nothing to position, so forget any previously picked image. */
    private void clearOriginal() {
        new File(mWallpaperUtil.getOriginalPath(mType)).delete();
    }

    private void actionPlainColor() {
        int lastColor = Color.BLACK;
        Bitmap image = BitmapFactory.decodeFile(mWallpaperUtil.getWallpaperTypePath(mType));
        if (image != null) lastColor = image.getPixel(0, 0);

        new ColorPickerDialog(mContext, color -> mWallpaperUtil.saveFromBitmap(BitmapUtil.plainColor(DeviceUtil.getDisplaySize(mContext), color), mType, false, false, new WallpaperUtil.SaveCallback() {
            @Override
            public void onSuccess(WallpaperUtil.WallpaperType type) {
                clearOriginal();
                mCallback.onDone();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(mContext, R.string.error_saving_wallpaper, Toast.LENGTH_SHORT).show();
            }
        }), lastColor).show();
    }

    private void actionGradientColor() {
        new GradientPickerDialog(mContext, (startColor, endColor) -> mWallpaperUtil.saveFromBitmap(BitmapUtil.gradientColor(DeviceUtil.getDisplaySize(mContext), startColor, endColor), mType, false, true, new WallpaperUtil.SaveCallback() {
            @Override
            public void onSuccess(WallpaperUtil.WallpaperType type) {
                clearOriginal();
                mCallback.onDone();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(mContext, R.string.error_saving_wallpaper, Toast.LENGTH_SHORT).show();
            }
        })).show();
    }
}
