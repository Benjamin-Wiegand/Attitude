package io.benwiegand.attitude.adb;

import android.content.Context;

import androidx.annotation.NonNull;

import java.security.PrivateKey;
import java.security.cert.Certificate;

import io.benwiegand.attitude.R;
import io.github.muntashirakon.adb.AbsAdbConnectionManager;

public class AdbConnectionManager extends AbsAdbConnectionManager {

    private final PrivateKey privateKey;
    private final Certificate certificate;
    private final String deviceName;

    public AdbConnectionManager(Context context, PrivateKey privateKey, Certificate certificate) {
        this.privateKey = privateKey;
        this.certificate = certificate;
        this.deviceName = context.getString(R.string.app_name);
    }

    @NonNull
    @Override
    protected PrivateKey getPrivateKey() {
        return privateKey;
    }

    @NonNull
    @Override
    protected Certificate getCertificate() {
        return certificate;
    }

    @NonNull
    @Override
    protected String getDeviceName() {
        return deviceName;
    }
}
