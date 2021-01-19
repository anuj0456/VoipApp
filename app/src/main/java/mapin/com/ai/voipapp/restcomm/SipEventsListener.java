package mapin.com.ai.voipapp.restcomm;

public interface SipEventsListener {

    /** When outgoing call is preparing **/
    void onOutgoing();

    /** When outgoing call is connected **/
    void onConnected();

    /** When outgoing call is disconnected/ended **/
    void onDisconnected();

    /** When outgoing call has failed **/
    void onCallFailed();
}
