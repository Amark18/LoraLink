package com.akapps.loralink;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.AsyncTask;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.MutableLiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import com.akapps.loralink.R;
import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmResults;
import io.realm.Sort;

public class LoraLink extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private ProgressDialog progress;
    private boolean isBtConnected = false;
    private MutableLiveData<Boolean> isBtConnectedLive = new MutableLiveData<>();
    private BluetoothSocket btSocket = null;
    private static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private String deviceName = "";
    private Context context;
    private Devices found_Device;
    private String found_Device_Address;
    private ConnectBT connectTask;
    private BluetoothReceiverTask receiverTask;

    // layout
    private TextInputEditText editTextMessage;
    private TextView setDeviceNameMessage;
    private Button buttonSend;
    private RecyclerView recyclerViewMessages;
    private MessageAdapter messageAdapter;
    private ConstraintLayout emptyMessagesView;
    private MaterialCardView bluetoothConnection;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loralink);
        context = this;

        Realm.init(context);
        RealmConfiguration config = new RealmConfiguration.Builder()
                .allowWritesOnUiThread(true)
                .build();
        Realm.setDefaultConfiguration(config);

        // Changes color of title to black
        setTitle(Html.fromHtml("<font color='#000000'> LoraLink </font>"));

        deviceName = LocalData.getDeviceName(context);
        initialize_Layout(); // initializes layout

        if(!deviceName.isEmpty())
            setDeviceNameMessage.setVisibility(View.GONE);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN},
                1001); // Any number

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        this.registerReceiver(mReceiver, filter);

        keyBoardObserver();
        isArduinoMicrocontrollerSelected();
    }

    private void initialize_Layout(){
        // get references to views
        editTextMessage = findViewById(R.id.message_text_input_edittext);
        buttonSend = findViewById(R.id.send_button);
        recyclerViewMessages = findViewById(R.id.message_recycler_view);
        emptyMessagesView = findViewById(R.id.empty_messages);
        setDeviceNameMessage = findViewById(R.id.device_name_message);
        bluetoothConnection = findViewById(R.id.bluetooth_connection);

        setUpToolbar();

        // set up recycler view
        messageAdapter = new MessageAdapter(getAllMessages(), this);
        recyclerViewMessages.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewMessages.setAdapter(messageAdapter);
        messageAdapter.registerAdapterDataObserver(observer);
        if(messageAdapter.getItemCount() > 0)
            recyclerViewMessages.scrollToPosition(messageAdapter.getItemCount() - 1);
        toggleEmptyView();

        isBtConnectedLive.observe(this, aBoolean -> {
            // Do something when the boolean value changes
            if (aBoolean)
                bluetoothConnection.setCardBackgroundColor(getResources().getColor(R.color.android_message_green));
            else {
                bluetoothConnection.setCardBackgroundColor(getResources().getColor(R.color.red));
            }
        });

        // set up button click listener
        buttonSend.setOnClickListener(v -> {
            String message = editTextMessage.getText().toString();

            if(message.trim().length() == 0)
                return;

            String currentDeviceName = LocalData.getDeviceName(context);
            // check to see if user is trying to set/re-set device name
            String inputtedDeviceName = message.toLowerCase().trim();
            if(inputtedDeviceName.contains("device") &&
                    (inputtedDeviceName.contains("hc") || inputtedDeviceName.contains("esp"))){
                LocalData.setDeviceName(context, inputtedDeviceName.contains("hc") ? "hc" : "esp");
                deviceName = LocalData.getDeviceName(context);
                setDeviceNameMessage.setVisibility(View.GONE);
                showUserMessage("Device name set to " + getDeviceType(deviceName));
                editTextMessage.setText("");
                closeKeyboard();
                isArduinoMicrocontrollerSelected();
            }
            else if(message.toLowerCase().equals("device?")){
                showUserMessage("Device set to " + getDeviceType(currentDeviceName));
                editTextMessage.setText("");
                closeKeyboard();
            }
            else if(inputtedDeviceName.contains("device")){
                showUserMessage("Missing Device Type\n(ex: 'hc' or 'esp')");
            }
            else if(isBtConnected && message.length() > 0 && !deviceName.isEmpty()) {
                sendMessage(message);
                messageAdapter.addMessage(message, LocalData.getSenderID(context));
                editTextMessage.setText("");
                recyclerViewMessages.scrollToPosition(messageAdapter.getItemCount() - 1);
                closeKeyboard();
            }
            else {
                showUserMessage("Connect to Bluetooth First");
                editTextMessage.setText("");
                closeKeyboard();
            }
        });
    }

    /**
     * Broadcast Receiver for listing devices that are not yet paired
     * -Executed by btnDiscover() method.
     */
    private BroadcastReceiver discover = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                Toast.makeText(context, "Started", Toast.LENGTH_SHORT).show();
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Toast.makeText(context, "Done", Toast.LENGTH_SHORT).show();
            }
            else if (action.equals(BluetoothDevice.ACTION_FOUND)){
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                @SuppressLint("MissingPermission") Devices current_Found_Device = new Devices(device.getName(), device.getAddress());
                // looks for one bluetooth device in particular, which is the robot
                // bluetooth module named HC-06, but it looks only for device name
                // containing "HC" and connects to that device
                if(current_Found_Device != null && current_Found_Device.getName()!=null){
                    if(current_Found_Device.getName().toLowerCase().contains(deviceName)) {
                        unregisterReceiver(discover);
                        found_Device = current_Found_Device;
                        found_Device_Address = found_Device.getAddress();
                        connectTask = new ConnectBT();
                        connectTask.execute();
                    }
                }
            }
        }
    };

    // BroadcastReceiver that listens for bluetooth broadcasts that are disconnected
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(action) ||
                    BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                showUserMessage("Disconnected");
                disconnectBluetooth();
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeAllListeners();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            showUserMessage("Bluetooth has been enabled since it was off");
        }
        if (resultCode == RESULT_CANCELED) {
            showUserMessage("Bluetooth not enabled");
        }
    }

    private class ConnectBT extends AsyncTask<Void, Void, Void>  //  UI thread
    {
        private boolean ConnectSuccess = true;

        @Override
        protected void onPreExecute()
        {
            progress = ProgressDialog.show(context, "Connecting...", "Please wait!!!");
        }

        @SuppressLint("MissingPermission")
        @Override
        protected Void doInBackground(Void... devices)
        {
            try
            {
                if (btSocket == null || !isBtConnected)
                {
                    bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                    // connects to the device's address and checks if it's available
                    BluetoothDevice this_Device = bluetoothAdapter.getRemoteDevice(found_Device_Address);
                    // creates a RFCOMM (SPP) connection
                    btSocket = this_Device.createInsecureRfcommSocketToServiceRecord(myUUID);
                    // cancels discovery since it is trying to connect to a device
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    btSocket.connect(); //starts connection
                }
            }
            catch (IOException e)
            {
                ConnectSuccess = false;
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result)
        {
            super.onPostExecute(result);

            if (!ConnectSuccess){
                showUserMessage("Connection Failed. Try again.");
                disconnectBluetooth();
                finish();
            }
            else
            {
                showUserMessage("Connected.");
                isBtConnected = true;
                isBtConnectedLive.setValue(true);
                receiverTask = new BluetoothReceiverTask();
                receiverTask.execute();
            }
            progress.dismiss();
        }
    }

    private class BluetoothReceiverTask extends AsyncTask<Void, Void, Void> {

        private InputStream mInputStream;

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                mInputStream = btSocket.getInputStream();

                byte[] buffer = new byte[1024];
                StringBuilder sb = new StringBuilder();

                while (true) {
                    int numBytes = mInputStream.read(buffer);
                    String received = new String(buffer, 0, numBytes);
                    sb.append(received);

                    int newlineIndex = sb.indexOf("\n");
                    if (newlineIndex >= 0) {
                        final String message = sb.substring(0, newlineIndex);
                        sb.delete(0, newlineIndex + 1);

                        runOnUiThread(() -> {
                            if(message.trim().length() > 0) {
                                messageAdapter.addMessage(message, -1);
                                recyclerViewMessages.scrollToPosition(messageAdapter.getItemCount() - 1);
                                sendMessage("read : " + message);
                                Log.d("Here", "Message received -> " + message);
                            }
                        });
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    mInputStream.close();
                    btSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return null;
        }
    }

    // notifies user via message
    public void showUserMessage(String message){
        Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), message,
                Snackbar.LENGTH_SHORT).setAction("Action", null);
        View snackBarView = snackbar.getView();
        TextView snack_Text = snackBarView.findViewById(com.google.android.material.R.id.snackbar_text);
        snack_Text.setTextColor(getResources().getColor(R.color.actually_dark_black));
        snack_Text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        snack_Text.setTypeface(null, Typeface.BOLD);
        snackBarView.setBackground(ContextCompat.getDrawable(this, R.drawable.snackbar_background));
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) snackBarView.getLayoutParams();
        params.gravity = Gravity.CENTER_VERTICAL;
        snackBarView.setLayoutParams(params);
        snackbar.show();
    }

    // notifies user via message
    public void showUserMessageLong(String message){
        Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), message,
                Snackbar.LENGTH_LONG).setAction("Action", null);
        View snackBarView = snackbar.getView();
        TextView snack_Text = snackBarView.findViewById(com.google.android.material.R.id.snackbar_text);
        snack_Text.setTextColor(getResources().getColor(R.color.actually_dark_black));
        snack_Text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        snack_Text.setTypeface(null, Typeface.NORMAL);
        snack_Text.setMaxLines(6);
        snackbar.setDuration(10000);
        snackBarView.setBackground(ContextCompat.getDrawable(this, R.drawable.snackbar_background));
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) snackBarView.getLayoutParams();
        params.gravity = Gravity.CENTER_VERTICAL;
        snackBarView.setLayoutParams(params);
        snackbar.show();
    }

    private void sendMessage(String message){
        if (btSocket!=null) {
            try {
                btSocket.getOutputStream().write(message.getBytes());
            }
            catch (IOException e) {
                Log.d("Main_Activity", "Error sending message");
            }
        }
    }

    private void closeKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static RealmResults<Message> getAllMessages() {
        Realm realm = Realm.getDefaultInstance();
        return realm.where(Message.class).sort("date", Sort.ASCENDING).findAll();
    }

    private void toggleEmptyView() {
        if (messageAdapter.getItemCount() == 0)
            emptyMessagesView.setVisibility(View.VISIBLE);
        else
            emptyMessagesView.setVisibility(View.GONE);
    }

    private String getDeviceType(String deviceName) {
        String deviceType = "";
        if(deviceName.equals("hc"))
            deviceType = "Arduino";
        else if(deviceName.equals("esp"))
            deviceType = "ESP32";
        else
            deviceType = "None";
        return deviceType;
    }

    RecyclerView.AdapterDataObserver observer = new RecyclerView.AdapterDataObserver() {
        @Override
        public void onChanged() {
            super.onChanged();
            toggleEmptyView();
        }
    };

    // checks to see if keyboard is open, if true, get rid of empty view so that
    // the text is not jumbled
    private void keyBoardObserver(){
        // get the root view of your activity
        View rootView = getWindow().getDecorView().getRootView();

        // add a global layout listener to detect when the keyboard is opened or closed
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int heightDiff = rootView.getRootView().getHeight() - (r.bottom - r.top);

            if (heightDiff > 300) {
                emptyMessagesView.setVisibility(View.GONE);
                recyclerViewMessages.scrollToPosition(messageAdapter.getItemCount() - 1);
            } else {
                if(emptyMessagesView.getVisibility() == View.GONE && messageAdapter.getItemCount() == 0)
                    emptyMessagesView.setVisibility(View.VISIBLE);
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void setUpToolbar(){
        findViewById(R.id.info).setOnClickListener(view -> {
            String infoMessage = "Info:" + "\nTo check current device type -> device?" +
                "\nTo set device type -> device + hc or esp " +
                "\nTo delete all messages -> long press trash icon" +
                "\nTo get sensor readings type -> !temp or !temp plz";
        showUserMessageLong(infoMessage);
        });

        findViewById(R.id.trash).setOnLongClickListener(view -> {
            if(getAllMessages().size() > 0) {
                Realm realm = Realm.getDefaultInstance();
                realm.executeTransaction(realm1 -> realm1.delete(Message.class));
                realm.close();
                messageAdapter.notifyDataSetChanged();
                showUserMessage("All messages deleted!");
            }
            return true;
        });

        findViewById(R.id.disconnect_bluetooth).setOnClickListener(view -> {
            if(deviceName.isEmpty()) {
                showUserMessage("Device name not set!");
            }

            if (btSocket!=null)
            {
                try
                {
                    if(!isBtConnected)
                        showUserMessage("Already disconnected");
                    else {
                        btSocket.close(); //close connection
                        disconnectBluetooth();
                    }
                }
                catch (IOException e)
                { showUserMessage("Error in disconnecting device");}
            }
            else{
                if (bluetoothAdapter.isDiscovering()){
                    bluetoothAdapter.cancelDiscovery();
                    showUserMessage("Discovery canceled");
                }
                else
                    showUserMessage("No connected device");
            }
        });

        findViewById(R.id.connect_bluetooth).setOnClickListener(view -> {
            connectBluetooth();
        });

        findViewById(R.id.chart).setOnClickListener(view -> {
            showUserMessage("chart stuff");
        });
    }

    private void isArduinoMicrocontrollerSelected(){
        if(deviceName.equals("hc"))
            findViewById(R.id.chart).setVisibility(View.GONE);
        else
            findViewById(R.id.chart).setVisibility(View.VISIBLE);
    }

    private void removeAllListeners(){
        try {
            unregisterReceiver(discover);
            unregisterReceiver(mReceiver);
            if(btSocket != null)
                btSocket.close();
            if (connectTask != null)
                connectTask.cancel(true); // cancel the async task
            if (receiverTask != null)
                receiverTask.cancel(true); // cancel the async task
        } catch(IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void disconnectBluetooth(){
        isBtConnected= false;
        isBtConnectedLive.setValue(false);
        removeAllListeners();
    }

    @SuppressLint("MissingPermission")
    private void connectBluetooth(){
        if(deviceName.isEmpty()) {
            showUserMessage("Device name not set!");
        }

        if(!isBtConnected) {
            if (bluetoothAdapter!=null && !bluetoothAdapter.isEnabled()) {
                // enables bluetooth if not enabled
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
            else {
                if(bluetoothAdapter==null)
                    showUserMessage("No bluetooth");
                else if (bluetoothAdapter.isEnabled()) {
                    // if bluetooth enabled, then it starts looking for bluetooth devices
                    if (!bluetoothAdapter.isDiscovering()) {
                        showUserMessage("Attempting to Connect...");
                        bluetoothAdapter.startDiscovery();
                        registerReceiver(discover, new IntentFilter(BluetoothDevice.ACTION_FOUND));
                    }
                    else {
                        // if button is pressed again, then it cancels the discovery
                        bluetoothAdapter.cancelDiscovery();
                        showUserMessage("Discovery canceled");
                    }
                }
            }
        }
        else{
            showUserMessage("Already connected");
        }
    }
}
