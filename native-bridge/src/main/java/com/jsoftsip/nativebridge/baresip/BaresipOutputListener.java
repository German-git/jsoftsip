package com.jsoftsip.nativebridge.baresip;

@FunctionalInterface
public interface BaresipOutputListener {

    void onLine(String line);
}