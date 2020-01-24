package com.example.bluetoothvoicecontrol;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.davidmiguel.numberkeyboard.NumberKeyboard;
import com.davidmiguel.numberkeyboard.NumberKeyboardListener;

public class KeypadActivity extends AppCompatActivity {

    static final String NUMBER = "number";
    private static final String LOG_TAG = "KeypadActivityLogTag";

    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_keypad);
        textView = findViewById(R.id.numberView);
        NumberKeyboard numberKeyboard = findViewById(R.id.keypad);
        numberKeyboard.setListener(new NumberKeyboardListener() {
            @Override
            public void onNumberClicked(int i) {
                Log.d(LOG_TAG, "Number: " + i );
                StringBuilder stringBuilder = new StringBuilder(textView.getText().toString());
                if (stringBuilder.length() > 0) {
                    stringBuilder.append(' ');
                    stringBuilder.append(' ');
                }
                stringBuilder.append(i);
                textView.setText(stringBuilder.toString());
                if (stringBuilder.length() == 10) {
                    setResult(RESULT_OK, new Intent().putExtra(NUMBER,
                            Integer.parseInt(stringBuilder.toString().replace("  ", ""))));
                    finish();
                }
            }

            @Override
            public void onLeftAuxButtonClicked() {

            }

            @Override
            public void onRightAuxButtonClicked() {
                StringBuilder stringBuilder = new StringBuilder(textView.getText().toString());
                if (stringBuilder.length() > 1) {
                    stringBuilder.delete(stringBuilder.length() - 3, stringBuilder.length());

                } else if (stringBuilder.length() > 0) {
                    stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                }
                textView.setText(stringBuilder.toString());
            }
        });
    }

}
