package com.openforge.capacitorgameconnect;

public interface GetMakeGooglePlayAvailableResultCallback {
    void success(boolean enabled);
    void failed(boolean enabled, String message);
}