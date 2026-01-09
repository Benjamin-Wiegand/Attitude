package io.benwiegand.attitude.notification;

import static android.util.TypedValue.COMPLEX_UNIT_MM;

import android.content.Context;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


public class NotificationFrameView extends FrameLayout {
    private static final String TAG = NotificationFrameView.class.getSimpleName();

    private static final long SNAP_BACK_ANIMATION_DURATION = 250;
    private static final long CLEAR_ANIMATION_DURATION = 250;
    private static final long CLEAR_CANCEL_DELAY = 500;

    // velocity of notification required to clear it
    private static final float CLEAR_VELOCITY_THRESHOLD_MM_S = 20;

    // movement must occur by at least this much in a direction before deciding what gesture it is
    private static final int GESTURE_DECISION_THRESHOLD_MM = 1;


    // GESTURE_DECISION_THRESHOLD_MM in px
    private float decisionThreshold;

    // CLEAR_VELOCITY_THRESHOLD_MM_S in px/ms
    private float clearVelocityThreshold;

    // threshold of x movement at which to clear notification regardless of velocity
    // also used to fade alpha
    private float clearDistanceThreshold;


    private long lastDownEventTime = 0;
    private float downX = 0;
    private float downY = 0;
    private float prevDeltaX = 0;
    private float prevEventTime = 0;
    private boolean gestureDecided = false;
    private boolean gestureClear = false;
    private boolean passedClearThreshold = false;


    private Runnable onClearListener = null;


    private void init() {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        decisionThreshold = TypedValue.applyDimension(COMPLEX_UNIT_MM, GESTURE_DECISION_THRESHOLD_MM, displayMetrics);
        clearVelocityThreshold = TypedValue.applyDimension(COMPLEX_UNIT_MM, CLEAR_VELOCITY_THRESHOLD_MM_S, displayMetrics) / 1000f;
        clearDistanceThreshold = displayMetrics.widthPixels * 2f / 3f;  // 2/3 of screen, close-ish to aosp
    }

    public NotificationFrameView(@NonNull Context context) {
        super(context);
        init();
    }

    public NotificationFrameView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public NotificationFrameView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public NotificationFrameView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public Runnable getOnClearListener() {
        return onClearListener;
    }

    public void setOnClearListener(Runnable onClearListener) {
        this.onClearListener = onClearListener;
    }

    private void clear() {
        if (onClearListener != null)
            onClearListener.run();

        // come back after a moment if clear fails
        // I *think* this happens on aosp?
        getHandler().postDelayed(() -> {
            if (getParent() != null)
                snapBack();
        }, CLEAR_CANCEL_DELAY);
    }


    private void snapBack() {

        animate()
                .setDuration(SNAP_BACK_ANIMATION_DURATION)
                .translationX(0)
                .alpha(1)
                .start();

    }

    private void flyOut(boolean left) {
        animate()
                .setDuration(CLEAR_ANIMATION_DURATION)
                .translationX(left ? -getWidth() : getWidth())
                .alpha(0)
                .withEndAction(this::clear)
                .start();
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
//        Log.d(TAG, "event = " + event);

        boolean runSuper = true;
        boolean requireNext = false;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                lastDownEventTime = event.getDownTime();
                downX = event.getRawX();
                downY = event.getRawY();
                prevDeltaX = 0;
                prevEventTime = event.getEventTime();
                gestureDecided = false;
                gestureClear = false;
                passedClearThreshold = false;

                requireNext = true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (event.getDownTime() != lastDownEventTime) break;    // must be initiated with down event
                if (gestureDecided && !gestureClear) break;

                float deltaX = event.getRawX() - downX;
                float diffX = Math.abs(deltaX);

                ViewParent parent = getParent();

                if (!gestureDecided) {
                    float diffY = Math.abs(event.getRawY() - downY);

                    // need enough of a movement to decide
                    if (diffY > decisionThreshold || diffX > decisionThreshold) {
                        gestureClear = Math.abs(diffX) > Math.abs(diffY);
                        gestureDecided = true;
                        Log.d(TAG, "delta x = " + diffX);
                        Log.d(TAG, "delta y = " + diffY);
                        Log.d(TAG, "decided gesture, clear = " + gestureClear);

                        if (parent != null) parent.requestDisallowInterceptTouchEvent(gestureClear);
                    } else {
                        requireNext = true;

                        // don't let the ScrollView start scrolling yet
                        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                    }
                }


                if (gestureClear) {
                    float clearProgress = diffX / clearDistanceThreshold;
                    float absVelocityX = Math.abs(deltaX - prevDeltaX) / (event.getEventTime() - prevEventTime);

                    setTranslationX(deltaX);
                    setAlpha(1f - Math.min(clearProgress, 1f));

                    if ((absVelocityX > clearVelocityThreshold || clearProgress > 1f) != passedClearThreshold) {
                        passedClearThreshold = !passedClearThreshold;
//                        Log.d(TAG, "passedClearThreshold = " + passedClearThreshold);

                        // TODO: haptic feedback would happen here if the distance threshold was crossed.
                        //       unfortunately, the Vibrator service doesn't work on quest. the openxr sdk would be needed.
                    }

                    prevDeltaX = deltaX;
                    prevEventTime = event.getEventTime();
                    requireNext = true;
                }

            }
            case MotionEvent.ACTION_UP -> {
                if (event.getDownTime() != lastDownEventTime) break;
                if (!gestureDecided || !gestureClear) break;

                if (passedClearThreshold) {
                    float deltaX = event.getRawX() - downX;

                    flyOut(deltaX < 0);

                } else {
                    snapBack();
                }

                // don't do onClick
                runSuper = false;
            }
            default -> {
                if (event.getDownTime() != lastDownEventTime) break;
                if (!gestureDecided || !gestureClear) break;

                // assume the gesture was cancelled
                lastDownEventTime = 0;  // ensure no further handling
                snapBack();

            }
        }

        if (!runSuper) return requireNext;
        return super.onTouchEvent(event) || requireNext;
    }
}
