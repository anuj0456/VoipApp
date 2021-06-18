package mapin.com.ai.voipapp.restcomm;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.WindowManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import mapin.com.ai.voipapp.Constants;
import mapin.com.ai.voipapp.R;
import mapin.com.ai.voipapp.activity.PushToTalkActivity;

public class SipService extends Service {
    public static final int IC_LEVEL_ORANGE=0;

    private final static int NOTIF_ID=1;
    private final static int INCALL_NOTIF_ID=2;
    private final static int MESSAGE_NOTIF_ID=3;
    private final static int CUSTOM_NOTIF_ID=4;
    private final static int MISSED_NOTIF_ID=5;

    private static SipService _INSTANCE;
    private static MainSipListener mainSip;

    public static boolean isReady() {
        return _INSTANCE != null && _INSTANCE.mTestDelayElapsed;
    }

    /**
     * @throws RuntimeException service not instantiated
     */
    public static SipService instance()  {
        if (isReady()) return _INSTANCE;

        throw new RuntimeException("Sip Service not instantiated yet");
    }

    public Handler mHandler = new Handler();
    private boolean mTestDelayElapsed = true; // no timer

    private NotificationManager mNM;

    private Notification mNotif;
    private Notification mIncallNotif;
    private Notification mMsgNotif;
    private Notification mCustomNotif;
    private int mMsgNotifCount;
    private PendingIntent mNotifContentIntent, mMissedCallsNotifContentIntent;
    private String mNotificationTitle;
    private boolean mDisableRegistrationStatus = true;
    private SipEventsListener listener;
    public static int notifcationsPriority = (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN? Notification.PRIORITY_MIN : 0);
    private WindowManager mWindowManager;
    private Application.ActivityLifecycleCallbacks activityCallbacks;

    /*Believe me or not, but knowing the application visibility state on Android is a nightmare.
	After two days of hard work I ended with the following class, that does the job more or less reliabily.
	*/
    class ActivityMonitor implements Application.ActivityLifecycleCallbacks {
        private ArrayList<Activity> activities = new ArrayList<Activity>();
        private boolean mActive = false;
        private int mRunningActivities = 0;

        class InactivityChecker implements Runnable {
            private boolean isCanceled;

            public void cancel() {
                isCanceled = true;
            }

            @Override
            public void run() {
                synchronized(SipService.this) {
                    if (!isCanceled) {
                        if (SipService.ActivityMonitor.this.mRunningActivities == 0 && mActive) {
                            mActive = false;
                            SipService.this.onBackgroundMode();
                        }
                    }
                }
            }
        };

        private SipService.ActivityMonitor.InactivityChecker mLastChecker;

        @Override
        public synchronized void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            Log.i("SipService", "Activity created:" + activity);
            if (!activities.contains(activity))
                activities.add(activity);
        }

        @Override
        public void onActivityStarted(Activity activity) {
            Log.i("SipService", "Activity started:" + activity);
        }

        @Override
        public synchronized void onActivityResumed(Activity activity) {
            Log.i("SipService", "Activity resumed:" + activity);
            if (activities.contains(activity)) {
                mRunningActivities++;
                Log.i("SipService", "runningActivities=" + mRunningActivities);
                checkActivity();
            }

        }

        @Override
        public synchronized void onActivityPaused(Activity activity) {
            Log.i("SipService", "Activity paused:" + activity);
            if (activities.contains(activity)) {
                mRunningActivities--;
                Log.i("SipService", "runningActivities=" + mRunningActivities);
                checkActivity();
            }

        }

        @Override
        public void onActivityStopped(Activity activity) {
            Log.i("SipService", "Activity stopped:" + activity);
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

        }

        @Override
        public synchronized void onActivityDestroyed(Activity activity) {
            Log.i("SipService", "Activity destroyed:" + activity);
            if (activities.contains(activity)) {
                activities.remove(activity);
            }
        }

