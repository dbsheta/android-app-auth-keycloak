package com.dhoomilbsheta.authtest.oauth;

import android.net.Uri;

import com.google.gson.annotations.SerializedName;


public class Configuration {

    @SerializedName("discovery_uri")
    private String discoveryUri;

    @SerializedName("client_id")
    private String clientId;

    @SerializedName("redirect_uri")
    private String redirectUri;

    @SerializedName("authorization_scope")
    private String authorizationScope;

    @SerializedName("https_required")
    private boolean httpsRequired;

    public Uri getDiscoveryUri() {
        return Uri.parse(discoveryUri);
    }

    public void setDiscoveryUri(String discoveryUri) {
        this.discoveryUri = discoveryUri;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Uri getRedirectUri() {
        return Uri.parse(redirectUri);
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getAuthorizationScope() {
        return authorizationScope;
    }

    public void setAuthorizationScope(String authorizationScope) {
        this.authorizationScope = authorizationScope;
    }

    public boolean isHttpsRequired() {
        return httpsRequired;
    }

    public void setHttpsRequired(boolean httpsRequired) {
        this.httpsRequired = httpsRequired;
    }

    @Override
    public String toString() {
        return "Configuration{" +
                "discoveryUri='" + discoveryUri + '\'' +
                ", clientId='" + clientId + '\'' +
                ", redirectUri='" + redirectUri + '\'' +
                ", authorizationScope='" + authorizationScope + '\'' +
                ", httpsRequired=" + httpsRequired +
                '}';
    }
}
