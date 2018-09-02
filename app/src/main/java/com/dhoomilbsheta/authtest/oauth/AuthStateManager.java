package com.dhoomilbsheta.authtest.oauth;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import android.util.Log;

import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.TokenResponse;

import org.json.JSONException;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class AuthStateManager {
    private static final AtomicReference<WeakReference<AuthStateManager>> INSTANCE_REF =
            new AtomicReference<>(new WeakReference<>(null));
    private static final String TAG = "AuthStateManager";

    private static final String STORE_NAME = "AuthState";
    private static final String KEY_STATE = "state";

    private final SharedPreferences preferences;
    private final ReentrantLock lock;
    private final AtomicReference<AuthState> currentAuthState;

    private AuthStateManager(Context context) {
        preferences = context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE);
        lock = new ReentrantLock();
        currentAuthState = new AtomicReference<>();
    }

    @AnyThread
    public static AuthStateManager getInstance(Context context) {
        AuthStateManager authStateManager = INSTANCE_REF.get().get();
        if (authStateManager == null) {
            authStateManager = new AuthStateManager(context.getApplicationContext());
            INSTANCE_REF.set(new WeakReference<>(authStateManager));
        }
        return authStateManager;
    }

    private void writeState(AuthState state) {
        lock.lock();
        try {
            SharedPreferences.Editor editor = preferences.edit();
            if (state == null) editor.remove(KEY_STATE);
            else editor.putString(KEY_STATE, state.jsonSerializeString());

            if (!editor.commit()) {
                throw new IllegalStateException("Failed to write state to shared prefs");
            }
        } finally {
            lock.unlock();
        }
    }

    private AuthState readState() {
        lock.lock();
        try {
            String currentState = preferences.getString(KEY_STATE, null);
            if (currentState == null) {
                return new AuthState();
            }

            try {
                return AuthState.jsonDeserialize(currentState);
            } catch (JSONException ex) {
                Log.w(TAG, "Failed to deserialize stored auth state - discarding");
                return new AuthState();
            }
        } finally {
            lock.unlock();
        }
    }

    public AuthState getCurrent() {
        if (currentAuthState.get() != null) {
            return currentAuthState.get();
        }

        AuthState authState = readState();
        if (currentAuthState.compareAndSet(null, authState)) {
            return authState;
        } else {
            return currentAuthState.get();
        }
    }

    public AuthState replace(@NonNull AuthState state) {
        writeState(state);
        currentAuthState.set(state);
        return state;
    }

    public AuthState updateAfterAuthorization(AuthorizationResponse response, AuthorizationException ex) {
        AuthState current = getCurrent();
        current.update(response, ex);
        return replace(current);
    }

    public AuthState updateAfterTokenResponse(TokenResponse response, AuthorizationException ex) {
        AuthState current = getCurrent();
        current.update(response, ex);
        return replace(current);
    }

}
