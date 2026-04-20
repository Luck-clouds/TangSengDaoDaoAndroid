package com.chat.base.utils;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

public final class WKImageDisplayUtils {
    private WKImageDisplayUtils() {
    }

    public static void prepareImageSlot(ImageView imageView, float paddingDp) {
        if (imageView == null) {
            return;
        }
        int padding = AndroidUtilities.dp(paddingDp);
        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        imageView.setAdjustViewBounds(true);
        imageView.setPadding(padding, padding, padding, padding);
    }

    public static void limitDrawableInside(ImageView imageView, @Nullable Drawable drawable, float insetDp, float paddingDp) {
        if (imageView == null) {
            return;
        }
        prepareImageSlot(imageView, paddingDp);
        if (drawable == null) {
            imageView.setImageDrawable(null);
            return;
        }
        int inset = AndroidUtilities.dp(insetDp);
        imageView.setImageDrawable(new InsetDrawable(drawable, inset));
    }

    public static void limitResourceInside(ImageView imageView, @DrawableRes int drawableRes, float insetDp, float paddingDp) {
        if (imageView == null) {
            return;
        }
        Drawable drawable = ContextCompat.getDrawable(imageView.getContext(), drawableRes);
        limitDrawableInside(imageView, drawable, insetDp, paddingDp);
    }
}
