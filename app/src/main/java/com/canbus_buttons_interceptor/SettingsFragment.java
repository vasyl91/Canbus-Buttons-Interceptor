package com.canbus_buttons_interceptor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import java.util.regex.*; 

public class SettingsFragment extends PreferenceFragment {

	private SharedPreferences mSettings;
	private String baud = "115200";
	private String customBaud = "1200";
	private int checkedItem = 5;
	private String[] bauds;
	private int pat;

	@Override
	public void onAttach(Activity activity){
		super.onAttach(activity);
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.pref_general);

        Preference preference = new Preference(getActivity());
        preference.setKey("custom_baud_value");

 		Preference preferenceCheckedItem = new Preference(getActivity());
        preferenceCheckedItem.setKey("checked_item");

		mSettings = PreferenceManager.getDefaultSharedPreferences(getActivity());

		if (mSettings.getString("scantool_baud", "") == "") {
			mSettings.edit().putString("scantool_baud", baud).apply();
		}
		if (mSettings.getInt("checked_item", 0) == 0) {
			mSettings.edit().putInt("checked_item", checkedItem).apply();
		}


		if (mSettings.getString("custom_baud_value", "") == "") {
			//
		} else {
			customBaud = mSettings.getString("custom_baud_value", "");
		}
		if (mSettings.getInt("checked_item", 0) == 0) {
			//
		} else {
			checkedItem = mSettings.getInt("checked_item", 0);
		}

