package com.example.appdt;

import android.app.Application;
import android.content.SharedPreferences;

import com.example.appdt.repository.SecurePrefs;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.UUID;

public final class DocuMindApplication extends Application {
    public static final String PROCESS_BOOT_ID = UUID.randomUUID().toString();

    @Override public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
        AppCheckInstaller.install(FirebaseAppCheck.getInstance());
        SharedPreferences session = SecurePrefs.open(this, "documind_firebase_session");
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && !session.getBoolean("remember", false) && user.isEmailVerified()) {
            FirebaseAuth.getInstance().signOut();
            session.edit().remove("boot_id").apply();
        }
    }
}
