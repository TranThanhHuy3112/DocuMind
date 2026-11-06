package com.example.appdt.ui.auth;

import android.app.Application;
import android.content.Context;

import androidx.lifecycle.Observer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.appdt.model.AuthResult;
import com.example.appdt.model.OtpChallenge;
import com.example.appdt.model.UserModel;
import com.example.appdt.repository.AuthRepository;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class AuthViewModelTest {
    @Test public void registrationAndVerificationPublishStateWithoutBlockingUi() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = "documind_auth_viewmodel_test_" + UUID.randomUUID();
        Application application = (Application) context.getApplicationContext();
        AuthViewModel viewModel = new AuthViewModel(application,
                new AuthRepository(context, name, System::currentTimeMillis));
        CountDownLatch registered = new CountDownLatch(1);
        CountDownLatch verified = new CountDownLatch(1);
        Observer<AuthResult<OtpChallenge>> registrationObserver = result -> {
            if (result.getStatus() == AuthResult.Status.SUCCESS) registered.countDown();
        };
        Observer<AuthResult<UserModel>> verificationObserver = result -> {
            if (result.getStatus() == AuthResult.Status.SUCCESS) verified.countDown();
        };
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                viewModel.getRegisterState().observeForever(registrationObserver);
                viewModel.getOtpState().observeForever(verificationObserver);
                viewModel.register("Test User", "user@example.com", "StrongPass1",
                        "StrongPass1");
            });
            assertTrue("Registration should finish", registered.await(10, TimeUnit.SECONDS));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            OtpChallenge challenge = viewModel.getRegisterState().getValue().getValue();
            assertNotNull(challenge);
            Long remaining = viewModel.getCountdownTimeRemaining().getValue();
            assertNotNull(remaining);
            assertTrue(remaining > 0 && remaining <= 60);

            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    viewModel.verifyOtp(challenge.getEmail(), challenge.getCode()));
            assertTrue("Verification should finish", verified.await(10, TimeUnit.SECONDS));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals(AuthResult.Status.SUCCESS,
                    viewModel.getOtpState().getValue().getStatus());
            assertNotNull(viewModel.getCurrentUser().getValue());
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                viewModel.getRegisterState().removeObserver(registrationObserver);
                viewModel.getOtpState().removeObserver(verificationObserver);
                viewModel.stopCountdown();
                viewModel.onCleared();
            });
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit();
        }
    }
}
