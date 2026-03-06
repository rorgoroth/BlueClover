/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.otacoo.chan.ui.view;

import static org.otacoo.chan.utils.AndroidUtils.enableHighEndAnimations;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class TransitionImageView extends View {
    private static final String TAG = "TransitionImageView";

    private Bitmap bitmap;
    private final Matrix matrix = new Matrix();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF bitmapRect = new RectF();
    private final RectF destRect = new RectF();
    private final RectF sourceImageRect = new RectF();
    private final PointF sourceOverlap = new PointF();
    private final RectF destClip = new RectF();
    private float progress;
    private float stateScale;
    private float stateBitmapScaleDiff;
    private PointF stateBitmapSize;
    private PointF statePos;
    private float fromRounding = 0.0f;

    private BitmapShader bitmapShader;
    private Bitmap lastBitmap;

    public TransitionImageView(Context context) {
        super(context);
        init();
    }

    public TransitionImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TransitionImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        if (bitmap == null) return;
        bitmapRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());

        // Center inside method
        float selfWidth = getWidth();
        float selfHeight = getHeight();

        if (selfWidth == 0 || selfHeight == 0) return;

        float destScale = Math.min(
                selfWidth / (float) bitmap.getWidth(),
                selfHeight / (float) bitmap.getHeight());

        RectF output = new RectF(
                (selfWidth - bitmap.getWidth() * destScale) * 0.5f,
                (selfHeight - bitmap.getHeight() * destScale) * 0.5f, 0, 0);

        output.right = bitmap.getWidth() * destScale + output.left;
        output.bottom = bitmap.getHeight() * destScale + output.top;

        destRect.set(output);

        matrix.setRectToRect(bitmapRect, destRect, Matrix.ScaleToFit.FILL);
    }

    public void setSourceImageView(Point windowLocation, Point viewSize, Bitmap bitmap,
                                   float rounding) {
        this.bitmap = bitmap;
        this.fromRounding = rounding;
        if (bitmap == null) return;
        bitmapRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());

        if (stateBitmapSize != null) {
            stateBitmapScaleDiff = stateBitmapSize.x / bitmap.getWidth();
        }

        int[] myLoc = new int[2];
        getLocationInWindow(myLoc);
        float globalOffsetX = windowLocation.x - myLoc[0];
        float globalOffsetY = windowLocation.y - myLoc[1];

        // Get the coords in the image view with the center crop method
        float scale = Math.max(
                (float) viewSize.x / (float) bitmap.getWidth(),
                (float) viewSize.y / (float) bitmap.getHeight());
        float scaledX = bitmap.getWidth() * scale;
        float scaledY = bitmap.getHeight() * scale;
        float offsetX = (scaledX - viewSize.x) * 0.5f;
        float offsetY = (scaledY - viewSize.y) * 0.5f;

        sourceOverlap.set(offsetX, offsetY);

        sourceImageRect.set(
                -offsetX + globalOffsetX,
                -offsetY + globalOffsetY,
                scaledX - offsetX + globalOffsetX,
                scaledY - offsetY + globalOffsetY);
    }

    public void setState(float stateScale, PointF statePos, PointF stateBitmapSize) {
        this.stateScale = stateScale;
        this.statePos = statePos;
        this.stateBitmapSize = stateBitmapSize;
    }

    public void setProgress(float progress) {
        this.progress = progress;
        if (bitmap == null) return;

        RectF output;
        if (statePos != null) {
            // Use scale and translate from ssiv
            output = new RectF(-statePos.x * stateScale, -statePos.y * stateScale, 0, 0);
            output.right = output.left + bitmap.getWidth() * stateBitmapScaleDiff * stateScale;
            output.bottom = output.top + bitmap.getHeight() * stateBitmapScaleDiff * stateScale;
        } else {
            // Center inside method
            float selfWidth = getWidth();
            float selfHeight = getHeight();

            if (selfWidth == 0 || selfHeight == 0) return;

            float destScale = Math.min(
                    selfWidth / (float) bitmap.getWidth(),
                    selfHeight / (float) bitmap.getHeight());

            output = new RectF(
                    (selfWidth - bitmap.getWidth() * destScale) * 0.5f,
                    (selfHeight - bitmap.getHeight() * destScale) * 0.5f, 0, 0);

            output.right = bitmap.getWidth() * destScale + output.left;
            output.bottom = bitmap.getHeight() * destScale + output.top;
        }

        // Linear interpolate between start bounds and calculated final bounds
        output.left = lerp(sourceImageRect.left, output.left, progress);
        output.top = lerp(sourceImageRect.top, output.top, progress);
        output.right = lerp(sourceImageRect.right, output.right, progress);
        output.bottom = lerp(sourceImageRect.bottom, output.bottom, progress);

        destRect.set(output);

        matrix.setRectToRect(bitmapRect, destRect, Matrix.ScaleToFit.FILL);

        destClip.set(
                output.left + sourceOverlap.x * (1f - progress),
                output.top + sourceOverlap.y * (1f - progress),
                output.right - sourceOverlap.x * (1f - progress),
                output.bottom - sourceOverlap.y * (1f - progress)
        );

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }

        float rounding = 0f;
        if (progress < 1f) {
            rounding = enableHighEndAnimations() ? lerp(fromRounding, 0.0f, progress) : 0f;
        }

        if (rounding > 0.5f) {
            if (bitmapShader == null || lastBitmap != bitmap) {
                bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                lastBitmap = bitmap;
                paint.setShader(bitmapShader);
            }
            bitmapShader.setLocalMatrix(matrix);
            canvas.drawRoundRect(destClip, rounding, rounding, paint);
        } else {
            paint.setShader(null);
            canvas.save();
            canvas.clipRect(destClip);
            canvas.drawBitmap(bitmap, matrix, paint);
            canvas.restore();
        }
    }

    private float lerp(float a, float b, float x) {
        return a + (b - a) * x;
    }
}
