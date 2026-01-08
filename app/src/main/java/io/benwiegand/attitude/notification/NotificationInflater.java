package io.benwiegand.attitude.notification;

import static io.benwiegand.attitude.util.UiUtil.dpToPx;
import static io.benwiegand.attitude.util.UiUtil.showDebugError;
import static io.benwiegand.attitude.util.UiUtil.showError;
import static io.benwiegand.attitude.util.UiUtil.showUnexpectedError;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import io.benwiegand.attitude.R;
import io.benwiegand.attitude.misc.RemoteResourceId;

public class NotificationInflater {
    private static final String TAG = NotificationInflater.class.getSimpleName();

    private static final long ANIMATION_DURATION = 250L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Context context;
    private final LayoutInflater layoutInflater;
    private final ViewGroup notificationListView;
    private final boolean showDebug;

    // android internal resource ids
    // these may differ across builds, so determine and cache them at runtime
    private static final RemoteResourceId expandButtonId = new RemoteResourceId("android:id/expand_button");
    private static final RemoteResourceId alternateExpandTargetId = new RemoteResourceId("android:id/alternate_expand_target");

    private static final RemoteResourceId actionsId = new RemoteResourceId("android:id/actions");


    public NotificationInflater(Context context, LayoutInflater layoutInflater, ViewGroup notificationListView, boolean showDebug) {
        this.context = context;
        this.layoutInflater = layoutInflater;
        this.notificationListView = notificationListView;
        this.showDebug = showDebug;
    }

    private Optional<View> findInContentView(View contentView, RemoteResourceId rri) {
        if (rri == null) return Optional.empty();
        return Optional.ofNullable(contentView.findViewById(rri.getOrFindId(contentView.getResources())));
    }

    public Runnable setupViewTransition(View parent, View from, View to) {
        return () -> {
            int fromHeight = from.getMeasuredHeight();
            int toHeight = to.getMeasuredHeight();
//            Log.d(TAG, "from height = " + fromHeight);
//            Log.d(TAG, "to height = " + toHeight);

            // TODO: animate parent height
            //       or just replace the animation logic altogether, it's not very good

            to.setAlpha(.5f);
            to.setVisibility(View.VISIBLE);
            to.animate()
                    .setDuration(ANIMATION_DURATION)
                    .alpha(1)
                    .start();

            from.animate()
                    .setDuration(ANIMATION_DURATION / 4 * 3)
                    .alpha(.3f)
                    .withEndAction(() -> from.setVisibility(View.GONE))
                    .start();

            if (toHeight == 0) return;  // not rendered


            // morph effect, similar to the actual systemui animation
            int[] oldCoords = new int[2];
            int[] newCoords = new int[2];

            traverse(to, newView -> {
                newView.setAlpha(1);
                newView.setTranslationX(0);
                newView.setTranslationY(0);
            });

            traverse(from, oldView -> {
                if (oldView instanceof ViewGroup) return;    // only animate leafs
                View newView = to.findViewById(oldView.getId());
                if (newView == null) return;

                oldView.getLocationOnScreen(oldCoords);
                newView.getLocationOnScreen(newCoords);

                int xOff = newCoords[0] - oldCoords[0];
                int yOff = newCoords[1] - oldCoords[1];

                oldView.animate()
                        .setDuration(ANIMATION_DURATION)
                        .translationX(xOff)
                        .translationY(yOff)
                        .alpha(.3f)
                        .start();

                newView.setTranslationX(-xOff);
                newView.setTranslationY(-yOff);
                newView.animate()
                        .setDuration(ANIMATION_DURATION)
                        .translationX(0)
                        .translationY(0)
                        .alpha(1)
                        .start();

            });
        };
    }

