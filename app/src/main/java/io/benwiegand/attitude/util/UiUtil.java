package io.benwiegand.attitude.util;

import static android.util.TypedValue.COMPLEX_UNIT_DIP;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

public class UiUtil {

    private static String getExceptionCauseMessageList(Throwable t) {
        StringBuilder sb = new StringBuilder();

        do {
            sb.append("\n\ncaused by ")
                    .append(t.getClass().getSimpleName())
                    .append(": ")
                    .append(t.getMessage());
        } while ((t = t.getCause()) != null);

        return sb.toString();
    }

    public static void showError(Context c, String title, String message) {
        new AlertDialog.Builder(c)
                .setIcon(android.R.drawable.ic_delete)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("f---", null)
                .show();
    }

    public static void showError(Context c, Throwable t) {
        String message = t.getMessage();
        if (message == null) message = "null";

        if (t.getCause() != null)
            message += getExceptionCauseMessageList(t.getCause());

        showError(c, t.getClass().getSimpleName(), message);
    }

    public static void showWarning(Context c, String title, String message) {
        new AlertDialog.Builder(c)
                .setIcon(android.R.drawable.stat_notify_error)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("s---", null)
                .show();
    }

    public static void showToast(Context c, String text) {
        // the most over-used thing ever, I know
        Toast.makeText(c, text, Toast.LENGTH_SHORT).show();
    }

    public static float dpToPx(Context context, float dp) {
        return TypedValue.applyDimension(COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    /**
     * applies a constant tint color to a view.
     * handles special case for ImageView.
     */
    public static void tintView(View view, int color) {
        ColorStateList tintList = new ColorStateList(
                new int[][] {new int[0]},
                new int[] {color}
        );

        if (view instanceof ImageView v) {
            v.setImageTintList(tintList);
        } else {
            view.setForegroundTintList(tintList);
        }
    }


}
