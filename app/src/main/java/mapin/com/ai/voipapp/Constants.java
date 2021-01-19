package mapin.com.ai.voipapp;

public class Constants {

    public static final boolean EMULATOR_MODE_ENABLED = BuildConfig.EMULATOR_MODE_ENABLED;

    public static String ENV_ICE_HOSTNAME = "freeswitch_domain";
    public static String ENV_URL_BASE = "base_url";
    public static String SIP_SERVER_OUTBOUND_PROXY = "";

    public enum SOCKET_STATUS {
        CONNECTED, DISCONNECTED, UNDEFINED, CANCELED;
    }

    public enum LINPHONE_STATE {
        Start_Call, Dialing, On_Call, End_Call
    }
}
