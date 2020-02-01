package com.canbus_buttons_interceptor;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.PrintWriter;
import java.util.Date;
import java.util.HashMap;

public class ButtonActions {
	private static final String TAG = ButtonActions.class.getSimpleName();
	
	private static final int DEBOUNCE_THRESHOLD = 50;	//milliseconds
	
	private PrintWriter writer;
	private Context mAppContext;
	private HashMap<String, Long> mBusMessageDebounceTimes = new HashMap<String, Long>();
	private SharedPreferences mSettings;
	
    //performAction() return status
	private static final int STATUS_ERROR_UNKNOWNBUTTON = 1;
	private static final int STATUS_ERROR_ACTIONERROR = 2;
	private static final int STATUS_SUCCESS = 3;
	
	private ButtonActions() { 
		//exists only to prevent creation of class without passing required param
	}
	
	/**
	 * Constructor.
	 * 
	 * @param appContext		The application context of the container app. 
	 */
	public ButtonActions(Context appContext) {
		mAppContext = appContext.getApplicationContext();
	}
	
	/**
	 * Executes a particular button's assigned action.
	 * 
	 * @param forBusMessage		A bus message, expected to correspond to a button (one of the ButtonActions.BUTTON_XYZ definitions).
	 * @return 					Returns one of the ButtonActions.STATUS_XYZ definitions.
	 */
	public int performAction(String forBusMessage) {		
		try {	
			mSettings = PreferenceManager.getDefaultSharedPreferences(mAppContext.getApplicationContext());
			String b1 = mSettings.getString("elm_monitor1", "");
			String b2 = mSettings.getString("elm_monitor2", "");
			String b3 = mSettings.getString("elm_monitor3", "");
			String b4 = mSettings.getString("elm_monitor4", "");
			String b5 = mSettings.getString("elm_monitor5", "");
			String b6 = mSettings.getString("elm_monitor6", "");
			String b7 = mSettings.getString("elm_monitor7", "");
			String b8 = mSettings.getString("elm_monitor8", "");
			String b9 = mSettings.getString("elm_monitor9", "");
			String b10 = mSettings.getString("elm_monitor10", "");

			if (forBusMessage.startsWith(b1)) {
				if (!isHardwareBounce(forBusMessage)) {					
					printMessage("BUTTON_1");
				}
			} else if (forBusMessage.startsWith(b2)) {
				if (!isHardwareBounce(forBusMessage)) {
					printMessage("BUTTON_2");
				}
			} else if (forBusMessage.startsWith(b3)) {
				if (!isHardwareBounce(forBusMessage)) {
					printMessage("BUTTON_3");
				}
			} else if (forBusMessage.startsWith(b4)) {
				if (!isHardwareBounce(forBusMessage)) {
					printMessage("BUTTON_4");
				}
			} else if (forBusMessage.startsWith(b5)) {
				if (!isHardwareBounce(forBusMessage)) {
					printMessage("BUTTON_5");
				}
			} else if (forBusMessage.startsWith(b6)) {
				if (!isHardwareBounce(forBusMessage)) {
					printMessage("BUTTON_6");
				}
			} else if (forBusMessage.startsWith(b7)) {
				if (!isHardwareBounce(forBusMessage)) {
					printMessage("BUTTON_7");
				}
			} else if (forBusMessage.startsWith(b8)) {
				if (!isHardwareBounce(forBusMessage)) {
					printMessage("BUTTON_8");
				}
			} else if (forBusMessage.startsWith(b9)) {
				if (!isHardwareBounce(forBusMessage)) {
					printMessage("BUTTON_9");
				}
			} else if (forBusMessage.startsWith(b10)) {
				if (!isHardwareBounce(forBusMessage)) {
					printMessage("BUTTON_10");
				}
			} else {
				Log.i(TAG, "Unknown button: " + forBusMessage);
				return STATUS_ERROR_UNKNOWNBUTTON;
			}
			writer.close();
		} catch (Exception ex) {
			Log.e(TAG, "Error performing action for button: " + forBusMessage, ex);
			return STATUS_ERROR_ACTIONERROR;
		}
		return STATUS_SUCCESS;
	}
	
	private boolean isHardwareBounce(String busMessage) {
		Boolean isBounce = true;		
		
		long now = (new Date()).getTime();		
		Long last = mBusMessageDebounceTimes.get(busMessage);
		
		if (last == null) {
			isBounce = false;
		} else {		
			if (now - last > DEBOUNCE_THRESHOLD) {
				isBounce = false;
			}
		}
		
		mBusMessageDebounceTimes.put(busMessage, now);
		
		return isBounce;
	}	

	private void printMessage(String buttonNumber) {
		try {	
			PrintWriter out = new PrintWriter("/storage/emulated/0/SteeringWheelInterface/InterfaceLog.csv");
		    out.print(buttonNumber);
		    out.flush();
			out.close();
		} catch (Exception ignored) {}

	}  
}