package com.bridge.mycityvibes.ui;

import android.os.Bundle;

import com.bridge.mycityvibes.R;

/**
 * Created by abbey.ola on 18/01/2017.
 */

public class LoginActivity extends BaseActivity
{
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_login);
		initializeToolbar();

	}
}
