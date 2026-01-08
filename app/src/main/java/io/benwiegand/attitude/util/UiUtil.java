package io.benwiegand.attitude.util;

import static android.util.TypedValue.COMPLEX_UNIT_DIP;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import io.benwiegand.attitude.R;
import io.benwiegand.attitude.exception.UserFriendlyException;

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

    public static void showError(Context c, @StringRes int title, @StringRes int message) {
        new AlertDialog.Builder(c)
                .setIcon(android.R.drawable.ic_delete)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.ok_button, null)
                .show();
    }

    private static void showError(Context c, String title, String message) {
        new AlertDialog.Builder(c)
                .setIcon(android.R.drawable.ic_delete)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.ok_button, null)
                .show();
    }

    // exempt from string resource requirement (for now)
    public static void showDebugError(Context c, String title, String message) {
        showError(c, title, message);
    }

    // exempt from string resource requirement (for now)
    public static void showDebugError(Context c, Throwable t) {
        String message = t.getMessage();
        if (message == null) message = "null";

        if (t.getCause() != null)
            message += getExceptionCauseMessageList(t.getCause());

        showError(c, t.getClass().getSimpleName(), message);
    }

    public static void showError(Context c, UserFriendlyException e) {
        String message = e.getFriendlyMessage();

        //TODO: this still isn't the correct way to format this, but it is better than before
        if (e.getCause() != null)
            message += getExceptionCauseMessageList(e.getCause());

        String title = e.getFriendlyTitle();
        if (title == null) title = c.getString(R.string.error_message_title_generic);
        showError(c, title, message);
    }

    public static void showError(Context c, @StringRes int title, UserFriendlyException e) {
        String message = e.getFriendlyMessage();

        //TODO: this still isn't the correct way to format this, but it is better than before
        if (e.getCause() != null)
            message += getExceptionCauseMessageList(e.getCause());

        showError(c, c.getString(title), message);
    }

    public static void showUnexpectedError(Context c, @StringRes int title, Throwable unexpected) {
        showError(c, new UserFriendlyException(c, title, R.string.unexpected_error_message, unexpected));
    }


    public static void showWarning(Context c, @StringRes int title, @StringRes int message) {
        new AlertDialog.Builder(c)
                .setIcon(android.R.drawable.stat_notify_error)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.ok_button, null)
                .show();
    }

    public static void showToast(Context c, @StringRes int text) {
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
