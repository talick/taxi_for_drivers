package com.example.talgat.distancecounter;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.Intent;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;

import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A login screen that offers login via email/password.
 */
public class LoginActivity extends AppCompatActivity implements View.OnClickListener{

    private static final String TAG = LoginActivity.class.getSimpleName();

    private static final String KEY_VERIFY_IN_PROGRESS = "key_verify_in_progress";


    private static final int STATE_INITIALIZED = 1;
    private static final int STATE_CODE_SENT = 2;
    private static final int STATE_SIGNIN_FAILED = 5;


    private FirebaseAuth mAuth;

    private boolean mVerificationInProgress = false;
    private String mVerificationId;

    private EditText mPhoneNumberTextView;
    private EditText mVerificationCodeTextView;

    private View mProgressView;
    private View mLoginFormView;
    private View mConfirmFormView;





    private PhoneAuthProvider.ForceResendingToken mResendToken;
    private PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        // Set up the login form.


        if (savedInstanceState != null) {
            onRestoreInstanceState(savedInstanceState);
        }

        mPhoneNumberTextView = findViewById(R.id.field_phone_number);
        mVerificationCodeTextView = findViewById(R.id.verification_code);

        mLoginFormView = findViewById(R.id.login_form);
        mConfirmFormView = findViewById(R.id.confirm_form);
        mProgressView = findViewById(R.id.login_progress);

        Button mPhoneNumberSignInButton = findViewById(R.id.sign_in_button);
        Button mNextButton = findViewById(R.id.next_button);

        mNextButton.setOnClickListener(this);
        mPhoneNumberSignInButton.setOnClickListener(this);

        mAuth = FirebaseAuth.getInstance();
        mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            @Override
            public void onVerificationCompleted(PhoneAuthCredential credential) {

                Log.d(TAG, "onVerificationCompleted:" + credential);

                mVerificationInProgress = false;

                signInWithPhoneAuthCredential(credential);


            }
            @Override
            public void onVerificationFailed(FirebaseException e) {

                Log.w(TAG, "onVerificationFailed", e);

                mVerificationInProgress = false;


                if (e instanceof FirebaseAuthInvalidCredentialsException) {

                    mPhoneNumberTextView.setError("Invalid phone number.");

                } else if (e instanceof FirebaseTooManyRequestsException) {
                    Snackbar.make(findViewById(android.R.id.content), "Quota exceeded.",
                            Snackbar.LENGTH_SHORT).show();
                    // [END_EXCLUDE]
                }


                //updateUI(STATE_VERIFY_FAILED);

            }

            @Override
            public void onCodeSent(String verificationId,
                                   PhoneAuthProvider.ForceResendingToken token) {
                // The SMS verification code has been sent to the provided phone number, we
                // now need to ask the user to enter the code and then construct a credential
                // by combining the code with a verification ID.
                Log.d(TAG, "onCodeSent:" + verificationId);

                // Save verification ID and resending token so we can use them later
                mVerificationId = verificationId;
                mResendToken = token;


                updateUI(STATE_CODE_SENT);
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (mVerificationInProgress && validatePhoneNumber()) {
            startPhoneNumberVerificaiton(mPhoneNumberTextView.getText().toString());
        }

    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        outState.putBoolean(KEY_VERIFY_IN_PROGRESS, mVerificationInProgress);
    }


    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mVerificationInProgress = savedInstanceState.getBoolean(KEY_VERIFY_IN_PROGRESS);
    }

    private boolean validatePhoneNumber() {
        String phoneNumber = mPhoneNumberTextView.getText().toString();
        if (TextUtils.isEmpty(phoneNumber)) {
            mPhoneNumberTextView.setError("Invalid phone number.");
            return false;
        }

        return true;
    }

    private void startPhoneNumberVerificaiton(String phoneNumber) {

        Log.e(TAG, "startVerification");
        PhoneAuthProvider.getInstance().verifyPhoneNumber(
                phoneNumber,
                60,
                TimeUnit.SECONDS,
                this,
                mCallbacks
        );

        mVerificationInProgress = true;
    }

    private void verifyPhoneNumberWithCode(String verificationId, String code) {
        // [START verify_with_code]
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);

        signInWithPhoneAuthCredential(credential);
    }


    private void resendVerificationCode(String phoneNumber,
                                        PhoneAuthProvider.ForceResendingToken token) {
        PhoneAuthProvider.getInstance().verifyPhoneNumber(
                phoneNumber,        // Phone number to verify
                60,                 // Timeout duration
                TimeUnit.SECONDS,   // Unit of timeout
                this,               // Activity (for callback binding)
                mCallbacks,         // OnVerificationStateChangedCallbacks
                token);             // ForceResendingToken from callbacks
    }


    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.e(TAG, "signInWithCredential:success");

                            addToDB(task.getResult().getUser());
                            finishLogin();
                        } else {
                            Log.e(TAG, "signInWithCreedential:failure", task.getException());

                            if (task.getException() instanceof FirebaseAuthInvalidCredentialsException) {
                                //mVerificationField.setError("Invalid code.");
                            }

                            updateUI(STATE_SIGNIN_FAILED);
                        }
                    }
                });
    }

    private void addToDB(final FirebaseUser user) {
        final FirebaseFirestore db = FirebaseFirestore.getInstance();
        final String userId = user.getUid();

        db.collection("drivers").whereEqualTo("id", userId)
                .limit(1)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            if (task.getResult().isEmpty()) {
                                Map<String, Object> map = new HashMap<>();
                                map.put("id", userId);
                                map.put("phone", user.getPhoneNumber());

                                db.collection("drivers").document(userId)
                                        .set(map);

                            }

                        }
                    }
                });
    }
    private void updateUI(int uiState) {
        switch (uiState) {

            case STATE_CODE_SENT:
                // Code sent state, show the verification field, the
                mLoginFormView.setVisibility(View.GONE);
                mConfirmFormView.setVisibility(View.VISIBLE);
                break;
            case STATE_INITIALIZED:
                mLoginFormView.setVisibility(View.VISIBLE);
                mConfirmFormView.setVisibility(View.GONE);
                mPhoneNumberTextView.setText("");

                break;
            case STATE_SIGNIN_FAILED:
                mVerificationCodeTextView.setError("Неправильно введен код");
                Log.e(TAG, "failed");

                break;
        }
    }


    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show, final View view) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

        view.setVisibility(show ? View.GONE : View.VISIBLE);
        view.animate().setDuration(shortAnimTime).alpha(
                show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setVisibility(show ? View.GONE : View.VISIBLE);
            }
        });

        mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
        mProgressView.animate().setDuration(shortAnimTime).alpha(
                show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.next_button:
                if (!validatePhoneNumber()) {
                    return;
                }
                startPhoneNumberVerificaiton(mPhoneNumberTextView.getText().toString());
                updateUI(STATE_CODE_SENT);
                break;

            case R.id.sign_in_button:
                String code = mVerificationCodeTextView.getText().toString();
                if (TextUtils.isEmpty(code)) {
                    mVerificationCodeTextView.setError("Cannot be empty.");
                    return;
                }

                verifyPhoneNumberWithCode(mVerificationId, code);
                break;

        }
    }

    private void finishLogin() {
        Intent returnIntent = new Intent(this, MainActivity.class);
        startActivity(returnIntent);
        finish();

    }

    @Override
    public void onBackPressed() {
        if (mLoginFormView.getVisibility() == View.GONE) {
            mLoginFormView.setVisibility(View.VISIBLE);
        }
        super.onBackPressed();
    }
}