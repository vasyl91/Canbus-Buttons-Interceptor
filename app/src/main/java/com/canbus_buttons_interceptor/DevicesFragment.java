package com.canbus_buttons_interceptor;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.Manifest;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.ListFragment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.content.SharedPreferences;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class DevicesFragment extends ListFragment {

    private ArrayList<ListItem> listItems = new ArrayList<>();
    private ArrayAdapter<ListItem> listAdapter;
    private Context mAppContext;
    private SharedPreferences mSettings;
    private String directoryString = "/storage/emulated/0/CanbusButtonsInterceptor/";
    private File directory = new File(directoryString);
    private Timer timer;

    private int deviceId;
    private int portNum = 1;
    private int baudRate = 115200;
    private boolean autoconnect = false;

    class ListItem {
        UsbDevice device;
        int port;
        UsbSerialDriver driver;

        ListItem(UsbDevice device, int port, UsbSerialDriver driver) {
            this.device = device;
            this.port = port;
            this.driver = driver;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        
        //Try to auto connect to the last device
        mAppContext = getActivity().getApplicationContext();
        mSettings = PreferenceManager.getDefaultSharedPreferences(mAppContext);
        autoconnect = mSettings.getBoolean("autoconnect", false);
        
        if (mSettings.getInt("device", 0) == 0) {
            //
        } else {
            if (autoconnect){
                deviceId = mSettings.getInt("device", 0);
                portNum = mSettings.getInt("port", 0);
                baudRate = mSettings.getInt("baud", 0);
                
                Bundle args = new Bundle();
                args.putInt("device", deviceId);
                args.putInt("port", portNum);
                args.putInt("baud", baudRate);
                Fragment fragment = new TerminalFragment();
                fragment.setArguments(args);
                getFragmentManager().beginTransaction().replace(R.id.fragment, fragment, "terminal").addToBackStack(null).commit();
            }
        }
        
        listAdapter = new ArrayAdapter<ListItem>(getActivity(), 0, listItems) {
            @Override
            public View getView(int position, View view, ViewGroup parent) {
                ListItem item = listItems.get(position);
                if (view == null)
                    view = getActivity().getLayoutInflater().inflate(R.layout.device_list_item, parent, false);
                    TextView text1 = view.findViewById(R.id.text1);
                    TextView text2 = view.findViewById(R.id.text2);
                if(item.driver == null)
                    text1.setText("<no driver>");
                else if(item.driver.getPorts().size() == 1)
                    text1.setText(item.driver.getClass().getSimpleName().replace("SerialDriver",""));
                else
                    text1.setText(item.driver.getClass().getSimpleName().replace("SerialDriver","")+", Port "+item.port);
                    text2.setText(String.format(Locale.US, "Vendor %04X, Product %04X", item.device.getVendorId(), item.device.getProductId()));

                return view;
            }
        };
        setListAdapter(null);
        View header = getActivity().getLayoutInflater().inflate(R.layout.device_list_header, null, false);
        getListView().addHeaderView(header, null, false);
        setEmptyText("<no USB devices found>");
        ((TextView) getListView().getEmptyView()).setTextSize(18);
        setListAdapter(listAdapter);

        //Permission to write files
        //Checks if permission is granted and creates log file
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);

            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {   
                        if(!directory.exists()) {
                            directory.mkdirs();
                            File f = new File(directoryString + "InterfaceLog.csv");
                            f.createNewFile();
                        } else {
                            timer.cancel();
                            timer.purge();
                        }
                    } catch (Exception ignored) {}
                }
            }, 0, 1000);
        }

        //Second time in case user deleted log while permission is still granted
        try {   
            File f = new File(directoryString + "InterfaceLog.csv");
            if(!directory.exists()) {
                directory.mkdirs();
                f.createNewFile();
            } 
            if(f.exists()) {} else {
                f.createNewFile();
            } 
        } catch (Exception ignored) {}
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_devices, menu);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.refresh) {
            refresh();
            return true;
        } else if (id ==R.id.settings) {
            Intent intent = new Intent(getActivity(), SettingsActivity.class);
            startActivity(intent);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void refresh() {
        UsbManager usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        UsbSerialProber usbDefaultProber = UsbSerialProber.getDefaultProber();
        UsbSerialProber usbCustomProber = CustomProber.getCustomProber();
        listItems.clear();
        for(UsbDevice device : usbManager.getDeviceList().values()) {
            UsbSerialDriver driver = usbDefaultProber.probeDevice(device);
            if(driver == null) {
                driver = usbCustomProber.probeDevice(device);
            }
            if(driver != null) {
                for(int port = 0; port < driver.getPorts().size(); port++)
                    listItems.add(new ListItem(device, port, driver));
            } else {
                listItems.add(new ListItem(device, 0, null));
            }
        }
        listAdapter.notifyDataSetChanged();
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        ListItem item = listItems.get(position-1);
        if(item.driver == null) {
            Toast.makeText(getActivity(), "no driver", Toast.LENGTH_SHORT).show();
        } else {
            mSettings.edit().putInt("device", item.device.getDeviceId()).apply();
            mSettings.edit().putInt("port", item.port).apply();
            mSettings.edit().putInt("baud", baudRate).apply();
            mSettings.edit().putString("chosenDevice", item.driver.getClass().getSimpleName().replace("SerialDriver","")).apply();

            Bundle args = new Bundle();
            args.putInt("device", item.device.getDeviceId());
            args.putInt("port", item.port);
            args.putInt("baud", baudRate);
            Fragment fragment = new TerminalFragment();
            fragment.setArguments(args);
            getFragmentManager().beginTransaction().replace(R.id.fragment, fragment, "terminal").addToBackStack(null).commit();
        }
    }
}
