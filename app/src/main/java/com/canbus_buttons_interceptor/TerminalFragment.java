package com.canbus_buttons_interceptor;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    private boolean initialStart = true;
    private boolean minimize = false;
    private enum Connected { False, Pending, True }
    private int deviceId, portNum, baudRate;
    private int mStartWarmAttempts = 0;
    private int mStartColdAttempts = 0;
    private int mCommandTimeoutTotal = 0;
    private int mCommandTimeoutData = 0;
    private int mCommandRetries = 0;
    private int mCommandRetryCounter = 0;
    private static final int MONITOR_START_WARM_ATTEMPTS = 3;
    private static final int MONITOR_START_COLD_ATTEMPTS = 3;
    private static final int DEFAULT_COMMAND_TOTAL_TIMEOUT = 2500;
    private static final int DEFAULT_COMMAND_SEND_TIMEOUT = 250;
    private static final int DEFAULT_COMMAND_DATA_TIMEOUT = 1000;
    private static final int DEFAULT_COMMAND_RETRIES = 3;
    private static final int DEFAULT_RESET_COMMAND_TOTAL_TIMEOUT = 5000;
    private static final int DEFAULT_MONITOR_COMMAND_DATA_TIMEOUT = 5000;
    private static final int WATCHDOG_INTERVAL = 30000;
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    private String newline = "\r\n";
    private String protocolCommand = "ATSP2";
    private String monitorCommand = "ATMR11";
    private String mCommand = "";
    private String mResponse = "";

    private TextView receiveText;
    private SerialSocket socket;
    private SerialService service;   
    private Connected connected = Connected.False;
    private BroadcastReceiver broadcastReceiver;
    private SharedPreferences mSettings;
    private ButtonActions mButtons;
    protected static final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private static final Handler mCommandTimeoutTotal_Timer = new Handler();
    private static final Handler mCommandTimeoutData_Timer = new Handler();
    private UsbSerialDriver driver;
    protected SerialInputOutputManager mSerialIoManager;
    private UsbSerialPort usbSerialPort;
    private final Handler watchdog_Timer = new Handler();


    public TerminalFragment() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent.getAction().equals(INTENT_ACTION_GRANT_USB)) {
                    Boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    connect(granted);
                }
            }
        };
    }

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        mSettings = PreferenceManager.getDefaultSharedPreferences(getContext());
        protocolCommand = mSettings.getString("scantool_protocol", "");
        monitorCommand = mSettings.getString("scantool_monitor_command", "");
        deviceId = getArguments().getInt("device");
        portNum = getArguments().getInt("port");
        baudRate = getArguments().getInt("baud");
        mButtons = new ButtonActions(getContext());
    }



    @Override
    public void onDestroy() {
        watchdog_TimerStop();
        deviceClose(false);
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        deviceClose(false);
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION_GRANT_USB));
        if(initialStart && service !=null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onPause() {
        getActivity().unregisterReceiver(broadcastReceiver);
        super.onPause();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());
        TextView sendText = view.findViewById(R.id.send_text);
        View sendBtn = view.findViewById(R.id.send_btn);
        sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));
        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if (id ==R.id.newline) {
            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
            int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Newline");
            builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
                newline = newlineValues[item1];
                dialog.dismiss();
            });
            builder.create().show();
            return true;
        } else if (id ==R.id.settings) {
            Intent intent = new Intent(getActivity(), SettingsActivity.class);
            startActivity(intent);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    private void connect() {
        connect(null);
    }

    private void connect(Boolean permissionGranted) {
        UsbDevice device = null;
        UsbManager usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        for(UsbDevice v : usbManager.getDeviceList().values())
            if(v.getDeviceId() == deviceId)
                device = v;
        if(device == null) {
            status("connection failed: device not found");
            return;
        }
        driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if(driver == null) {
            driver = CustomProber.getCustomProber().probeDevice(device);
        }
        if(driver == null) {
            status("connection failed: no driver for device");
            return;
        }
        if(driver.getPorts().size() < portNum) {
            status("connection failed: not enough ports at device");
            return;
        }
        usbSerialPort = driver.getPorts().get(portNum);
        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
        if(usbConnection == null && permissionGranted == null && !usbManager.hasPermission(driver.getDevice())) {
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(INTENT_ACTION_GRANT_USB), 0);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return;
        }
        if(usbConnection == null) {
            if (!usbManager.hasPermission(driver.getDevice()))
                status("connection failed: permission denied");
            else
                status("connection failed: open failed");
            return;
        }

        connected = Connected.Pending;
        try {
            socket = new SerialSocket();
            service.connect(this, getString(R.string.msg_app_starting));
            socket.connect(getContext(), service, usbConnection, usbSerialPort, baudRate);
            // usb connect is not asynchronous. connect-success and connect-error are returned immediately from socket.connect
            // for consistency to bluetooth/bluetooth-LE app use same SerialListener and SerialService classes
            onSerialConnect();
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
        socket.disconnect();
        socket = null;
        monitorStop();
    }

    private void send(String str) {
        if(connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            SpannableStringBuilder spn = new SpannableStringBuilder(str+'\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            receiveText.append(spn);
            byte[] data = (str + newline).getBytes();
            socket.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str+'\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    private void receive(byte[] data) {
        String time = new SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis());
        receiveText.append(time + "  " + new String(data));
    }

    /*
     * Monitor
     */
    private void ioManagerOnReceivedData(byte[] data) {
        if (mCommandTimeoutData > 0) {
            commandTimeoutData_TimerReStart(mCommandTimeoutData);
        }
        
        String strdata = new String(data);
        
        //mResponse will continue to append data till a condition below sees it as a complete response and handles it  
        mResponse += strdata;

        //this is a catch for when the device resets due to cranking or a hardware error
        if (mResponse.contains("ELM327") || mResponse.contains("LV RESET")) {
            //just pretend the command was an intentional reset to re-start the entire command sequence 
            mCommand = "ATZ";
        }
        
        //for each command, allow response to append data until we see the expected full response 
        if (mCommand == "ATZ" || mCommand == "ATI") {
            if (!mResponse.contains(">") || !mResponse.contains("ELM327")) return;          
            status("  ELM DEVICE FOUND");
            sendCommand("ATE1");
        } else if (mCommand == "ATE1") {
            if (!mResponse.contains(">") || !mResponse.contains("OK")) return;
            status("  ECHO ON");
            sendCommand("ATL1");            
        } else if (mCommand == "ATL1") {
            if (!mResponse.contains(mCommand) || !mResponse.contains(">") || !mResponse.contains("OK")) return;
            status("  LINE BREAKS ON");
            sendCommand("ATS1");
        } else if (mCommand == "ATS1") {
            if (!mResponse.contains(mCommand) || !mResponse.contains(">") || !mResponse.contains("OK")) return;
            status("  SPACES ON");
            sendCommand("ATH1");
        } else if (mCommand == "ATH1") {
            if (!mResponse.contains(mCommand) || !mResponse.contains(">") || !mResponse.contains("OK")) return;         
            status("  HEADERS ON");
            sendCommand(protocolCommand);
        } else if (mCommand == protocolCommand) {
            if (!mResponse.contains(mCommand) || !mResponse.contains(">") || !mResponse.contains("OK")) return;         
            status("  PROTOCOL SET");
            sendCommand(monitorCommand, 0, DEFAULT_MONITOR_COMMAND_DATA_TIMEOUT, DEFAULT_COMMAND_RETRIES);      
        } else if (mCommand == monitorCommand) {
            if (!mResponse.contains("\r") && !mResponse.contains("\n")) return;
            mButtons.performAction(mResponse.trim());
            mResponse = "";
            service.updateNotification(getString(R.string.msg_monitoring));
        } else {
            status("  UNEXPECTED DATA RECEIVED (WHILE NO COMMAND PENDING)");
        }
    }

    private Runnable commandTimeout_TimersRun = new Runnable() {
        public void run() {
            if (mCommandRetryCounter < mCommandRetries) {
                status("COMMAND OR DATA TIMEOUT - RETRYING COMMAND ATTEMPT: " + mCommandRetryCounter);
                sendCommandRetry();           
            } else {
                monitorStartWarm();
            }
        }
    };
    
    
    private void commandTimeout_TimersStop() {
        commandTimeoutTotal_TimerStop();
        commandTimeoutData_TimerStop();
    }
    
    
    private void commandTimeoutTotal_TimerStop() {
        mCommandTimeoutTotal_Timer.removeCallbacks(commandTimeout_TimersRun);
    }

    
    private void commandTimeoutTotal_TimerReStart(int timeout) {
        commandTimeoutTotal_TimerStop();
        mCommandTimeoutTotal_Timer.postDelayed(commandTimeout_TimersRun, timeout);
    }
    
    
    private void commandTimeoutData_TimerStop() {
        mCommandTimeoutData_Timer.removeCallbacks(commandTimeout_TimersRun);
    }

    
    private void commandTimeoutData_TimerReStart(int timeout) {
        commandTimeoutData_TimerStop();
        mCommandTimeoutData_Timer.postDelayed(commandTimeout_TimersRun, timeout);
    }
    
    
    /**
     * Send a command to the serial device with no retries and do not expect response.
     * 
     * @param command           A valid ELM AT Command.
     * @return                  Returns false if the command was not written correctly to device.
     */
    private Boolean sendCommandBlind(String command) {
        return sendCommand(command, 0, 0, 0, false, true);
    }
    
    
    /**
     * Retry last command with same params.
     * Must call sendCommand() prior.
     * 
     * @return                  Returns false if the command was not written correctly to device.
     */
    private Boolean sendCommandRetry() {
        return sendCommand(mCommand, mCommandTimeoutTotal, mCommandTimeoutData, mCommandRetries, true, false);
    }
    
    
    /**
     * Send a command to the serial device using the default params.
     * 
     * @param command           A valid ELM AT Command.
     * @return                  Returns false if the command was not written correctly to device.
     */
    private Boolean sendCommand(String command) {
        return sendCommand(command, DEFAULT_COMMAND_TOTAL_TIMEOUT, DEFAULT_COMMAND_DATA_TIMEOUT, DEFAULT_COMMAND_RETRIES, false, false); 
    }
    
    
    /**
     * Send a command to the serial device.
     * 
     * @param command           A valid ELM AT Command.
     * @param timeoutTotal      Total milliseconds to wait for a complete response before retrying.
     * @param timeoutData       Milliseconds to wait between partial response fragments before retrying. 
     * @param retries           Number of times to retry command if a complete response is not received (or if timeouts occur)
     * @return                  Returns false if the command was not written correctly to device.
     */
    private Boolean sendCommand(String command, int timeoutTotal, int timeoutData, int retries) {
        return sendCommand(command, timeoutTotal, timeoutData, retries, false, false); 
    }

    
    private Boolean sendCommand(String command, int timeoutTotal, int timeoutData, int retries, Boolean isRetry, Boolean isBlind) {
        commandTimeout_TimersStop();

        mResponse = "";
        mCommand = "";

        command = command.trim();
        status("SENDING COMMAND: " + command);
        
        if (isBlind) {
            mCommandTimeoutTotal = 0;
            mCommandTimeoutData = 0;
            
            mCommandRetries = 0;
            mCommandRetryCounter = 0;
        } else {
            mCommandTimeoutTotal = timeoutTotal;
            mCommandTimeoutData = timeoutData;
            
            mCommandRetries = retries;          
            if (isRetry) {
                mCommandRetryCounter++;
            } else {
                mCommandRetryCounter = 0;
            }

            mCommand = command;
            
            if (timeoutTotal > 0) {
                commandTimeoutTotal_TimerReStart(timeoutTotal);
            }           
            if (timeoutData > 0) {
                commandTimeoutData_TimerReStart(timeoutData);
            }
        }
                
        command += "\r";        
        byte[] bytes = command.getBytes();
        int written = 0;

        if (driver != null) {
            try {
                written = usbSerialPort.write(bytes, DEFAULT_COMMAND_SEND_TIMEOUT);
            } catch (Exception ex) {
                status("ERROR WRITING COMMAND TO DEVICE");
            }
        }

        return written == bytes.length;
    }
    
    /**
     * Begins monitoring the serial device.
     * Must call deviceOpen() prior.
     * Optionally call setProtocolCommand() prior.
     */
    private void monitorStart() {
        mStartWarmAttempts = 0;
        mStartColdAttempts = 0;
        monitorStartWarm();         
    }
    
    /**
     * Stops monitoring the serial device.
     * Must have called deviceOpen() and monitorStart() prior.
     */
    private void monitorStop() {
        //attempt to send a simple command to make sure any current long running commands are stopped, ignore results
        sendCommandBlind("ATI");
    
        //attempt to send the Low Power Mode command and ignore results (only newer ELM devices support this)
        sendCommandBlind("LP");

        service.updateNotification(getString(R.string.msg_monitoring_stopped));
    }
   
    private void monitorStartWarm() {
        if (mStartWarmAttempts < MONITOR_START_WARM_ATTEMPTS) {
            status("MONITORING WARM START ATTEMPT: " + mStartWarmAttempts);
            sendCommand("ATI", DEFAULT_RESET_COMMAND_TOTAL_TIMEOUT, 0, 1);
        } else {
            status("MONITORING WARM START - TOO MANY ATTEMPTS");
            monitorStartCold();
        }
    
        mStartWarmAttempts++;
    }
    
    
    private void monitorStartCold() {
        if (mStartColdAttempts < MONITOR_START_COLD_ATTEMPTS) {
            status("MONITORING COLD START ATTEMPT: " + mStartColdAttempts);
            sendCommand("ATZ", DEFAULT_RESET_COMMAND_TOTAL_TIMEOUT, 0, 1);
        } else {
            status("MONITORING COLD START - TOO MANY ATTEMPTS");
            try {
                monitorStop();
            } catch (Exception ignored) { }
        }
        
        mStartColdAttempts++;
    }

    private void deviceClose(Boolean fromError) {
        commandTimeout_TimersStop();       
        try {
            if (driver != null) {
                usbSerialPort.close();
            }           
        } catch (Exception ex) {
            status("ERROR CLOSING SERIAL DEVICE");
        }
         
        driver = null;
    }

    /*
     * Watchdog
     */
    private Runnable watchdog_TimerRun = new Runnable() {
        public void run() {
            watchdog_TimerReStart();
        }
    };
    
        
    private void watchdog_TimerStop() {
        watchdog_Timer.removeCallbacks(watchdog_TimerRun);
    }

    
    private void watchdog_TimerReStart() {
        watchdog_TimerStop();
        watchdog_Timer.postDelayed(watchdog_TimerRun, WATCHDOG_INTERVAL);
    }  

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("Connected to " + mSettings.getString("chosenDevice", ""));
        connected = Connected.True;
        watchdog_TimerReStart();
        try {
            monitorStart();
        } catch (Exception e) {
            //
        }
        minimize = mSettings.getBoolean("minimize", false);
        if (minimize) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);        
        }
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
        ioManagerOnReceivedData(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }     
}
