package mapin.com.ai.voipapp.restcomm;

import android.gov.nist.core.StackLogger;
import android.util.Log;

import java.util.Properties;

public class SipStackLogger implements StackLogger {

    private static final String TAG = "SipStackLogger";
    @Override
    public void logStackTrace() {
        String stackTrace = Log.getStackTraceString(new Throwable());
        Log.e(TAG,"logStackTrace:" + stackTrace);
    }

    @Override
    public void logStackTrace(int traceLevel) {
        String stackTrace = Log.getStackTraceString(new Throwable());
        Log.e(TAG,"logStackTrace:" + stackTrace +" tracelevel: " + traceLevel);
    }

    @Override
    public int getLineCount() {
        return 0;
    }

    @Override
    public void logException(Throwable ex) {
        Log.e(TAG, "Error", ex);
    }

    @Override
    public void logDebug(String message) {
        Log.d(TAG, message);
    }

    //@Override
    public void logDebug(String message, Exception ex) {
        Log.d(TAG, message, ex);
    }


    @Override
    public void logTrace(String message) {
        Log.d(TAG, message);
    }

    @Override
    public void logFatalError(String message) {
        Log.e(TAG, message);
    }

    @Override
    public void logError(String message) {
        Log.e(TAG, message);
    }

    @Override
    public boolean isLoggingEnabled() {
        return true;
    }

    @Override
    public boolean isLoggingEnabled(int logLevel) {
        return true;
    }

    @Override
    public void logError(String message, Exception ex) {
        Log.e(TAG, message,ex);
    }

    @Override
    public void logWarning(String message) {
        Log.w(TAG, message);
    }

    @Override
    public void logInfo(String message) {
        Log.i(TAG, message);
    }

    @Override
    public void disableLogging() {
        Log.d(TAG, "disableLogging...");
    }

    @Override
    public void enableLogging() {
        Log.d(TAG, "enableLogging...");
    }

    @Override
    public void setBuildTimeStamp(String buildTimeStamp) {
        Log.d(TAG, "setBuildTimeStamp...");
    }

    @Override
    public void setStackProperties(Properties stackProperties) {
        Log.d(TAG, "setStackProperties...");
    }

    @Override
    public String getLoggerName() {
        return SipStackLogger.class.getName();
    }
}