    private boolean traverse(View view, Function<View, Boolean> callback) {
        if (!callback.apply(view)) return false;

        if (view instanceof ViewGroup viewGroup) {
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                if (!traverse(viewGroup.getChildAt(i), callback)) return false;
            }
        }
        return true;
    }

    private void traverse(View view, Consumer<View> consumer) {
        traverse(view, v -> {
            consumer.accept(v);
            return true;
        });
    }

    private void inflateDebugText(View containerView, StatusBarNotification sbn, NotificationListenerService.RankingMap rankingMap) {
        // because I don't always have a usb cable connected and logging for anomalies
        Notification notif = sbn.getNotification();
        NotificationListenerService.Ranking ranking = new NotificationListenerService.Ranking();
        if (rankingMap == null) {
            Log.wtf(TAG, "RankingMap is null!");
            ranking = null;
        } else if (!rankingMap.getRanking(sbn.getKey(), ranking)) {
            Log.wtf(TAG, "RankingMap is rejecting the key it was paired with!");
            ranking = null;
        }

        TextView debugTextView = containerView.findViewById(R.id.notification_debug_text);
        debugTextView.setVisibility(View.VISIBLE);

        String debugText =
                "key = " + sbn.getKey() + "\n"
                        + "grp key = " + sbn.getGroupKey() + "\n"
                        + "o grp key = " + sbn.getOverrideGroupKey() + "\n"
                        + "id = " + sbn.getId() + "\n"
                        + "tag = " + sbn.getTag() + "\n"
                        + "grp? = " + sbn.isGroup() + "\n"
                        + "appgrp? = " + sbn.isAppGroup() + "\n"
                        + "clearable? = " + sbn.isClearable() + "\n"
                        + "ongoing? = " + sbn.isOngoing() + "\n"
                        + "posted@ = " + sbn.getPostTime() + "\n"
                        + "rank = " + (ranking != null ? ranking.getRank() : "null") + "\n"
                        + "ch imp = " + (ranking != null ? ranking.getChannel().getImportance() : "null") + "\n"
                        + "silent? = " + (ranking != null ? ranking.getChannel().getImportance() == NotificationManager.IMPORTANCE_NONE : "null") + "\n"
                        + "vis = " + notif.visibility  + "\n"
                        + "ch id = " + notif.getChannelId() + "\n"
                        + "grp = " + notif.getGroup() + "\n"
                        + "grp alert = " + notif.getGroupAlertBehavior() + "\n"
                        + "ico_lvl = " + notif.iconLevel + "\n"
                ;

        debugTextView.setText(debugText);
    }

    private View findActionButtonView(ViewGroup actionsView, Notification.Action action, int actionIndex) {
        String actionText = String.valueOf(action.title);

        if (actionsView.getChildCount() > actionIndex) {
            // naive approach (works at time of writing)
            View actionView = actionsView.getChildAt(actionIndex);
            if (actionView instanceof TextView actionButton) {  // they use actual buttons for now, but who knows
                String actionButtonText = String.valueOf(actionButton.getText());
                if (Objects.equals(actionButtonText, actionText)) {
                    // probably the same action
                    // but I believe it's also possible to make multiple action buttons with identical text
                    // either way, there's no way to correct for that without nuking the action button row
                    return actionButton;
                }

                Log.w(TAG, "action button lookup by index failed due to mismatching text");
            }
        } else {
            Log.w(TAG, "action button index out of range, falling back");
        }


        // fallback in case they change shit
        // just literally traverse the whole thing
        View[] actionView = new View[] {null};
        traverse(actionsView, v -> {
            if (v instanceof TextView tv) { // check anything with text
                String actionButtonText = String.valueOf(tv.getText());

                if (Objects.equals(actionButtonText, actionText)) {
                    // probably is the correct one?
                    // the conditions required for this to break would most likely be "unreasonable"
                    actionView[0] = tv;
                    return false;
                }
            }

            return true;
        });

        if (actionView[0] == null) Log.wtf(TAG, "entirely failed to find action button. is there a locale mismatch or something?");
        return actionView[0];
    }

    private void setupTextInputActions(View bigContentView, Notification notif) {
        // fix for text input actions
        // weirdly the other actions work as-is

        if (notif.actions.length == 0) return;
        ViewGroup actionsView = (ViewGroup) findInContentView(bigContentView, actionsId)
                .filter(view -> view instanceof ViewGroup)
                .orElse(null);

        if (actionsView == null) {
            Log.e(TAG, "no actions view in big content view! text inputs may not work.");
            return;
        }

        if (actionsView.getChildCount() == 0) {
            Log.wtf(TAG, "notification has actions but no action buttons");
            return;
        }

        for (int i = 0; i < notif.actions.length; i++) {
            Notification.Action action = notif.actions[i];
            Log.d(TAG, "Action: " + action.title);

            PendingIntent actionIntent = action.actionIntent;
            RemoteInput[] remoteInputs = action.getRemoteInputs();
            if (actionIntent == null || remoteInputs == null) continue;

            for (RemoteInput remoteInput : remoteInputs) {
                Log.d(TAG, "- Input: " + remoteInput.getLabel());
                Log.d(TAG, "- - free-form: " + remoteInput.getAllowFreeFormInput());
                Log.d(TAG, "- - allowed types: " + remoteInput.getAllowedDataTypes());

                // TODO: support choices and dataOnly inputs?
                if (!remoteInput.getAllowFreeFormInput()) continue;

                View actionButton = findActionButtonView(actionsView, action, i);
                if (actionButton == null) {
                    Log.w(TAG, "failed to find action button, the text input action probably won't work");
                    continue;
                }

                actionButton.setOnClickListener(v -> {

                    View actionInputView = layoutInflater.inflate(R.layout.layout_notification_text_input_dialog, actionsView, false);

                    EditText textInput = actionInputView.findViewById(R.id.action_text_input);
                    ImageButton submitButton = actionInputView.findViewById(R.id.action_submit_button);

                    textInput.setHint(remoteInput.getLabel());

                    AlertDialog inputDialog = new AlertDialog.Builder(context)
                            .setView(actionInputView)
                            .create();

                    submitButton.setOnClickListener(vv -> {
                        Intent intent = new Intent();
                        Bundle bundle = new Bundle();
                        bundle.putCharSequence(remoteInput.getResultKey(), textInput.getText());
                        RemoteInput.addResultsToIntent(remoteInputs, intent, bundle);

                        try {
                            actionIntent.send(context, 0, intent);
                        } catch (PendingIntent.CanceledException e) {
                            Log.e(TAG, "notification text input action intent expired", e);
                            showUnexpectedError(context, R.string.notification_action_error_title, e);
                        }

                        inputDialog.dismiss();
                    });

                    inputDialog.show();

                });

                break;  // I assume there can't be multiple free-form remote inputs?
            }
        }
    }

    private void setupExpandContract(View notificationFrame, View bigContentView, View contentView) {
        Runnable expandTransition = setupViewTransition(notificationFrame, contentView, bigContentView);
        Runnable collapseTransition = setupViewTransition(notificationFrame, bigContentView, contentView);

        findInContentView(contentView, expandButtonId)
                .ifPresent(ev -> ev.setOnClickListener(v -> expandTransition.run()));
        findInContentView(contentView, alternateExpandTargetId)
                .ifPresent(ev -> ev.setOnClickListener(v -> expandTransition.run()));

        findInContentView(bigContentView, expandButtonId)
                .ifPresent(ev -> ev.setOnClickListener(v -> collapseTransition.run()));
        findInContentView(bigContentView, alternateExpandTargetId)
                .ifPresent(ev -> ev.setOnClickListener(v -> collapseTransition.run()));
    }

    private void setupOnClick(View notificationFrame, Notification notif) {
        PendingIntent onClickIntent = notif.contentIntent;
        if (onClickIntent != null) {
            notificationFrame.setOnClickListener(v -> {
                try {
                    onClickIntent.send();
                } catch (PendingIntent.CanceledException e) {
                    Log.w(TAG, "notification pending intent expired", e);
                    if (showDebug)
                        showDebugError(context, new RuntimeException("notification pending intent is expired", e));
                }
            });
        }

    }

    private void inflateContentViews(ViewGroup notificationFrame, StatusBarNotification sbn) {
        // this is a hack.
        // not a very good one (see Notification.Builder.createContentView() documentation)
        // but it does work, it does roughly what I want, and it's a lot of work to rebuild from scratch (I've tried in the past)
        try {
            Log.d(TAG, "generating content views");
            Notification notif = sbn.getNotification();
            Notification.Builder builder = Notification.Builder.recoverBuilder(context, notif);
            View bigContentView = builder.createBigContentView().apply(context.getApplicationContext(), notificationFrame);
            View contentView = builder.createContentView().apply(context.getApplicationContext(), notificationFrame);
//            Log.d(TAG, "tree:\n" + debugTraverse(contentView));
//            Log.d(TAG, "bigtree:\n" + debugTraverse(bigContentView));

            if (notif.actions != null) setupTextInputActions(bigContentView, notif);
            setupExpandContract(notificationFrame, bigContentView, contentView);
            setupOnClick(notificationFrame, notif);

            // terrible fix for content view taking up entire screen height
            // I've tried to fix this a number of other ways but nothing works
            // TODO: fix?
            ViewGroup.LayoutParams lp = contentView.getLayoutParams();
            lp.height = (int) dpToPx(context, 76);

            notificationFrame.addView(bigContentView);
            notificationFrame.addView(contentView);

            // ensure both get drawn before hiding one
            // it's for the animation, so not the end of the world if it doesn't actually get drawn before the callback
            handler.post(() -> bigContentView.setVisibility(View.GONE));

        } catch (Throwable t) {
            Log.e(TAG, "failed to render notif", t);
            // TODO: fallback rendering
        }
    }

    public View inflate(StatusBarNotification sbn, NotificationListenerService.RankingMap rankingMap) {
        View containerView = layoutInflater.inflate(R.layout.layout_notification_container, notificationListView, false);
        if (showDebug) inflateDebugText(containerView, sbn, rankingMap);
        inflateContentViews(containerView.findViewById(R.id.notification_frame), sbn);
        return containerView;
    }


    private static String debugTraverse(View view, int curDepth) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < curDepth; i++) sb.append(" -- ");

        sb
                .append(view.getLayoutParams().getClass().getName())
                .append(" ")
                .append(view.getLayoutParams().width)
                .append("x")
                .append(view.getLayoutParams().height)
                .append(" - ")
                .append("oc?=")
                .append(view.hasOnClickListeners())
                .append(" - ")
                .append(view);

        if (view instanceof ViewGroup viewGroup) {
            for (int i = 0; i < viewGroup.getChildCount(); i++) sb
                    .append("\n")
                    .append(debugTraverse(viewGroup.getChildAt(i), curDepth + 1));
        }

        return sb.toString();
    }

    private static String debugTraverse(View view) {
        return debugTraverse(view, 0);
    }
}
