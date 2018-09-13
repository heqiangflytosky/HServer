package com.hq.android.hserver.sdk.nano;

public enum Method {
    GET,
    PUT,
    POST,
    DELETE,
    HEAD,
    OPTIONS;

    public static Method lookup(String method) {
        if (method == null)
            return null;

        try {
            return valueOf(method);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return null;
        }
    }
}
