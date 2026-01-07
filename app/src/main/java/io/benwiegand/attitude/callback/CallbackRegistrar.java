package io.benwiegand.attitude.callback;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CallbackRegistrar<T> {
    private static final String TAG = CallbackRegistrar.class.getSimpleName();

    private final List<T> callbacks = new ArrayList<>();
    private final List<Consumer<T>> initialCallers;

    /**
     * creates a callback registrar
     * @param initialCallers list of callers that will be used to initialize new callbacks upon registration. can be null
     */
    public CallbackRegistrar(List<Consumer<T>> initialCallers) {
        this.initialCallers = initialCallers;
    }


    private void callCallbackLocked(T callback, Consumer<T> caller) {
        try {
            caller.accept(callback);
        } catch (Throwable t) {
            Log.wtf(TAG, "exception thrown by callback!!!", t);
        }
    }


    public void registerCallback(T callback) {
        assert callback != null;
        synchronized (callbacks) {
            if (callbacks.contains(callback)) {
                Log.wtf(TAG, "attempted to register callback which was already registered", new RuntimeException());
                assert false;
                return;
            }

            callbacks.add(callback);

            // primarily to flush any existing states the owner of the registrar wants to always inform callbacks of
            if (initialCallers == null) return;
            for (Consumer<T> initialCall : initialCallers) {
                callCallbackLocked(callback, initialCall);
            }
        }
    }

    public boolean unregisterCallback(T callback) {
        assert callback != null;
        synchronized (callbacks) {
            return callbacks.remove(callback);
        }
    }

    public void callCallbacks(Consumer<T> caller) {
        synchronized (callbacks) {
            for (T callback : callbacks) {
                callCallbackLocked(callback, caller);
            }
        }
    }

}
