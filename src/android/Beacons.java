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
package com.redant.cordova.beacons;

import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.RemoteException;

import com.radiusnetworks.ibeacon.IBeacon;
import com.radiusnetworks.ibeacon.IBeaconConsumer;
import com.radiusnetworks.ibeacon.IBeaconManager;
import com.radiusnetworks.ibeacon.MonitorNotifier;
import com.radiusnetworks.ibeacon.RangeNotifier;
import com.radiusnetworks.ibeacon.Region;

public class Beacons extends CordovaPlugin implements IBeaconConsumer {
	
    public static final String TAG = "com.redant.cordova.beacons";
    
    private IBeaconManager iBeaconManager;
    private BlockingQueue<Runnable> queue;
    private PausableThreadPoolExecutor threadPoolExecutor;
    
    private boolean debugEnabled = true;
    private IBeaconServiceNotifier beaconServiceNotifier; 
    
    //listener for changes in state for system Bluetooth service
	private BroadcastReceiver broadcastReceiver; 

	private CallbackContext callbackId;
	private Integer sensitivity;
	private Region region;

    /**
     * Constructor.
     */
    public Beacons() {
    }

    /**
     * Sets the context of the Command. This can then be used to do things like
     * get file paths associated with the Activity.
     *
     * @param cordova The context of the main Activity.
     * @param webView The CordovaWebView Cordova is running in.
     */
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

    }
    
    /**
     * The final call you receive before your activity is destroyed.
     */ 
    @Override
    public void onDestroy() {
    	iBeaconManager.unBind(this);
    	
    	if (broadcastReceiver != null) {
    		cordova.getActivity().unregisterReceiver(broadcastReceiver);
    		broadcastReceiver = null;
    	}
    	
    	super.onDestroy(); 
    }


    
	//////////////// PLUGIN ENTRY POINT /////////////////////////////
    /**
     * Executes the request and returns PluginResult.
     *
     * @param action            The action to execute.
     * @param args              JSONArray of arguments for the plugin.
     * @param callbackContext   The callback id used when calling back into JavaScript.
     * @return                  True if the action was valid, false if not.
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        if (action.equals("initMonitoringBeacons")) {
             
        	callbackId = callbackContext;
        	
            initBluetoothListener();
            initEventQueue();
            initBeacons(args);

        } else {
            return false;
        }
        return true;
    }

	///////////////// SETUP AND VALIDATION /////////////////////////////////
    
    private void initBeacons(JSONArray args) {
      
		try {
			
			sensitivity = Integer.valueOf(args.getString(0));
			String uuid = args.getString(1);//@"f7826da6-4fa2-4e98-8024-bc5b71e0893e";
			String identifier = args.getString(2); //@"Beacons";
			
			region = new Region(identifier, uuid, null, null);
			
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
    	
    	iBeaconManager = IBeaconManager.getInstanceForApplication(cordova.getActivity());
        iBeaconManager.bind(this);
        
        createMonitorCallbacks();
		createRangingCallbacks();
    }
    
	@Override
	public void onIBeaconServiceConnect() {
		debugLog("Connected to IBeacon service");
		
		try {
			
			iBeaconManager.startMonitoringBeaconsInRegion(region);

		} catch (RemoteException e) {   
        	Log.e(TAG, "'startMonitoringForRegion' service error: " + e.getMessage());
		} catch (Exception e) {
			Log.e(TAG, "'startMonitoringForRegion' exception "+e.getMessage());
        }
	}

	private void initBluetoothListener() {
	
		//check access
		if (!hasBlueToothPermission()) {
			debugWarn("Cannot listen to Bluetooth service when BLUETOOTH permission is not added");
			return;
		}
		
		//check device support
		try {
			iBeaconManager.checkAvailability();
		} catch (Exception e) {
			//if device does not support iBeacons an error is thrown
			debugWarn("Cannot listen to Bluetooth service: "+e.getMessage());
			return;
		}
		
		if (broadcastReceiver != null) {
			debugWarn("Already listening to Bluetooth service, not adding again");
			return;
		}
		
		broadcastReceiver = new BroadcastReceiver() {
		    @Override
		    public void onReceive(Context context, Intent intent) {
		        final String action = intent.getAction();
	
		        // Only listen for Bluetooth server changes
		        if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
		        	
		            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,BluetoothAdapter.ERROR);
		            final int oldState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE,BluetoothAdapter.ERROR);
		            		            
		            debugLog("Bluetooth Service state changed from "+getStateDescription(oldState)+" to " + getStateDescription(state));
		            
		            switch (state) {
			            case BluetoothAdapter.ERROR:
			            	beaconServiceNotifier.didChangeAuthorizationStatus("AuthorizationStatusNotDetermined");
			                break;
			            case BluetoothAdapter.STATE_OFF:
			            case BluetoothAdapter.STATE_TURNING_OFF:
				        	if (oldState==BluetoothAdapter.STATE_ON)
			            		beaconServiceNotifier.didChangeAuthorizationStatus("AuthorizationStatusDenied");
			                break;
			            case BluetoothAdapter.STATE_ON:
			            	beaconServiceNotifier.didChangeAuthorizationStatus("AuthorizationStatusAuthorized");
			                break;
			            case BluetoothAdapter.STATE_TURNING_ON:
			            	break;
		            }
		        }
		    }
		    
		    private String getStateDescription(int state) {
	            switch (state) {
		            case BluetoothAdapter.ERROR:
		            	return "ERROR";
		            case BluetoothAdapter.STATE_OFF:
		            	return "STATE_OFF";
		            case BluetoothAdapter.STATE_TURNING_OFF:
		            	return "STATE_TURNING_OFF";
		            case BluetoothAdapter.STATE_ON:
		            	return "STATE_ON";
		            case BluetoothAdapter.STATE_TURNING_ON:
		            	return "STATE_TURNING_ON";
	            }
	            return "ERROR"+state;
		    }
		};
		
		// Register for broadcasts on BluetoothAdapter state change
	    IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
    	cordova.getActivity().registerReceiver(broadcastReceiver, filter);
	}	
	
	private void initEventQueue() {
		//queue is limited to one thread at a time
	    queue = new LinkedBlockingQueue<Runnable>();
	    threadPoolExecutor = new PausableThreadPoolExecutor(queue);
	        
	}
	
	///////// CALLBACKS ////////////////////////////
	
	private void createMonitorCallbacks() {
		
		//Monitor callbacks
		iBeaconManager.setMonitorNotifier(new MonitorNotifier() {
            @Override
            public void didEnterRegion(Region region) {
            	debugLog("didEnterRegion INSIDE for "+region.getUniqueId());
            	
            	try {
            		iBeaconManager.startRangingBeaconsInRegion(region);

				} catch (RemoteException e) {   
		        	Log.e(TAG, "'startRangingBeaconsInRegion' service error: " + e.getCause());
				} catch (Exception e) {
					Log.e(TAG, "'startRangingBeaconsInRegion' exception "+e.getCause());
		        }
            }

            @Override
            public void didExitRegion(Region region) {
            	debugLog("didExitRegion OUTSIDE for "+region.getUniqueId());
            	
            	try {
					iBeaconManager.stopRangingBeaconsInRegion(region);

				} catch (RemoteException e) {   
		        	Log.e(TAG, "'startRangingBeaconsInRegion' service error: " + e.getCause());
				} catch (Exception e) {
					Log.e(TAG, "'startRangingBeaconsInRegion' exception "+e.getCause());
		        }
            }

            @Override
			public void didDetermineStateForRegion(int state, Region region) {
            	debugLog("didDetermineStateForRegion for region: "+region.getUniqueId());
            }
        });
	}

	private void createRangingCallbacks() {
		
       iBeaconManager.setRangeNotifier(new RangeNotifier() {
	        @Override 
	        public void didRangeBeaconsInRegion(final Collection<IBeacon> iBeacons, final Region region) {
	           	
	        	threadPoolExecutor.execute(new Runnable() {
                    public void run() {
                    	
                    	try {
                    		if (iBeacons.size() > 0) {
                    		
                    			IBeacon beacon = null;
                    			
                    			for (IBeacon _beacon : iBeacons) {
                        			if (beacon == null) {
                        				beacon = _beacon;
                        			} else if (_beacon.getRssi() > beacon.getRssi()) {
                        				beacon = _beacon;
                        			}
                        		}
                    			
                    			if (beacon.getRssi() != 0 && beacon.getRssi() > sensitivity) {
                    				
                    				JSONObject beaconProperties = new JSONObject();
                    				beaconProperties.put("major", beacon.getMajor());
                    				beaconProperties.put("minor", beacon.getMinor());
                    				beaconProperties.put("rssi", beacon.getRssi());
                    				
                    				PluginResult result = new PluginResult(PluginResult.Status.OK, beaconProperties);
                					result.setKeepCallback(true);
                					callbackId.sendPluginResult(result);
                    			}
                    		}
                    		
           				} catch (Exception e) {
        					Log.e(TAG, "'rangingBeaconsDidFailForRegion' exception "+e.getCause());
        					beaconServiceNotifier.rangingBeaconsDidFailForRegion(region, e);
        				}
                    }
                });
	        }
	        
	    });

	}

    /////////// SERIALISATION /////////////////////

	private boolean hasBlueToothPermission()
	{
		Context context = cordova.getActivity();
	    int access = context.checkCallingOrSelfPermission(Manifest.permission.BLUETOOTH);
	    int adminAccess = context.checkCallingOrSelfPermission(Manifest.permission.BLUETOOTH_ADMIN); 
	    		
	    return (access == PackageManager.PERMISSION_GRANTED) && (adminAccess == PackageManager.PERMISSION_GRANTED);
	}
  
	private void debugLog(String message) {
		if (debugEnabled) {
			Log.d(TAG, message);
		}
	}
	
	private void debugWarn(String message) {
		if (debugEnabled) {
			Log.w(TAG, message);
		}
	}
    
    //////// IBeaconConsumer implementation /////////////////////

	@Override
	public Context getApplicationContext() {
		return cordova.getActivity();
	}

	@Override
	public void unbindService(ServiceConnection connection) {
		debugLog("Unbind from IBeacon service");
		cordova.getActivity().unbindService(connection);
	}

	@Override
	public boolean bindService(Intent intent, ServiceConnection connection, int mode) {
		debugLog("Bind to IBeacon service");
		return cordova.getActivity().bindService(intent, connection, mode);
	}

}
