package mapin.com.ai.voipapp;

import android.content.Context;
import android.text.TextUtils;

public class Utils {

    public static boolean isPushToTalkEnable(){
        if(isEmpty(Constants.SIP_SERVER_OUTBOUND_PROXY))
            return false;
        return true;
    }

    public static String getGroupName(Context context, String groupCode) {
        return "";
    }

    public static boolean isEmpty(String text) {
        return text == null || text.isEmpty();
    }
}