		bauds = new String[]{"2400", "9600", "19200", "38400", "57600", "115200", "Custom: " + customBaud};
		Preference myPref = (Preference) findPreference("scantool_baud");
		myPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
	         public boolean onPreferenceClick(Preference preference) {
	         		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			        builder
			        .setTitle("Baud rate")
					.setSingleChoiceItems(bauds, checkedItem, new DialogInterface.OnClickListener() {
			            @Override
			            public void onClick(DialogInterface dialog, int which) {
			                String strName = bauds[which];
			                if (strName.contains("2400")) {
				                mSettings.edit().putString("scantool_baud", strName).apply();
				                mSettings.edit().putInt("checked_item", 0).apply();
				                checkedItem = 0;
			                } else if (strName.contains("9600")) {
				                mSettings.edit().putString("scantool_baud", strName).apply();
				                mSettings.edit().putInt("checked_item", 1).apply();
				                checkedItem = 1;
				            } else if (strName.contains("19200")) {
				                mSettings.edit().putString("scantool_baud", strName).apply();
				                mSettings.edit().putInt("checked_item", 2).apply();
				                checkedItem = 2;
				            } else if (strName.contains("38400")) {
				                mSettings.edit().putString("scantool_baud", strName).apply();
				                mSettings.edit().putInt("checked_item", 3).apply();
				                checkedItem = 3;
				            } else if (strName.contains("57600")) {
				                mSettings.edit().putString("scantool_baud", strName).apply();
				                mSettings.edit().putInt("checked_item", 4).apply();
				                checkedItem = 4;
				            } else if (strName.contains("115200")) {
				                mSettings.edit().putString("scantool_baud", strName).apply();
				                mSettings.edit().putInt("checked_item", 5).apply();	
				                checkedItem = 5;	                	
			            	} else if (strName.contains("Custom:")) {
			                	String customName = strName.substring(8);
				                mSettings.edit().putString("scantool_baud", customName).apply();
				                mSettings.edit().putInt("checked_item", 6).apply();
				                checkedItem = 6;
				            }
			            	bindStringPreferenceSummaryToValue(findPreference("scantool_baud"));
							dialog.dismiss();
			            }
					})
			        .setNeutralButton("EDIT CUSTOM", new DialogInterface.OnClickListener() {
		                public void onClick(DialogInterface dialog, int id) {
		                	// CUSTOM BAUD      
					        LayoutInflater editCustom = LayoutInflater.from(getActivity());
					        final View textEntryView = editCustom.inflate(R.layout.dialog, null);              
					        final EditText editText = (EditText)textEntryView.findViewById(R.id.edit_text);
					        final AlertDialog.Builder builderCustom = new AlertDialog.Builder(getActivity());				  
					        builderCustom     
					        .setTitle("Custom baud rate")
					        .setView(textEntryView)
					        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					                public void onClick(DialogInterface dialog, int id) { 
					                	String inputValue = editText.getText().toString().trim();
								        mSettings.edit().putString("custom_baud_value", inputValue).apply();
							        	checkedItem = 6;
							        	mSettings.edit().putString("scantool_baud", inputValue).apply();
							        	bindStringPreferenceSummaryToValue(findPreference("scantool_baud"));
							        	bauds = new String[]{"2400", "9600", "19200", "38400", "57600", "115200", "Custom: " + inputValue};	
					                }
					            })
					        .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
					                public void onClick(DialogInterface dialog, int id) {      
					                    dialog.dismiss();
					                }
					            });
					        AlertDialog customDialog = builderCustom.create();
					        customDialog.show();
		                	((AlertDialog) customDialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);					          
							editText.addTextChangedListener(new TextWatcher() {
							    @Override
							    public void afterTextChanged(Editable s) {
							        //
							    }

							    @Override
							    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
							        //
							    }

							    @Override
							    public void onTextChanged(CharSequence s, int start, int before, int count) {
									String patternOne = "^[0]+$"; 
									String patternTwo = "^[0]+[0-9]*$"; 
									String patternThree = "^.{2,7}$";
									
									Pattern patOne = Pattern.compile(patternOne);
									Pattern patTwo = Pattern.compile(patternTwo);
									Pattern patThree = Pattern.compile(patternThree);

									Matcher matcherOne = patOne.matcher(s);
									Matcher matcherTwo = patTwo.matcher(s);
									Matcher matcherThree = patThree.matcher(s);

	    							boolean foundOne = matcherOne.find();
	    							boolean foundTwo = matcherTwo.find();
	    							boolean foundThree = matcherThree.find();
	    																						        
							        if (TextUtils.isEmpty(s) || foundOne || foundTwo || !foundThree) {
							            ((AlertDialog) customDialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
							        } else {
							            ((AlertDialog) customDialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
							        }
							    } 
							});	
		                }
		            })
			        .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
			                public void onClick(DialogInterface dialog, int id) {      
			                    dialog.dismiss();
			                }
			            });
			        final AlertDialog alertDialog = builder.create();
			        alertDialog.show();
	                return true;
	           
	         }
	     });
				
		//for preference types with string values
		bindStringPreferenceSummaryToValue(findPreference("scantool_baud"));
		bindStringPreferenceSummaryToValue(findPreference("scantool_device_number"));
		bindStringPreferenceSummaryToValue(findPreference("scantool_monitor_command"));
		bindStringPreferenceSummaryToValue(findPreference("scantool_protocol"));
        
        for (int m = 1; m <= 10; m++) {
            bindStringPreferenceSummaryToValue(findPreference("elm_monitor" + m));
        }
	}
		
	private void bindStringPreferenceSummaryToValue(Preference preference) {
		preference.setOnPreferenceChangeListener(mStringPreferenceChangeListener);

		mStringPreferenceChangeListener.onPreferenceChange(
				preference,
				PreferenceManager.getDefaultSharedPreferences(preference.getContext()).getString(preference.getKey(), ""));
	}
	
	private OnPreferenceChangeListener mStringPreferenceChangeListener = new Preference.OnPreferenceChangeListener() {
		@Override
		public boolean onPreferenceChange(Preference preference, Object value) {
			String stringValue = value.toString();
			
			if (preference instanceof ListPreference) {
				ListPreference list = (ListPreference)preference;
				stringValue = (String)list.getEntries()[list.findIndexOfValue(stringValue)];
			}
			
			preference.setSummary(stringValue);
			
			return true;
		}
	};
}