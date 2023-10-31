package com.example.tictactoe;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.Button;
import android.content.Intent;
import  android.view.View;

public class lastActivity extends AppCompatActivity {
    public Button button3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_last);

        button3 = (Button) findViewById(R.id.btn3);

                button3.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(lastActivity.this, MainActivity2.class);
                        startActivity(intent);
                    }
                });
    }
  }