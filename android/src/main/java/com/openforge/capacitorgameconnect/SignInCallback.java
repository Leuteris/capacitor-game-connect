package com.openforge.capacitorgameconnect;

public interface SignInCallback {
    void success(boolean isAuthenticated);
    void error(String message);
}
