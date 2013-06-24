/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
 */

package com.iitb.vpeub;

import org.apache.cordova.DroidGap;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;

public class VPEUB extends DroidGap
{
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		super.setIntegerProperty("loadUrlTimeoutValue", 60000);


		super.init(); 
		// Calling this is necessary to call java code (native code) from JavaScript
		appView.addJavascriptInterface(this, "MainActivity");

		// "this" points the to the object of the current activity. 
		//"MainActivity" is used to refer "this" object in JavaScript

		super. loadUrl("file:///android_asset/www/blockly/apps/blocklyduino/main.html");

	}
	
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		//super.onConfigurationChanged(newConfig);
	}
	

	/*
	 * Function to start the Editor Activity
	 */
	public void customFunctionCalled() {
		Log.e("Custom Function Called", "Custom Function Called");

		Intent intent = new Intent(this,EditorActivity.class);	
		startActivity(intent); 
	} 
	

}

