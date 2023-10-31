package com.example.tictactoe;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

public class register extends AppCompatActivity {
    public Button regbut;
    public ProgressBar prog;
    public FirebaseAuth fauth;
    public EditText mEmail;
    public  EditText mPass;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_register);

       mEmail = ( EditText)findViewById(R.id.eml);
       mPass =( EditText)findViewById(R.id.pswrd);
       fauth = FirebaseAuth.getInstance();



        regbut = (Button) findViewById(R.id.regbtn);

        regbut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = mEmail.getText().toString().trim();
                String password = mPass.getText().toString().trim();

                if(TextUtils.isEmpty(email))
                {
                    mEmail.setError("Email is Required to create an account");

                    return;
                }
                if(TextUtils.isEmpty(password))
                {
                    mPass.setError("Password is Required to create an account");
                    return;
                }


                if(password.length() < 6)
                {
                    mPass.setError("password must be greater than 6 characters");
                    return;
                }

                setProgressBarVisibility(true);
                //for registering the user in firebase

                fauth.createUserWithEmailAndPassword(email,password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull @org.jetbrains.annotations.NotNull Task<AuthResult> task) {
                        if(task.isSuccessful())
                        {
                            Toast.makeText(register.this,"account created successfully", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(getApplicationContext(),MainActivity.class));
                        }
                        else
                        {
                            Toast.makeText(register.this,"account creation failed. Try Again", Toast.LENGTH_SHORT).show();
                        }
                };



                    });

                startActivity(new Intent(getApplicationContext(),MainActivity.class));
                };
        });



    }
}