        void startInactivityChecker() {
            if (mLastChecker != null) mLastChecker.cancel();
            SipService.this.mHandler.postDelayed(
                    (mLastChecker = new SipService.ActivityMonitor.InactivityChecker()), 2000);
        }

        void checkActivity() {

            if (mRunningActivities == 0) {
                if (mActive) startInactivityChecker();
            } else if (mRunningActivities > 0) {
                if (!mActive) {
                    mActive = true;
                    SipService.this.onForegroundMode();
                }
                if (mLastChecker != null) {
                    mLastChecker.cancel();
                    mLastChecker = null;
                }
            }
        }
    }

    public MainSipListener getMainSip() {
        return mainSip;
    }

    protected void onBackgroundMode(){
        Log.i("SipService", "App has entered background mode");
    }

    protected void onForegroundMode() {
        Log.i("SipService", "App has left background mode");
    }

    private void setupActivityMonitor(){
        if (activityCallbacks != null) return;
        getApplication().registerActivityLifecycleCallbacks(activityCallbacks = new SipService.ActivityMonitor());
    }

    public int getMessageNotifCount() {
        return mMsgNotifCount;
    }

    public void resetMessageNotifCount() {
        mMsgNotifCount = 0;
    }

    private boolean displayServiceNotification() {
        return false;
    }

    public void hideServiceNotification() {
        stopForegroundCompat(NOTIF_ID);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mainSip == null) {
            initMainSip();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void initMainSip() {
        String cacheDir = this.getApplicationContext().getCacheDir().getAbsolutePath();
        System.setProperty("org.restcomm.CustomSecurityManagerProvider.cacheDir", cacheDir);
        mainSip = new MainSipListener(true, "user", "password", "ws://192.168.1.21:8080");
        new Thread(new Runnable() {
            @Override
            public void run() {
                mainSip.start(false);
            }
        }).start();
    }

    private enum IncallIconState {INCALL, PAUSE, VIDEO, IDLE}
    private SipService.IncallIconState mCurrentIncallIconState = SipService.IncallIconState.IDLE;
    private synchronized void setIncallIcon(SipService.IncallIconState state) {
        if (state == mCurrentIncallIconState) return;
        mCurrentIncallIconState = state;

        int notificationTextId = 0;
        int inconId = 0;

        switch (state) {
            case IDLE:
                mNM.cancel(INCALL_NOTIF_ID);
                return;
            case INCALL:
                inconId = R.drawable.topbar_call_notification;
                notificationTextId = R.string.incall_notif_active;
                break;
            case PAUSE:
                inconId = R.drawable.topbar_call_notification;
                notificationTextId = R.string.incall_notif_paused;
                break;
            default:
                throw new IllegalArgumentException("Unknown state " + state);
        }

        Bitmap bm = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher_background);
        BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher_background);
        String name = "";       //TODO get incoming call user name
        mIncallNotif = createInCallNotification(getApplicationContext(), mNotificationTitle, getString(notificationTextId), inconId, bm, name, mNotifContentIntent);

        notifyWrapper(INCALL_NOTIF_ID, mIncallNotif);
    }

    @Deprecated
    public void addNotification(Intent onClickIntent, int iconResourceID, String title, String message) {
        addCustomNotification(onClickIntent, iconResourceID, title, message, true);
    }

    public void addCustomNotification(Intent onClickIntent, int iconResourceID, String title, String message, boolean isOngoingEvent) {
        PendingIntent notifContentIntent = PendingIntent.getActivity(this, 0, onClickIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Bitmap bm = null;
        try {
            bm = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher_background);
        } catch (Exception e) {
        }
        mCustomNotif = createNotification(this, title, message, iconResourceID, 0, bm, notifContentIntent, isOngoingEvent,notifcationsPriority);

        mCustomNotif.defaults |= Notification.DEFAULT_VIBRATE;
        mCustomNotif.defaults |= Notification.DEFAULT_SOUND;
        mCustomNotif.defaults |= Notification.DEFAULT_LIGHTS;

        notifyWrapper(CUSTOM_NOTIF_ID, mCustomNotif);
    }

    private static final Class<?>[] mSetFgSign = new Class[] {boolean.class};
    private static final Class<?>[] mStartFgSign = new Class[] {
            int.class, Notification.class};
    private static final Class<?>[] mStopFgSign = new Class[] {boolean.class};

    private Method mSetForeground;
    private Method mStartForeground;
    private Method mStopForeground;
    private Object[] mSetForegroundArgs = new Object[1];
    private Object[] mStartForegroundArgs = new Object[2];
    private Object[] mStopForegroundArgs = new Object[1];

    void invokeMethod(Method method, Object[] args) {
        try {
            method.invoke(this, args);
        } catch (InvocationTargetException e) {
            // Should not happen.
            Log.w("SipService", e +"Unable to invoke method");
        } catch (IllegalAccessException e) {
            // Should not happen.
            Log.w("SipService", e + "Unable to invoke method");
        }
    }

    /**
     * This is a wrapper around the new startForeground method, using the older
     * APIs if it is not available.
     */
    void startForegroundCompat(int id, Notification notification) {
        // If we have the new startForeground API, then use it.
        if (mStartForeground != null) {
            mStartForegroundArgs[0] = Integer.valueOf(id);
            mStartForegroundArgs[1] = notification;
            invokeMethod(mStartForeground, mStartForegroundArgs);
            return;
        }

        // Fall back on the old API.
        if (mSetForeground != null) {
            mSetForegroundArgs[0] = Boolean.TRUE;
            invokeMethod(mSetForeground, mSetForegroundArgs);
            // continue
        }

        notifyWrapper(id, notification);
    }

    /**
     * This is a wrapper around the new stopForeground method, using the older
     * APIs if it is not available.
     */
    void stopForegroundCompat(int id) {
        // If we have the new stopForeground API, then use it.
        if (mStopForeground != null) {
            mStopForegroundArgs[0] = Boolean.TRUE;
            invokeMethod(mStopForeground, mStopForegroundArgs);
            return;
        }

        // Fall back on the old API.  Note to cancel BEFORE changing the
        // foreground state, since we could be killed at that point.
        mNM.cancel(id);
        if (mSetForeground != null) {
            mSetForegroundArgs[0] = Boolean.FALSE;
            invokeMethod(mSetForeground, mSetForegroundArgs);
        }
    }

    private synchronized void sendNotification(int level, int textId) {
        String text = getString(textId);
        //TODO handle
//        if (text.contains("%s") && LinphoneManager.getLc() != null) {
//            // Test for null lc is to avoid a NPE when Android mess up badly with the String resources.
//            LinphoneProxyConfig lpc = LinphoneManager.getLc().getDefaultProxyConfig();
//            String id = lpc != null ? lpc.getIdentity() : "";
//            text = String.format(text, id);
//        }

        Bitmap bm = null;
        try {
            bm = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher_background);
        } catch (Exception e) {
        }
        mNotif = createNotification(this, mNotificationTitle, text, R.drawable.status_level, 0, bm, mNotifContentIntent, true,notifcationsPriority);
        notifyWrapper(NOTIF_ID, mNotif);
    }

    @TargetApi(21)
    public Notification createNotification(Context context, String title, String message, int icon, int level, Bitmap largeIcon, PendingIntent intent, boolean isOngoingEvent, int priority) {
        Notification notif;

        if (largeIcon != null) {
            notif = new Notification.Builder(context)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setSmallIcon(icon, level)
                    .setLargeIcon(largeIcon)
                    .setContentIntent(intent)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setVisibility(Notification.VISIBILITY_SECRET)
                    .setPriority(priority)
                    .build();
        } else {
            notif = new Notification.Builder(context)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setSmallIcon(icon, level)
                    .setContentIntent(intent)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setVisibility(Notification.VISIBILITY_SECRET)
                    .setPriority(priority)
                    .build();
        }

        return notif;
    }

    @TargetApi(21)
    public static Notification createInCallNotification(Context context,
                                                        String title, String msg, int iconID, Bitmap contactIcon,
                                                        String contactName, PendingIntent intent) {

        Notification notif = new Notification.Builder(context).setContentTitle(contactName)
                .setContentText(msg)
                .setSmallIcon(iconID)
                .setAutoCancel(false)
                .setContentIntent(intent)
                .setLargeIcon(contactIcon)
                .setCategory(Notification.CATEGORY_CALL)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_HIGH)
                .build();

        return notif;
    }

    /**
     * Wrap notifier to avoid setting the linphone icons while the service
     * is stopping. When the (rare) bug is triggered, the linphone icon is
     * present despite the service is not running. To trigger it one could
     * stop linphone as soon as it is started. Transport configured with TLS.
     */
    private synchronized void notifyWrapper(int id, Notification notification) {
        if (_INSTANCE != null && notification != null) {
            mNM.notify(id, notification);
        } else {
            Log.i("SipService", "Service not ready, discarding notification");
        }
    }

    public class LocalBinder extends Binder {
        public SipService getService() {
            return SipService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public synchronized void onDestroy() {
        super.onDestroy();

        if (Constants.EMULATOR_MODE_ENABLED) {
            return;
        }

        if (activityCallbacks != null){
            getApplication().unregisterActivityLifecycleCallbacks(activityCallbacks);
            activityCallbacks = null;
        }

        if (mainSip != null) {
            mainSip.removeListener();
        }

        _INSTANCE = null;
        if (mainSip != null) {
            mainSip.stop();     //TODO handle crash
        }

        // Make sure our notification is gone.
        stopForegroundCompat(NOTIF_ID);
        mNM.cancel(INCALL_NOTIF_ID);
        mNM.cancel(MESSAGE_NOTIF_ID);
    }

    private void resetIntentLaunchedOnNotificationClick() {
        Intent notifIntent = new Intent(this, PushToTalkActivity.class);
        notifIntent.putExtra("Notification", true);
        notifIntent.putExtra("resumeCall", true);
        notifIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        mNotifContentIntent = PendingIntent.getActivity(this, 0, notifIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    protected void onIncomingReceived() {
        startActivity(new Intent()
                .setClass(this, PushToTalkActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate() {
        super.onCreate();

        setupActivityMonitor();
        // In case restart after a crash. Main in LinphoneActivity
        mNotificationTitle = getString(R.string.app_name);

        // Needed in order for the two next calls to succeed, libraries must have been loaded first
        if (mainSip == null) {
            initMainSip();
        }

        // Dump some debugging information to the logs

        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNM.cancel(INCALL_NOTIF_ID); // in case of crash the icon is not removed

        Intent notifIntent = new Intent(this, PushToTalkActivity.class);
        notifIntent.putExtra("Notification", true);
        notifIntent.putExtra("resumeCall", true);
        notifIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        mNotifContentIntent = PendingIntent.getActivity(this, 0, notifIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        _INSTANCE = this; // instance is ready once mainsiplistener manager has been initialized

        try {
            mStartForeground = getClass().getMethod("startForeground", mStartFgSign);
            mStopForeground = getClass().getMethod("stopForeground", mStopFgSign);
        } catch (NoSuchMethodException e) {
            Log.e("SipService", e + "Couldn't find startForeground or stopForeground");
        }

//		getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, ContactsManager.newInstance());

        if (displayServiceNotification()) {
            startForegroundCompat(NOTIF_ID, mNotif);
        }

        if (!mTestDelayElapsed) {
            // Only used when testing. Simulates a 5 seconds delay for launching service
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mTestDelayElapsed = true;
                }
            }, 5000);
        }

        //make sure the application will at least wakes up every 10 mn
//        Intent intent = new Intent(this, KeepAliveReceiver.class);
//        PendingIntent keepAlivePendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_ONE_SHOT);
//        AlarmManager alarmManager = ((AlarmManager) this.getSystemService(Context.ALARM_SERVICE));
//        Compatibility.scheduleAlarm(alarmManager, AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 600000, keepAlivePendingIntent);

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }
}
