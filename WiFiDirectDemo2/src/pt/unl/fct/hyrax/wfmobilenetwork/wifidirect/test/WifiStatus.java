/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pt.unl.fct.hyrax.wfmobilenetwork.wifidirect.test;

//import com.android.settings.R;

import android.content.Context;
import android.net.NetworkInfo;
import android.text.TextUtils;

public class WifiStatus {
    public static String getStatus(Context context, String ssid,
            NetworkInfo.DetailedState detailedState) {
        
        if (!TextUtils.isEmpty(ssid) && isLiveConnection(detailedState)) {
            return getPrintableFragment(context, detailedState, ssid);
        } else {
            return getPrintable(context, detailedState);
        }
    }
    
    public static boolean isLiveConnection(NetworkInfo.DetailedState detailedState) {
        return detailedState != NetworkInfo.DetailedState.DISCONNECTED
                && detailedState != NetworkInfo.DetailedState.FAILED
                && detailedState != NetworkInfo.DetailedState.IDLE
                && detailedState != NetworkInfo.DetailedState.SCANNING;
    }
    
    public static String getPrintable(Context context,
            NetworkInfo.DetailedState detailedState) {
        
        switch (detailedState) {
            case AUTHENTICATING:
                return "status_authenticating";
            case CONNECTED:
                return "status_connected";
            case CONNECTING:
                return "status_connecting";
            case DISCONNECTED:
                return "status_disconnected";
            case DISCONNECTING:
                return "status_disconnecting";
            case FAILED:
                return "status_failed";
            case OBTAINING_IPADDR:
                return "status_obtaining_ip";
            case SCANNING:
                return "status_scanning";
            default:
                return null;
        }
    }
    
    public static String getPrintableFragment(Context context,
            NetworkInfo.DetailedState detailedState, String apName) {
        
        String fragment = null;
        switch (detailedState) {
            case AUTHENTICATING:
                fragment = "fragment_status_authenticating";
                break;
            case CONNECTED:
                fragment = "fragment_status_connected";
                break;
            case CONNECTING:
                fragment = "fragment_status_connecting";
                break;
            case DISCONNECTED:
                fragment = "fragment_status_disconnected";
                break;
            case DISCONNECTING:
                fragment = "fragment_status_disconnecting";
                break;
            case FAILED:
                fragment = "fragment_status_failed";
                break;
            case OBTAINING_IPADDR:
                fragment = "fragment_status_obtaining_ip";
                break;
            case SCANNING:
                fragment = "fragment_status_scanning";
                break;
        }
        
        return String.format(fragment, apName);
    }
    
}
