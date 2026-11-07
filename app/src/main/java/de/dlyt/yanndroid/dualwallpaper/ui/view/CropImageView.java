package de.dlyt.yanndroid.dualwallpaper.ui.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Shows a bitmap behind a frame that has the aspect ratio of the screen.
 * The user can drag to move and pinch to zoom the image inside the frame.
 * The image is always kept large enough to cover the whole frame.
 * Double tap resets to the default centered position.
 */
public class CropImageView extends View {

    private static final float MAX_ZOOM = 6f;

    private Bitmap mBitmap;
    private float mAspect = 9f / 19.5f; // width / height of the target (screen)

    private final RectF mFrame = new RectF();
    private final Matrix mMatrix = new Matrix();
    private final Paint mBitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint mDimPaint = new Paint();
    private final Paint mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mDimPath = new Path();

    // Current transform: bitmap pixel (x, y) -> view (x * mScale + mTransX, y * mScale + mTransY)
    private float mScale = 1f;
    private float mTransX = 0f;
    private float mTransY = 0f;
    private boolean mLayoutReady = false;
    private boolean mInteracting = false;

    /** Crop (normalized 0..1 bitmap coords) to restore once the view is laid out. */
    @Nullable
    private RectF mPendingCrop;

    private final ScaleGestureDetector mScaleDetector;
    private final GestureDetector mGestureDetector;

    public CropImageView(Context context) {
        this(context, null);
    }

    public CropImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;

        mDimPaint.setColor(0xB3000000);
        mDimPaint.setStyle(Paint.Style.FILL);

        mBorderPaint.setColor(Color.WHITE);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(2 * density);

        mGridPaint.setColor(0x80FFFFFF);
        mGridPaint.setStyle(Paint.Style.STROKE);
        mGridPaint.setStrokeWidth(1 * density);

        mScaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                zoomBy(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                return true;
            }
        });

        mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                mTransX -= distanceX;
                mTransY -= distanceY;
                applyConstraints();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                resetPosition();
                return true;
            }
        });
    }

    public void setBitmap(Bitmap bitmap) {
        mBitmap = bitmap;
        if (mLayoutReady) {
            if (mPendingCrop != null) restoreCrop();
            else resetPosition();
        }
        invalidate();
    }

    /** @param aspect target width / height (e.g. screen width / screen height) */
    public void setTargetAspect(float aspect) {
        mAspect = aspect;
        if (mLayoutReady) {
            computeFrame(getWidth(), getHeight());
            resetPosition();
        }
    }

    /** Restore a previous crop, given as normalized (0..1) coordinates of the bitmap. */
    public void setCrop(@Nullable RectF normalizedCrop) {
        mPendingCrop = normalizedCrop;
        if (mLayoutReady && mBitmap != null) {
            if (mPendingCrop != null) restoreCrop();
            else resetPosition();
        }
    }

    /** Centered, smallest zoom that fills the frame (same as the old automatic behaviour). */
    public void resetPosition() {
        mPendingCrop = null;
        if (mBitmap == null || mFrame.isEmpty()) return;
        mScale = getMinScale();
        mTransX = mFrame.centerX() - mBitmap.getWidth() * mScale / 2f;
        mTransY = mFrame.centerY() - mBitmap.getHeight() * mScale / 2f;
        applyConstraints();
    }

    /** @return the visible area inside the frame, in bitmap pixel coordinates. */
    public RectF getCropRect() {
        RectF r = new RectF(
                (mFrame.left - mTransX) / mScale,
                (mFrame.top - mTransY) / mScale,
                (mFrame.right - mTransX) / mScale,
                (mFrame.bottom - mTransY) / mScale);
        if (mBitmap != null) {
            r.left = Math.max(0, r.left);
            r.top = Math.max(0, r.top);
            r.right = Math.min(mBitmap.getWidth(), r.right);
            r.bottom = Math.min(mBitmap.getHeight(), r.bottom);
        }
        return r;
    }

    /** @return the visible area as normalized (0..1) bitmap coordinates. */
    public RectF getNormalizedCrop() {
        RectF r = getCropRect();
        if (mBitmap == null) return r;
        return new RectF(r.left / mBitmap.getWidth(), r.top / mBitmap.getHeight(),
                r.right / mBitmap.getWidth(), r.bottom / mBitmap.getHeight());
    }

    private void restoreCrop() {
        RectF c = mPendingCrop;
        mPendingCrop = null;
        if (c == null || mBitmap == null || mFrame.isEmpty()) return;
        float cropW = c.width() * mBitmap.getWidth();
        if (cropW <= 0) {
            resetPosition();
            return;
        }
        mScale = mFrame.width() / cropW;
        mTransX = mFrame.left - c.left * mBitmap.getWidth() * mScale;
        mTransY = mFrame.top - c.top * mBitmap.getHeight() * mScale;
        applyConstraints();
    }

    private float getMinScale() {
        if (mBitmap == null) return 1f;
        return Math.max(mFrame.width() / mBitmap.getWidth(), mFrame.height() / mBitmap.getHeight());
    }

    private void zoomBy(float factor, float focusX, float focusY) {
        float minScale = getMinScale();
        float newScale = Math.max(minScale, Math.min(minScale * MAX_ZOOM, mScale * factor));
        float applied = newScale / mScale;
        mTransX = focusX - (focusX - mTransX) * applied;
        mTransY = focusY - (focusY - mTransY) * applied;
        mScale = newScale;
        applyConstraints();
    }

    /** Keep the image covering the frame completely. */
    private void applyConstraints() {
        if (mBitmap == null) return;
        float minScale = getMinScale();
        if (mScale < minScale) mScale = minScale;

        float w = mBitmap.getWidth() * mScale;
        float h = mBitmap.getHeight() * mScale;

        // left edge must be <= frame.left, right edge >= frame.right
        mTransX = Math.min(mFrame.left, Math.max(mFrame.right - w, mTransX));
        mTransY = Math.min(mFrame.top, Math.max(mFrame.bottom - h, mTransY));

        mMatrix.reset();
        mMatrix.postScale(mScale, mScale);
        mMatrix.postTranslate(mTransX, mTransY);
        invalidate();
    }

    private void computeFrame(int w, int h) {
        float pad = 24 * getResources().getDisplayMetrics().density;
        float availW = w - 2 * pad;
        float availH = h - 2 * pad;
        float frameW, frameH;
        if (availW / availH > mAspect) {
            frameH = availH;
            frameW = frameH * mAspect;
        } else {
            frameW = availW;
            frameH = frameW / mAspect;
        }
        float left = (w - frameW) / 2f;
        float top = (h - frameH) / 2f;
        mFrame.set(left, top, left + frameW, top + frameH);

        mDimPath.reset();
        mDimPath.setFillType(Path.FillType.EVEN_ODD);
        mDimPath.addRect(0, 0, w, h, Path.Direction.CW);
        mDimPath.addRect(mFrame, Path.Direction.CW);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == 0 || h == 0) return;

        RectF keep = (mLayoutReady && mBitmap != null) ? getNormalizedCrop() : mPendingCrop;
        computeFrame(w, h);
        mLayoutReady = true;
        mPendingCrop = keep;
        if (mBitmap != null) {
            if (mPendingCrop != null) restoreCrop();
            else resetPosition();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mBitmap == null) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mInteracting = true;
            getParent().requestDisallowInterceptTouchEvent(true);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            mInteracting = false;
            invalidate();
        }
        mScaleDetector.onTouchEvent(event);
        if (!mScaleDetector.isInProgress()) mGestureDetector.onTouchEvent(event);
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mBitmap == null) return;

        canvas.drawBitmap(mBitmap, mMatrix, mBitmapPaint);
        canvas.drawPath(mDimPath, mDimPaint);

        if (mInteracting) {
            float tw = mFrame.width() / 3f;
            float th = mFrame.height() / 3f;
            for (int i = 1; i < 3; i++) {
                canvas.drawLine(mFrame.left + tw * i, mFrame.top, mFrame.left + tw * i, mFrame.bottom, mGridPaint);
                canvas.drawLine(mFrame.left, mFrame.top + th * i, mFrame.right, mFrame.top + th * i, mGridPaint);
            }
        }
        canvas.drawRect(mFrame, mBorderPaint);
    }
}
