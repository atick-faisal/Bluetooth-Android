package com.googleplex.bluetoothcommunication;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private ListView pairedDevicesList;
    public Button sendButton;
    public EditText editText;
    public TextView textView, data1, data2, data3;
    FloatingActionButton floatingActionButton;
    public String address;

    private BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket bluetoothSocket;
    private static final UUID mUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    String receivedMessage;
    private boolean isBtConnected = false;

    ////////////////////////////////////////////////////////////////////////////////////////////////
    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() { ///////////////////////////////////////////////////// handle received messages
        @Override
        public void handleMessage(Message msg) {
            byte[] writeBuf = (byte[]) msg.obj;
            int begin = msg.arg1;
            int end = msg.arg2;

            switch(msg.what) {
                case 1:
                    receivedMessage = new String(writeBuf);
                    receivedMessage = receivedMessage.substring(begin, end);
                    onBluetoothMessageReceive();
                    break;
            }
        }
    };
    ////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setElevation(0);
        }

        pairedDevicesList = findViewById(R.id.list_view);
        sendButton = findViewById(R.id.send_button);
        editText = findViewById(R.id.send_data);
        textView = findViewById(R.id.received_data);
        data1 = findViewById(R.id.data1);
        data2 = findViewById(R.id.data2);
        data3 = findViewById(R.id.data3);
        floatingActionButton = findViewById(R.id.floatingActionButton);

        turnOnBluetooth();

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMessage(editText.getText().toString());
            }
        });
        floatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pairedDevicesList();
            }
        });
    }

    public void pairedDevicesList() { ////////////////////////////////////////////////////////////// load paired devices and show them in the list
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        ArrayList<String> list = new ArrayList<>();

        if (pairedDevices.size() > 0) {
            for (BluetoothDevice bt : pairedDevices) {
                list.add(bt.getName() + "\n" + bt.getAddress());
            }
        } else {
            Toast.makeText(getApplicationContext(), "No Paired Bluetooth Devices Found.", Toast.LENGTH_LONG).show();
        }

        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, list);
        pairedDevicesList.setAdapter(adapter);
        pairedDevicesList.setOnItemClickListener(mListClickListener);
    }

    private AdapterView.OnItemClickListener mListClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView av, View v, int arg2, long arg3) {
            String info = ((TextView) v).getText().toString();
            address = info.substring(info.length() - 17);
            ConnectBluetooth connectBluetooth = new ConnectBluetooth();
            connectBluetooth.execute(); /////////////////////////////////////////////////////// connect
        }
    };

    void turnOnBluetooth() { /////////////////////////////////////////////////////////////////////// turn on bluetooth
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "Bluetooth Device Not Available", Toast.LENGTH_LONG).show();
            finish();
        } else {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);

            } else {
                pairedDevicesList();
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    class ConnectBluetooth extends AsyncTask<Void, Void, Void> { /////////////////////////// connect in background
        private boolean ConnectSuccess = true;

        @Override
        protected Void doInBackground(Void... devices) {
            try {
                if (bluetoothSocket == null || !isBtConnected) {
                    BluetoothDevice bluetoothDevice = mBluetoothAdapter.getRemoteDevice(address);
                    bluetoothSocket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(mUUID);
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    bluetoothSocket.connect();
                }
            } catch (IOException e) {
                ConnectSuccess = false;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);

            if (ConnectSuccess) {
                isBtConnected = true;
                ConnectedThread connectedThread = new ConnectedThread(bluetoothSocket);
                connectedThread.start(); /////////////////////////////////////////////////////////// start looking for received data
            }
        }
    }

    private class ConnectedThread extends Thread { ///////////////////////////////////////////////// received data listener
        private final InputStream mInStream;

        ConnectedThread(BluetoothSocket socket) {
            bluetoothSocket = socket;
            InputStream tmpIn = null;
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException ignored) {
            }
            mInStream = tmpIn;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int begin = 0;
            int bytes = 0;
            while (true) {
                try {
                    bytes += mInStream.read(buffer, bytes, buffer.length - bytes);
                    for (int i = begin; i < bytes; i++) {
                        if (buffer[i] == "\n".getBytes()[0]) { ///////////////////////////////////// read data until new line
                            mHandler.obtainMessage(1, begin, i, buffer).sendToTarget();
                            begin = i + 1;
                            if (i == bytes - 1) {
                                bytes = 0;
                                begin = 0;
                            }
                        }
                    }
                } catch (IOException e) {
                    break;
                }
            }
        }
    }

    public void onBluetoothMessageReceive(){ /////////////////////////////////////////////////////// Edit here...
        textView.setText(receivedMessage);
        String[] parsedData = receivedMessage.split(",");
        data1.setText(parsedData[0]);
        data2.setText(parsedData[1]);
        data3.setText(parsedData[2]);
    }

    public void sendMessage(String message){
        if(bluetoothSocket != null){
            try {
                bluetoothSocket.getOutputStream().write(message.getBytes());
            } catch (IOException ignored) {

            }
        }
    }
}
