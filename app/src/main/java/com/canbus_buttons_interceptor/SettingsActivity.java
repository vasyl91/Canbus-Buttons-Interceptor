package com.canbus_buttons_interceptor;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import com.canbus_buttons_interceptor.R;

public class SettingsActivity extends AppCompatActivity implements FragmentManager.OnBackStackChangedListener {

    private SharedPreferences mSettings;
    protected Context mAppContext;
    private Fragment fragment;
        
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setTheme(R.style.AppTheme);  
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportFragmentManager().addOnBackStackChangedListener(this);        

        Context mAppContext = getApplicationContext();
        mSettings = PreferenceManager.getDefaultSharedPreferences(mAppContext);

        if (savedInstanceState == null) {
            PreferenceFragment mSettingsFragment = new SettingsFragment();          
            getFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, mSettingsFragment)
                    .commit();
        }
        else {
            onBackStackChanged();
        }
    }

    @Override
    public void onBackStackChanged() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(getSupportFragmentManager().getBackStackEntryCount()>0);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}