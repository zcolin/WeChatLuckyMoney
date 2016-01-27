package com.example.wxqhb;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends Activity
{

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		findViewById(R.id.start_button).setOnClickListener(new View.OnClickListener()
		{

			@Override
			public void onClick(View v)
			{
				openSetting();
			}
		});
	}

	private void openSetting()
	{
		try
		{
			Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
			startActivity(intent);
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}
