package com.example.bluetoothvoicecontrol;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_CODE_SPEECH_INPUT = 100;
    private static final int REQ_CODE_KEYPAD_ACTIVITY = 101;
    private static final int REQ_CODE_TURN_BLUETOOTH_ON = 102;

    private static final int OPEN_COMMAND = 49;
    private static final int CLOSE_COMMAND = 50;

    private static final String LOG_TAG = "MainActivityLogTag";

    private static final String OPEN_PASSWORD_REGEX = "^[Oo][Pp][Ee][Nn]\\s*[Dd][Oo][Oo][Rr]$";
    private static final String CLOSE_PASSWORD_REGEX = "^[Cc][Ll][Oo][Ss][Ee]\\s*[Dd][Oo][Oo][Rr]$";

    private BluetoothDevice bluetoothDevice;
    private ConnectThread connectThread;
    private int currentCommand = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        for (BluetoothDevice bluetoothDevice : bluetoothAdapter.getBondedDevices()) {
            if ("HC05".equals(bluetoothDevice.getName())) {
                this.bluetoothDevice = bluetoothDevice;
                break;
            }
        }
        if (bluetoothAdapter.isEnabled()) {
            initialize();
        } else {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQ_CODE_TURN_BLUETOOTH_ON);
        }
    }

    public void onVoice(View view) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                getString(R.string.speech_prompt));
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(),
                    getString(R.string.voice),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQ_CODE_SPEECH_INPUT:
                if (resultCode == RESULT_OK && data != null) {
                    ArrayList<String> result = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (result != null) {
                        Log.d(LOG_TAG, "Voice Data: " + result);
                        String password = result.get(0).trim();
                        Pattern openPattern = Pattern.compile(OPEN_PASSWORD_REGEX);
                        Pattern closePattern = Pattern.compile(CLOSE_PASSWORD_REGEX);
                        Matcher openMatcher = openPattern.matcher(password);
                        Matcher closeMatcher = closePattern.matcher(password);
                        if (openMatcher.matches()) {
                            Toast.makeText(this, R.string.command_match, Toast.LENGTH_SHORT)
                                    .show();
                            openKeypadActivity();
                            currentCommand = OPEN_COMMAND;
                        } else if (closeMatcher.matches()) {
                            Toast.makeText(this, R.string.command_match, Toast.LENGTH_SHORT)
                                    .show();
                            openKeypadActivity();
                            currentCommand = CLOSE_COMMAND;
                        } else {
                            Toast.makeText(this, R.string.unknown_command,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                }
                break;
            case REQ_CODE_KEYPAD_ACTIVITY:
                if (resultCode == RESULT_OK && data != null) {
                    int pin = data.getIntExtra(KeypadActivity.NUMBER, -1);
                    Log.d(LOG_TAG, "Pin: " + pin);
                    if (pin == 2468) {
                        connectThread.sendData(currentCommand);
                    } else {
                        Toast.makeText(this, R.string.incorrect_pin, Toast.LENGTH_SHORT)
                                .show();
                    }
                }
                break;
            case REQ_CODE_TURN_BLUETOOTH_ON:
                if (resultCode == RESULT_OK) {
                    initialize();
                } else {
                    Toast.makeText(this, R.string.unable_to_enable_bluetooth,
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
        }
    }

    private void openKeypadActivity() {
        startActivityForResult(
                new Intent(this, KeypadActivity.class),
                REQ_CODE_KEYPAD_ACTIVITY);
    }

    @NonNull
    private AlertDialog createDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.loading_layout, null, false);
        ((TextView) view.findViewById(R.id.dialog_text)).setText(message);
        builder.setView(view);
        builder.setCancelable(false);
        return builder.create();
    }

    private void initialize() {
        final AlertDialog alertDialog = createDialog(getString(R.string.initializing));
        alertDialog.show();
        connectThread = new ConnectThread(bluetoothDevice);
        connectThread.setConnectionListener(new ConnectThread.ConnectionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(MainActivity.this, R.string.connection_established,
                        Toast.LENGTH_SHORT).show();
                alertDialog.cancel();
            }

            @Override
            public void onFailure() {
                Toast.makeText(MainActivity.this, R.string.unable_to_connect,
                        Toast.LENGTH_SHORT).show();
                alertDialog.cancel();
                finish();
            }

            @Override
            public void onSent(boolean status) {
                if (status) {
                    Toast.makeText(MainActivity.this, R.string.command_sent,
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, R.string.connection_closed,
                            Toast.LENGTH_SHORT).show();
                    connectThread.disConnect();
                    finish();
                }
            }
        });
        connectThread.start();
    }

    private static class ConnectThread extends Thread {

        private static final String SERVICE_ID = "00001101-0000-1000-8000-00805F9B34FB";
        private static final int SEND_WHAT = 0;
        private static final int DISCONNECT_WHAT = 1;

        private final BluetoothDevice thisDevice;
        private final Handler handler;
        private Handler internalHandler;

        private BluetoothSocket thisSocket;
        private ConnectionListener connectionListener;

        private static interface ConnectionListener {
            void onSuccess();
            void onFailure();
            void onSent(boolean status);
        }

        ConnectThread(BluetoothDevice device) {
            thisDevice = device;
            this.handler = new Handler(Looper.getMainLooper());
        }

        void setConnectionListener(ConnectionListener connectionListener) {
            this.connectionListener = connectionListener;
        }

        @Override
        public void run() {
            super.run();
            Looper.prepare();
            Looper looper = Looper.myLooper();
            if (looper != null) {
                internalHandler = new Handler(looper) {
                    @Override
                    public void handleMessage(@NonNull Message msg) {
                        super.handleMessage(msg);
                        if (msg.what == SEND_WHAT) {
                            if (thisSocket != null) {
                                try {
                                    thisSocket.getOutputStream().write((Integer) msg.obj);
                                    handler.post(() -> connectionListener.onSent(true));
                                } catch (IOException e) {
                                    handler.post(() -> connectionListener.onSent(false));
                                }
                            } else {
                                handler.post(() -> connectionListener.onSent(false));
                            }
                        } else if (msg.what == DISCONNECT_WHAT) {
                            try {
                                if (thisSocket != null) {
                                    thisSocket.close();
                                }
                            } catch (IOException ignored) {

                            } finally {
                                thisSocket = null;
                                getLooper().quit();
                            }
                        }
                    }
                };
                try {
                    thisSocket = thisDevice.createRfcommSocketToServiceRecord(UUID.fromString(SERVICE_ID));
                    thisSocket.connect();
                    handler.post(() -> {
                        connectionListener.onSuccess();
                    });
                } catch (IOException e) {
                    handler.post(() -> {
                        connectionListener.onFailure();
                    });
                }
            } else {
                handler.post(() -> {
                    connectionListener.onFailure();
                });
            }
            Looper.loop();
        }

        private void sendData(int data) {
            Message message = new Message();
            message.what = SEND_WHAT;
            message.obj = data;
            internalHandler.sendMessage(message);
        }

        private void disConnect() {
            internalHandler.sendEmptyMessage(DISCONNECT_WHAT);
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        connectThread.disConnect();
    }
}
