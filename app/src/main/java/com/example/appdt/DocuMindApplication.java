package com.example.appdt;

import android.app.Application;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;

public final class DocuMindApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
        AppCheckInstaller.install(FirebaseAppCheck.getInstance());
    }
}
