package com.dhoomilbsheta.authtest.oauth;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.dhoomilbsheta.authtest.R;
import com.google.gson.Gson;

import net.openid.appauth.AppAuthConfiguration;
import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.ClientAuthentication;
import net.openid.appauth.ResponseTypeValues;
import net.openid.appauth.TokenRequest;
import net.openid.appauth.TokenResponse;
import net.openid.appauth.browser.BrowserWhitelist;
import net.openid.appauth.browser.VersionedBrowserMatcher;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

import androidx.browser.customtabs.CustomTabsIntent;

public class AuthHelper {
    private final AuthHelper instance = null;
    private final String TAG = "AuthManager";

    private AuthorizationService authService;
    private AuthStateManager authStateManager;
    private Configuration configuration;

    private Context context;

    private final AtomicReference<String> clientId = new AtomicReference<>();
    private final AtomicReference<AuthorizationRequest> authRequest = new AtomicReference<>();
    private final AtomicReference<CustomTabsIntent> authIntent = new AtomicReference<>();

    public AuthHelper(Context context) {
        this.context = context;
        authStateManager = AuthStateManager.getInstance(context);
        loadConfiguration();

        if (!isAuthorised()) {
            initializeAuth();
        }
        Log.d(TAG, "User is already authenticated, proceeding to get token");
    }

    public boolean isAuthorised() {
        return authStateManager.getCurrent().isAuthorized() && configuration != null;
    }

    private void initializeAuth() {
        Log.d(TAG, "Initializing Auth");
        createAuthorizationService();

        if (authStateManager.getCurrent().getAuthorizationServiceConfiguration() != null) {
            Log.d(TAG, "auth config already established");
            initializeClient();
            return;
        }

        Log.d(TAG, "Retrieving OpenID discovery doc");
        AuthorizationServiceConfiguration.fetchFromUrl(
                configuration.getDiscoveryUri(),
                this::handleDiscoveryResult,
                CustomConnectionBuilder.INSTANCE
        );
    }

    private void initializeClient() {
        Log.d(TAG, "Client ID: " + configuration.getClientId());
        clientId.set(configuration.getClientId());

        AuthorizationRequest.Builder builder = new AuthorizationRequest.Builder(
                authStateManager.getCurrent().getAuthorizationServiceConfiguration(),
                clientId.get(),
                ResponseTypeValues.CODE,
                configuration.getRedirectUri()
        );
        builder.setScope(configuration.getAuthorizationScope());
        authRequest.set(builder.build());
    }

    private void handleDiscoveryResult(AuthorizationServiceConfiguration config, AuthorizationException ex) {
        if (config == null) {
            Log.i(TAG, "Failed to retrieve discovery document", ex);
            return;
        }

        Log.d(TAG, "Discovery document retrieved");
        authStateManager.replace(new AuthState(config));
        initializeClient();
    }

    private void createAuthorizationService() {
        Log.d(TAG, "Creating authorization service");
        AppAuthConfiguration.Builder builder = new AppAuthConfiguration.Builder();
        builder.setBrowserMatcher(new BrowserWhitelist(VersionedBrowserMatcher.CHROME_BROWSER,
                VersionedBrowserMatcher.FIREFOX_BROWSER));
        builder.setConnectionBuilder(CustomConnectionBuilder.INSTANCE);
        authService = new AuthorizationService(context, builder.build());
        authRequest.set(null);
    }

    private AuthorizationService getTokenExchangeService() {
        AppAuthConfiguration.Builder builder = new AppAuthConfiguration.Builder();
        builder.setConnectionBuilder(CustomConnectionBuilder.INSTANCE);
        return new AuthorizationService(context, builder.build());
    }

    public void doAuthorization(DoAuthCallback callback) {
        Intent intent = authService.getAuthorizationRequestIntent(authRequest.get());
        callback.start(intent);
    }

    private void exchangeAuthorizationCode(AuthorizationResponse response) {
        performTokenRequest(response.createTokenExchangeRequest(), this::handleTokenResponse);
    }

    private void performTokenRequest(TokenRequest request, AuthorizationService.TokenResponseCallback callback) {
        ClientAuthentication clientAuthentication;
        try {
            clientAuthentication = authStateManager.getCurrent().getClientAuthentication();
        } catch (ClientAuthentication.UnsupportedAuthenticationMethod unsupportedAuthenticationMethod) {
            Log.d(TAG, "Token request cannot be made, client authentication for the token "
                    + "endpoint could not be constructed (%s)", unsupportedAuthenticationMethod);
            unsupportedAuthenticationMethod.printStackTrace();
            return;
        }

        getTokenExchangeService().performTokenRequest(request, clientAuthentication, callback);
    }

    private void handleTokenResponse(TokenResponse tokenResponse, AuthorizationException ex) {
        authStateManager.updateAfterTokenResponse(tokenResponse, ex);
        Log.d(TAG, "ACCESS TOKEN: " + authStateManager.getCurrent().getAccessToken());
    }

    private void loadConfiguration() {
        try {
            Gson gson = new Gson();
            InputStream stream = context.getResources().openRawResource(R.raw.auth_config);
            String conf = Utils.readInputStream(stream);
            configuration = gson.fromJson(conf, Configuration.class);
            Log.d(TAG, configuration.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void refreshAccessToken() {
        Log.d(TAG, "Refreshing Access Token");
        performTokenRequest(authStateManager.getCurrent().createTokenRefreshRequest(), this::handleTokenResponse);
    }

    public String getValidAccessToken() {
        if (authStateManager.getCurrent().getNeedsTokenRefresh()) {
            Log.d(TAG, "Access token expired");
            refreshAccessToken();
        } else {
            Log.d(TAG, "Access token still valid");
        }
        return authStateManager.getCurrent().getAccessToken();
    }

    public void handlePostAuthFlow(Intent data) {
        AuthorizationResponse response = AuthorizationResponse.fromIntent(data);
        AuthorizationException exception = AuthorizationException.fromIntent(data);
        if (response != null || exception != null) {
            authStateManager.updateAfterAuthorization(response, exception);
        }

        if (response != null && response.authorizationCode != null) {
            // authorization code exchange is required
            authStateManager.updateAfterAuthorization(response, exception);
            exchangeAuthorizationCode(response);
        } else if (exception != null) {
            Log.d(TAG, "Authorization flow failed: " + exception.getMessage());
        } else {
            Log.d(TAG, "No authorization state retained - reauthorization required");
            initializeAuth();
        }
    }

    public void disposeAuthService() {
        if (authService != null)
            authService.dispose();
    }
}
