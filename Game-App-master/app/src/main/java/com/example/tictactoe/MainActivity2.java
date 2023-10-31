package com.example.tictactoe;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

import org.jetbrains.annotations.NotNull;

public class MainActivity2 extends AppCompatActivity {
    public  Button button;
    public TextView text;
    public EditText mUser;
    public EditText mPassw;
    public FirebaseAuth fauth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_main2);

        button = (Button) findViewById(R.id.btn);
        text = (TextView) findViewById(R.id.reg);
        mUser = (EditText) findViewById(R.id.editTextTextPersonName2);
        mPassw = (EditText) findViewById(R.id.editTextTextPassword);
        fauth = FirebaseAuth.getInstance();

        text.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity2.this, register.class);
                startActivity(intent);
            }
        });

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = mUser.getText().toString().trim();
                String password2 = mPassw.getText().toString().trim();

                if (TextUtils.isEmpty(username)) {
                    mUser.setError("username is Required to Login");

                    return;
                }
                if (TextUtils.isEmpty(password2)) {
                    mPassw.setError("password is Required to Login");
                    return;
                }

                fauth.signInWithEmailAndPassword(username, password2).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull @NotNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Toast.makeText(MainActivity2.this, "LoggedIn successfully", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(getApplicationContext(), MainActivity.class));
                        } else {

                            Toast.makeText(MainActivity2.this, "Login failed. Try Again" +task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    ;

                });
                startActivity(new Intent(getApplicationContext(), MainActivity.class));
            }

            ;

        });

    }
}