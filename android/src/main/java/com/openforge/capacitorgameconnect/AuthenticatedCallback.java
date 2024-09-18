package com.openforge.capacitorgameconnect;

public interface AuthenticatedCallback {
    void success(boolean isAuthenticated);
    void error(String message);
}
