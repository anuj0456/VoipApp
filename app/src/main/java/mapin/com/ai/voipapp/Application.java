package mapin.com.ai.voipapp;

import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.multidex.MultiDexApplication;

import mapin.com.ai.voipapp.restcomm.SipService;

public class Application extends MultiDexApplication {

    public static Application mInstance;

    @Override
    public void onCreate() {
        super.onCreate();
        mInstance = this;
    }

    public static synchronized Application getInstance() {
        return mInstance;
    }

    public void startSipService(){
        if(Utils.isPushToTalkEnable()){
            try {
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    startService(new Intent(Intent.ACTION_MAIN).setClass(this, SipService.class));
                } else {
                    //startService(new Intent(Intent.ACTION_MAIN).setClass(this, LinphoneService.class));
                }
            }catch (Exception e){
                Log.i("PTT Exception", "Service not initialised");
            }
        }
    }

    public void stopSipService(){
        if(Utils.isPushToTalkEnable()) {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopService(new Intent(Intent.ACTION_MAIN).setClass(this, SipService.class));
            } else {
                //stopService(new Intent(Intent.ACTION_MAIN).setClass(this, LinphoneService.class));
            }
        }
    }

    public void registerPushToTalkUser() {
        if (Constants.EMULATOR_MODE_ENABLED) {
            return;
        }

        if(!Utils.isPushToTalkEnable()){
            return;
        }

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return;
        }

        Log.i("registerPTTUser", "call registeringUser");
    }

    public void unregisterPushToTalkUser() {
        if (Constants.EMULATOR_MODE_ENABLED) {
            return;
        }

        if(!Utils.isPushToTalkEnable()){
            return;
        }

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return;
        }

        Log.d("registerPTTUser", "unregisteringUser");
    }
}
