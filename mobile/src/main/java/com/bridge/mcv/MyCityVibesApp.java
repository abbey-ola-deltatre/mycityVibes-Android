package com.bridge.mcv;

import android.app.Application;

import com.onesignal.OneSignal;

/**
 * Created by abbey.ola on 23/01/2017.
 */

public class MyCityVibesApp extends Application
{
	private static MyCityVibesApp singleton;

	public static MyCityVibesApp getInstance(){
		return singleton;
	}
	@Override
	public void onCreate() {
		super.onCreate();
		OneSignal.startInit(this).init();
		singleton = this;
	}
}
