package mapin.com.ai.voipapp.activity;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.ListIterator;

import butterknife.BindString;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnTouch;
import mapin.com.ai.voipapp.R;
import mapin.com.ai.voipapp.restcomm.MainSipListener;
import mapin.com.ai.voipapp.restcomm.SipEventsListener;
import mapin.com.ai.voipapp.restcomm.SipService;

public class PushToTalkActivity implements ServiceConnection {

    private static final String TAG = PushToTalkActivity.class.getSimpleName();

    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.call_status_textView)
    TextView callStatusTextView;
    @BindView(R.id.call_timer)
    Chronometer callTimer;
    @BindView(R.id.push_imageButton)
    ImageButton pushButton;
    @BindView(R.id.call_or_hangup_imageButton)
    ImageButton callOrHangupButton;
    @BindView(R.id.speaker_earpiece_switch_imageButton)
    ImageButton speakerEarpieceSwitchButton;

    @BindString(R.string.initializing)
    String statusInitializing;
    @BindString(R.string.dialing)
    String statusDialing;
    @BindString(R.string.call_failed)
    String statusCallFailed;
    @BindString(R.string.call_end)
    String statusCallEnd;
    @BindString(R.string.call_resuming)
    String statusCallResuming;
    @BindString(R.string.call_connected)
    String statusCallConnected;
    @BindString(R.string.call_active)
    String statusCallActive;
    @BindString(R.string.force_hangup_previous_call)
    String forceHangupPrevCallMessage;
    @BindString(R.string.activate_failed)
    String zoiperActivationFailed;

    boolean serviceBound = false;
    private final int PERMISSION_REQUEST_DANGEROUS = 1;
    private MainSipListener mainSip;
    private Boolean useIce = true;
    private SipEventsListener listener;

    private String groupCode, group, user, password;
    private static PushToTalkActivity instance;
    private Constants.LINPHONE_STATE callState;
    private boolean openFromNotification;

    private Context context = AirsideApp.getInstance().getApplicationContext();

    public static Context getMyApplicationContext() {
        return applicationContext;
    }

    private static Context applicationContext;

    public static PushToTalkActivity instance() {
        return instance;
    }

    public static boolean isInstanciated() {
        return instance != null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pushtotalk);
        ButterKnife.bind(this);

        groupCode = getIntent().getStringExtra("groupCode");
        group = getIntent().getStringExtra("groupCode");
        user = getIntent().getStringExtra("freeswitchUser");
        password = getIntent().getStringExtra("freeswitchPassword");

        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(Utils.getGroupName(this, groupCode));

        applicationContext = getApplicationContext();

        // make sure the group code is prefixed by "*"
        if (!group.startsWith("*"))
        {
            group = "*" + group;
        }

        initSipListener();

        mainSip = SipService.instance().getMainSip();
        mainSip.attachListener(listener);
        if (group != null) {
            mainSip.setGroup(group);
        }

        openFromNotification = false;
        if(getIntent()!=null && getIntent().getBooleanExtra("resumeCall",false)){
            callState = Constants.LINPHONE_STATE.On_Call;
        }else {
            callState = Constants.LINPHONE_STATE.Start_Call;
        }

        initUI();

        instance = this;
    }

    private void initSipListener() {
        listener = new SipEventsListener() {
            @Override
            public void onOutgoing() {
                android.util.Log.v("SipEventsListener", "SipEventsListener : onOutgoing");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        callState = Constants.LINPHONE_STATE.Dialing;
                        callStatusTextView.setText(statusDialing);
                        updateCallOrHangupButton();
                    }
                });
            }

            @Override
            public void onConnected() {
                android.util.Log.v("SipEventsListener", "SipEventsListener : onConnected");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        callStatusTextView.setText(statusCallActive);
                        callState = Constants.LINPHONE_STATE.On_Call;
                        sendStartCallRequest(groupCode);
                        updateCallOrHangupButton();
                        startCallTimer();
                        enableClickableCallUI();
                    }
                });
            }

            @Override
            public void onDisconnected() {
                android.util.Log.v("SipEventsListener", "SipEventsListener : onDisconnected");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        callStatusTextView.setText(statusCallEnd);
                        callState = Constants.LINPHONE_STATE.End_Call;
                        updateCallOrHangupButton();
                        stopCallTimer();
                        enableClickableCallUI();
                    }
                });
            }

            @Override
            public void onCallFailed() {
                android.util.Log.v("SipEventsListener", "SipEventsListener : onCallFailed");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        callStatusTextView.setText(statusCallFailed);
                        callState = Constants.LINPHONE_STATE.End_Call;
                        updateCallOrHangupButton();
                        stopCallTimer();
                        enableClickableCallUI();
                    }
                });
            }
        };
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogUtil.d(TAG, "onDestroy");
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(listener == null){
            initSipListener();
        }

        if(getIntent()!=null && getIntent().getBooleanExtra("Notification",false)){
            openFromNotification = true;
        }

        if (handlePermissions(false))
        {
            resumeCall();
        }
        //startCallTimer();
        refreshCallUI();
    }

    private void resumeCall()
    {

        if(!mainSip.checkListener()) {
            mainSip.attachListener(listener);
        }

        if (group != null && !group.startsWith("*"))
        {
            group = "*" + group;
        }
        if (group != null) {
            mainSip.setGroup(group);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    mainSip.start(true);
                }
            }).start();
        }
    }

    @Override
    protected void onPause() {
        stopCallTimer();
        super.onPause();
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        serviceUnbind();
    }

    public void serviceUnbind(){
        // Unbind from the service
        if (serviceBound) {
            //device.detach();
            unbindService(this);
            serviceBound = false;
        }
    }

    @Override
    public void onServiceConnected(ComponentName className, IBinder service)
    {
        android.util.Log.i(TAG, "%% onServiceConnected");
        // We've bound to LocalService, cast the IBinder and get LocalService instance

        // The SDK provides the user with default sounds for calling, ringing, busy (declined) and message, but the user can override them
        // by providing their own resource files (i.e. .wav, .mp3, etc) at res/raw passing them with Resource IDs like R.raw.user_provided_calling_sound
        //params.put(RCDevice.ParameterKeys.RESOURCE_SOUND_CALLING, R.raw.user_provided_calling_sound);
        //params.put(RCDevice.ParameterKeys.RESOURCE_SOUND_RINGING, R.raw.user_provided_ringing_sound);
        //params.put(RCDevice.ParameterKeys.RESOURCE_SOUND_DECLINED, R.raw.user_provided_declined_sound);
        //params.put(RCDevice.ParameterKeys.RESOURCE_SOUND_MESSAGE, R.raw.user_provided_message_sound);

        // This is for debugging purposes, not for release builds
        //params.put(RCDevice.ParameterKeys.SIGNALING_JAIN_SIP_LOGGING_ENABLED, prefs.getBoolean(RCDevice.ParameterKeys.SIGNALING_JAIN_SIP_LOGGING_ENABLED, true));

        //if (!device.isInitialized()) {
        //   try {
        //      device.initialize(getApplicationContext(), params, this);
        //      device.setLogLevel(Log.VERBOSE);
        //   }
        //   catch (RCException e) {
        //      Log.e(TAG, "RCDevice Initialization Error: " + e.errorText);
        //   }
        //}

        serviceBound = true;
    }

    @Override
    public void onServiceDisconnected(ComponentName arg0)
    {
        android.util.Log.i(TAG, "%% onServiceDisconnected");
        serviceBound = false;
    }

    private void checkEmulatorMode() {
        if (Constants.EMULATOR_MODE_ENABLED) {
            // Not able to start and run Linphone in emulator mode
            Toast.makeText(this,
                    "Push TO Talk feature is disabled in Emulator mode. Build on an Android device to use it",
                    Toast.LENGTH_LONG).show();
            onBackPressed();
            return;
        }
    }

    private synchronized void placeCall() {
        if (callTimer.getVisibility() == View.VISIBLE) {
            callTimer.setVisibility(View.INVISIBLE);
        }

        handlePermissions(false);
        callState = Constants.LINPHONE_STATE.Dialing;
    }

    private synchronized void hangupCall() {
        new Thread(() -> {
            sendEndCallRequest(groupCode);
            mainSip.hangUpCall();
            listener.onDisconnected();
            groupCode = null;
        }).start();
        callState = Constants.LINPHONE_STATE.End_Call;
        serviceUnbind();
        onBackPressed();
    }

    private void startCallTimer() {
        if (mainSip != null) {
            int callDuration = 0;
            //TODO Sahib try maintaining like Linphone

            callStatusTextView.setText("Call active");
            callTimer.setVisibility(View.VISIBLE);
            callTimer.setBase(SystemClock.elapsedRealtime() - 1000 * callDuration);
            callTimer.start();
        }
    }

    private void stopCallTimer() {
        if (callTimer != null) {
            callTimer.stop();
        }
    }

    @OnClick(R.id.call_or_hangup_imageButton)
    public void onCallOrHangupClicked() {
        if (callState == Constants.LINPHONE_STATE.Start_Call ||callState == Constants.LINPHONE_STATE.Dialing || callState == Constants.LINPHONE_STATE.On_Call) {
            hangupCall();
        } else {
            placeCall();
        }
    }

    private void updateCallOrHangupButton() {
        if (callState == Constants.LINPHONE_STATE.On_Call || callState == Constants.LINPHONE_STATE.Dialing || callState == Constants.LINPHONE_STATE.Start_Call) {
            callOrHangupButton.setImageResource(R.drawable.ic_call_end);
            callOrHangupButton.setBackgroundResource(R.drawable.circular_call_end);
        } else if(callState == Constants.LINPHONE_STATE.End_Call) {
            callOrHangupButton.setImageResource(R.drawable.ic_call);
            callOrHangupButton.setBackgroundResource(R.drawable.circular_call);
        }
    }

    @OnTouch(R.id.push_imageButton)
    public boolean onPushButtonClicked(View v, MotionEvent event) {

//        int recordAudio = getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName());
//        Log.i("[Permission] Record audio permission is " + (recordAudio == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            toggleMicro(false);
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            toggleMicro(true);
        }

        return true;
    }

    private void toggleMicro(boolean micMuted) {
        if (mainSip != null) {
            mainSip.micMuted(micMuted);
        }
        if (micMuted) {
            pushButton.setImageResource(R.drawable.ic_microphone_off);
        } else {
            pushButton.setImageResource(R.drawable.ic_microphone_on);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_go_chat, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_go_chat:
                backToChat();
                break;
            default:
                break;
        }

        return true;
    }

    private void initUI() {
        toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PushToTalkActivity.super.onBackPressed();
            }
        });

        speakerEarpieceSwitchButton.setVisibility(View.GONE);
    }

    private void enableClickableCallUI() {
        callOrHangupButton.setClickable(true);
        pushButton.setClickable(true);
    }

    private void disableClickableCallUI() {
        callOrHangupButton.setClickable(false);
        pushButton.setClickable(false);
    }

    private void refreshCallUI() {
        updateCallOrHangupButton();
        updateCallStatusTextView();
    }

    private void updateCallStatusTextView() {
        if (mainSip != null) {
            if (callState == Constants.LINPHONE_STATE.Dialing || callState == Constants.LINPHONE_STATE.Start_Call) {
                callStatusTextView.setText(statusDialing);
            } else if (callState == Constants.LINPHONE_STATE.On_Call) {
                callStatusTextView.setText(statusCallActive);
            } else {
                callStatusTextView.setText(statusCallEnd);
            }
        }
    }

    private void backToChat() {
        if(!openFromNotification)
            super.onBackPressed();
        else{
            Intent intent = new Intent(this,BottomNavigationActivity.class);
            //clear all other top activity, and push BottomNavigationActivity to top, not create new one for BottomNavigationActivity
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            this.finish();
        }
    }

    // Handle android permissions needed for Marshmallow (API 23) devices or later
    private boolean handlePermissions(boolean isVideo)
    {
        ArrayList<String> permissions = new ArrayList<>(Arrays.asList(Manifest.permission.MODIFY_AUDIO_SETTINGS, Manifest.permission.RECORD_AUDIO, Manifest.permission.USE_SIP));
        if (isVideo) {
            // Only add CAMERA permission if this is a video call
            permissions.add(Manifest.permission.CAMERA);
        }

        if (!havePermissions(permissions)) {
            // Dynamic permissions where introduced in M
            // PERMISSION_REQUEST_DANGEROUS is an app-defined int constant. The callback method (i.e. onRequestPermissionsResult) gets the result of the request.
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[permissions.size()]), PERMISSION_REQUEST_DANGEROUS);

            return false;
        }

        return true;
    }

    // Checks if user has given 'permissions'. If it has them all, it returns true. If not it returns false and modifies 'permissions' to keep only
    // the permission that got rejected, so that they can be passed later into requestPermissions()
    private boolean havePermissions(ArrayList<String> permissions)
    {
        boolean allgranted = true;
        ListIterator<String> it = permissions.listIterator();
        while (it.hasNext()) {
            if (ActivityCompat.checkSelfPermission(this, it.next()) != PackageManager.PERMISSION_GRANTED) {
                allgranted = false;
            }
            else {
                // permission granted, remove it from permissions
                it.remove();
            }
        }
        return allgranted;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_REQUEST_DANGEROUS: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the contacts-related task you need to do.
                    resumeCall();

                } else {
                    // permission denied, boo! Disable the functionality that depends on this permission.
                    android.util.Log.e(TAG, "Error: Permission(s) denied; aborting call");
                }
                return;
            }

            // other 'case' lines to check for other permissions this app might request
        }
    }

    private void sendStartCallRequest(final String groupCode){
        Thread startCallTask = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String userSessionToken = LoggedInUser_SharedPreference.getUserSessionToken(context);
                    String userId = LoggedInUser_SharedPreference.getUId(context);
                    Call<ATEmptyRS> startCallReq = MyRetroInterceptor.create(context, GroupAPICalls.class).startGroupCallReq(userSessionToken,groupCode,userId);
                    ATEmptyRS res = startCallReq.execute().body();
                    if(res!=null && res.isSuccess())
                        LogUtil.i(TAG,"Start Call Request Response OK");
                    else
                        LogUtil.i(TAG,"Start Call Request Response Fail");
                }catch (Exception ex){
                    LogUtil.e(TAG,ex.getMessage());
                }

            }
        });
        startCallTask.start();
    }

    private void sendEndCallRequest(final String groupCode){
        Thread endCallTask = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String userSessionToken = LoggedInUser_SharedPreference.getUserSessionToken(context);
                    String userId = LoggedInUser_SharedPreference.getUId(context);
                    Call<ATEmptyRS> endCallReq = MyRetroInterceptor.create(context, GroupAPICalls.class).endGroupCallReq(userSessionToken,groupCode,userId);
                    ATEmptyRS res = endCallReq.execute().body();
                    if(res!=null && res.isSuccess())
                        LogUtil.i(TAG,"End Call Request Response OK");
                    else
                        LogUtil.i(TAG,"End Call Request Response Fail");
                }catch (Exception ex){
                    LogUtil.e(TAG,ex.getMessage());
                }

            }
        });
        endCallTask.start();
    }

}
