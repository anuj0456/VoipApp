package mapin.com.ai.voipapp.restcomm;

import android.content.Context;
import android.gov.nist.javax.sip.address.SipUri;
import android.gov.nist.javax.sip.clientauthutils.AccountManager;
import android.gov.nist.javax.sip.header.Contact;
import android.gov.nist.javax.sip.header.ContactList;
import android.gov.nist.javax.sip.header.Via;
import android.gov.nist.javax.sip.stack.NioMessageProcessorFactory;
import android.javax.sdp.MediaDescription;
import android.javax.sdp.SdpFactory;
import android.javax.sdp.SessionDescription;
import android.javax.sip.ClientTransaction;
import android.javax.sip.Dialog;
import android.javax.sip.DialogTerminatedEvent;
import android.javax.sip.IOExceptionEvent;
import android.javax.sip.InvalidArgumentException;
import android.javax.sip.ListeningPoint;
import android.javax.sip.ObjectInUseException;
import android.javax.sip.PeerUnavailableException;
import android.javax.sip.RequestEvent;
import android.javax.sip.ResponseEvent;
import android.javax.sip.ServerTransaction;
import android.javax.sip.SipException;
import android.javax.sip.SipFactory;
import android.javax.sip.SipListener;
import android.javax.sip.SipProvider;
import android.javax.sip.SipStack;
import android.javax.sip.TimeoutEvent;
import android.javax.sip.Transaction;
import android.javax.sip.TransactionTerminatedEvent;
import android.javax.sip.TransportNotSupportedException;
import android.javax.sip.address.SipURI;
import android.javax.sip.header.CallIdHeader;
import android.javax.sip.header.ContactHeader;
import android.javax.sip.header.FromHeader;
import android.javax.sip.header.HeaderAddress;
import android.javax.sip.header.ToHeader;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;
import android.media.AudioManager;
import android.net.rtp.AudioCodec;
import android.net.rtp.AudioGroup;
import android.net.rtp.AudioStream;
import android.net.rtp.RtpStream;
import android.util.Log;

import org.ice4j.TransportAddress;
import org.ice4j.ice.Agent;
import org.ice4j.ice.CandidatePair;
import org.ice4j.ice.Component;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.IceProcessingState;
import org.ice4j.ice.LocalCandidate;
import org.ice4j.ice.harvest.TrickleCallback;
import org.ice4j.socket.IceSocketWrapper;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.Collection;
import java.util.Properties;
import java.util.TooManyListenersException;
import java.util.Vector;

import mapin.com.ai.voipapp.Constants;
import mapin.com.ai.voipapp.activity.PushToTalkActivity;

public class MainSipListener implements SipListener, PropertyChangeListener, TrickleCallback {
    private SipProvider sipProvider;
    private SipHelper sipHelper;

    private static final String TAG = MainSipListener.class.getSimpleName();
    private SipStack sipStack;

    private String user, userPassword, testCallDestination, fsdomain;

    //TODO get these from Build Configs
    private String sipDomain;
    private String websocketHostname;
    private String websocketURL = "";
    private String transport;
    private int sipPort = 443;

    private AccountManager accountManager;
    private MainSipListener instance;
    private IceManager iceManager;
    private SIPRegisterThread registerThread;
    private Thread inviteThread;
    private String publicIp;
    private int publicPort;
    private String audioStreamIp;

    // SDP string to use for INVITE
    private String sdp;
    private SipUri sipURI1;
    private Boolean init = false;
    private Boolean iceStarted = false;
    private Boolean listeners = false;
    private Boolean registered = false;


    // ports and datagramsocket for non ice usage
    int localDataStreamPost = 16000;
    DatagramSocket serverSocket = null;
    String listeningIP = null;
    SipURI destinationURI;


    private Boolean useIce = true;
    private SipEventsListener sipEventsListener, defaultSipEventsListener;
    private AudioManager audioManager;
    private AudioStream audioStream;
    private AudioGroup audioGroup;
    // store the call bye request and call dialog, so we can hang up the call
    private Request callByeRequest;
    private Dialog callDialog;
    private boolean shutdown;

    public MainSipListener(Boolean useIce, String user, String password, String fsdomain, String group, SipEventsListener listener) {
        this.useIce = useIce;
        this.user = user;
        this.userPassword = password;
        this.testCallDestination = group;
        this.sipEventsListener = listener;
        this.fsdomain = fsdomain;
    }

    public MainSipListener(Boolean useIce, String user, String password, String fsdomain) {
        this.useIce = useIce;
        this.user = user;
        this.userPassword = password;
        this.fsdomain = fsdomain;
    }

    public void setGroup(String group) {
        this.testCallDestination = group;
    }

    public String getGroup() {
        return testCallDestination;
    }

    public void removeListener() {
        this.sipEventsListener = null;
    }

    public void attachListener(SipEventsListener listener) {
        this.sipEventsListener = listener;
    }

    public void  init() {
        String env_url = Constants.ENV_URL_BASE;
        sipDomain = fsdomain;
        websocketHostname = getJustdomain(env_url);
        transport = getTransport(env_url);
        Properties properties = new Properties();

        // These properties are final
        properties.setProperty("android.javax.sip.STACK_NAME", "im.dlg.sip");

        /** Don't try and determine local address, just use 0.0.0.0
         InetAddress defaultInetAddress = null;
         try {
         Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
         while (interfaces.hasMoreElements() && defaultInetAddress == null)
         {
         NetworkInterface ni = interfaces.nextElement();
         if (ni.isUp())
         {
         if (ni.getName().startsWith("lo") || ni.getName().startsWith("p2p"))
         {
         continue;
         }
         Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
         while (inetAddresses.hasMoreElements() && defaultInetAddress == null)
         {
         InetAddress inetAddress = inetAddresses.nextElement();
         if (inetAddress instanceof Inet6Address)
         {
         continue;
         }
         defaultInetAddress = inetAddress;
         }
         }
         }
         } catch (SocketException e) {
         e.printStackTrace();
         }
         properties.setProperty("android.javax.sip.IP_ADDRESS", defaultInetAddress.getHostAddress());
         **/

        properties.setProperty("android.javax.sip.IP_ADDRESS", "0.0.0.0");

        //properties.setProperty("android.javax.sip.AUTOMATIC_DIALOG_SUPPORT", "on");
        properties.setProperty("android.gov.nist.javax.sip.DELIVER_RETRANSMITTED_ACK_TO_LISTENER", "true");
        properties.setProperty("android.gov.nist.javax.sip.REENTRANT_LISTENER", "false");
        properties.setProperty("android.gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY", NioMessageProcessorFactory.class.getCanonicalName());
        properties.setProperty("android.gov.nist.javax.sip.TRACE_LEVEL", "32");
        properties.setProperty("android.gov.nist.javax.sip.STACK_LOGGER", SipStackLogger.class.getName());
        properties.setProperty("android.gov.nist.javax.sip.RELIABLE_CONNECTION_KEEP_ALIVE_TIMEOUT", "10");
        if (transport.equals("wss")) {
            properties.setProperty("android.gov.nist.javax.sip.USE_TLS_GATEWAY", "true");
        } else {
            properties.setProperty("android.gov.nist.javax.sip.USE_TLS_GATEWAY", "false");
        }
        properties.setProperty("android.javax.sip.LOG_MESSAGE_CONTENT", "true");
        //properties.setProperty("android.gov.nist.javax.sip.STUN_SERVER","stun.faktortel.com.au:3478");

        //properties.setProperty("android.gov.nist.javax.sip.TCP_POST_PARSING_THREAD_POOL_SIZE", "1");
        properties.setProperty("android.javax.sip.AUTOMATIC_DIALOG_SUPPORT", "on");
        properties.setProperty("android.gov.nist.javax.sip.AUTOMATIC_DIALOG_ERROR_HANDLING", "false");
        properties.setProperty("android.gov.nist.javax.sip.EARLY_DIALOG_TIMEOUT_SECONDS", "360");
        properties.setProperty("android.gov.nist.javax.sip.READ_TIMEOUT", "1000");
        properties.setProperty("android.gov.nist.javax.sip.SSL_HANDSHAKE_TIMEOUT", "1000");
        properties.setProperty("android.gov.nist.javax.sip.TLS_CLIENT_AUTH_TYPE", "DisabledAll");
        properties.setProperty("android.gov.nist.javax.sip.DIALOG_TIMEOUT_FACTOR", "64");
        properties.setProperty("android.gov.nist.javax.sip.TLS_CLIENT_PROTOCOLS", "TLSv1.2");
        properties.setProperty("android.gov.nist.javax.sip.SECURITY_MANAGER_PROVIDER", CustomSecurityManagerProvider.class.getCanonicalName());

        SipFactory factory = SipFactory.getInstance();
        factory.setPathName("android.gov.nist");

        try {
            sipStack = factory.createSipStack(properties);
        } catch (Exception e) {
            Log.e(TAG, "Unable to create sipStack", e);
            if (sipEventsListener != null) {
                sipEventsListener.onCallFailed();
            }
        }

        if (useIce) {
            iceManager = new IceManager();
            startupIceDiscovery();
        }

        init = true;
    }

    private String getJustdomain(String url) {
        if (url.contains("/")) {
            url = url.substring(url.lastIndexOf("/") + 1);
        }
        return url;
    }

    private String getTransport(String url) {
        String transport = "ws";
        if (url.contains("https")) {
            transport = "wss";
        }
        return transport;
    }

    public void setupListeners()
    {
        //ListeningPoint listeningPoint = sipStack.createListeningPoint("127.0.0.1", 14000, transport);
        ListeningPoint listeningPoint = null;
        try {
            listeningPoint = sipStack.createListeningPoint(sipStack.getIPAddress(), 14000, transport);
        } catch (TransportNotSupportedException e) {
            Log.e(TAG, "Unable to create listeningPoint", e);
        } catch (InvalidArgumentException e) {
            Log.e(TAG, "Unable to create listeningPoint", e);
        }
        try {
            if (sipStack != null && sipStack.getSipProviders() != null && sipStack.getSipProviders().hasNext())
            {
                while (sipStack.getSipProviders().hasNext())
                {
                    sipProvider = (SipProvider) sipStack.getSipProviders().next();
                    break;
                }
            } else {
                sipProvider = sipStack.createSipProvider(listeningPoint);
            }
        } catch (ObjectInUseException e) {
            Log.e(TAG, "Unable to create sipProvider", e);
        }
        try {
            sipProvider.addSipListener(this);
        } catch (TooManyListenersException e) {
            Log.e(TAG, "Unable to add sipListener to sipProvider", e);
        }
        try {
            sipHelper = new SipHelper(sipStack, sipProvider);
        } catch (PeerUnavailableException e) {
            Log.e(TAG, "Unable to startup sipHelper", e);
        }
        accountManager = new SipAccountManager(user,userPassword,sipDomain);
        listeners = true;
    }

    public void startupIceDiscovery()
    {
        if (!iceStarted) {
            instance = this;
            Thread iceThread = new Thread() {
                @Override
                public void run() {
                    // retry loop to start harvester
                    for (int i = 0; i < 10; i++) {
                        try {
                            if (!iceStarted) {
                                if (iceManager == null) {
                                    iceManager = new IceManager();
                                }
                                try {
                                    iceManager.doIceLookup(instance, instance);
                                    break;
                                } catch (Throwable e) {
                                    Log.e(TAG, "Failed to lookup ICE", e);
                                }
                            }

                        } catch (Throwable e) {
                            Log.e(TAG, "Failed to instantiate ICE", e);
                        }
                        // sleep for 500 ms and try again (maximum 10 times)
                        try {
                            sleep(500);
                        } catch (InterruptedException e) {
                            Log.e(TAG, "Failed to sleep", e);
                        }
                    }
                }
            };
            iceThread.start();
            try {
                iceThread.join();
            } catch (InterruptedException ie)
            {
                Log.e(TAG, "Unable to join ice thread", ie);
                iceStarted = false;
                if (sipEventsListener != null) {
                    sipEventsListener.onCallFailed();
                }
            }
        }
        iceStarted = true;
    }

    public void stopIce(){
        if(iceManager != null){
            try {
                iceStarted = false;
                if (sipEventsListener != null) {
                    sipEventsListener.onDisconnected();
                }
                iceManager = null;
            } catch (Throwable e) {
                Log.e(TAG, "Failed to lookup ICE", e);
            }
        }
    }

    public void register() {
        try {
            sipURI1 = new SipUri();
            sipURI1.setUser(user);
            sipURI1.setUserPassword(userPassword);
            sipURI1.setHost(sipDomain);
            sipURI1.setPort(sipPort);
            sipURI1.setTransportParam(transport);
            sipURI1.setMethodParam("GET");
            sipURI1.setHeader("Host", websocketHostname);
            sipURI1.setHeader("Location", websocketURL);
            registerThread = new SIPRegisterThread(sipProvider, sipHelper, sipURI1);
            registerThread.start();
        }
        catch (ParseException e)
        {
            Log.e(TAG,"Unable to parse SipURI", e);
        }
        registered = true;
    }

    private void startTestCall() {
        if (!useIce) {
            try {
                Log.d(TAG,"Creating the datagram socket on port " + localDataStreamPost + "...");
                serverSocket = new DatagramSocket(null);
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(localDataStreamPost));
            } catch (Exception e) {
                Log.e(TAG,"Unable to open UDP listening socket", e);
            }
        }

        inviteThread = new Thread() {
            @Override
            public void run() {
                try {
                    if (destinationURI == null) {
                        destinationURI = new SipUri();
                        destinationURI.setUser(testCallDestination);
                        destinationURI.setHost(sipDomain);
                        destinationURI.setPort(sipPort);
                        destinationURI.setTransportParam(transport);
                        destinationURI.setMethodParam("GET");
                        destinationURI.setHeader("Host", websocketHostname);
                        destinationURI.setHeader("Location", websocketURL);
                        try {
                            String sdpData = null;
                            if (iceManager.getSdp() != null) {
                                iceManager.refreshLocalComponent();
                                sdpData = iceManager.getSdp();
                                sdp = iceManager.getSdp();
                            } else {
                                sdpData = "v=0\r\n" +
                                        "o=- 13760799956958020 13760799956958020" + " IN IP4 " + sipHelper.getListeningPoint().getIPAddress() + "\r\n" +
                                        //"s=mysession session\r\n" +
                                        "s=-\r\n";
                                //"p=+46 8 52018010\r\n" +
                                if (listeningIP != null) {
                                    sdpData += "c=IN IP4 " + listeningIP + "\r\n";
                                } else {
                                    sdpData += "c=IN IP4 " + sipHelper.getListeningPoint().getIPAddress() + "\r\n";
                                }
                                sdpData += "t=0 0\r\n" +
                                        "m=audio " + localDataStreamPost + " RTP/AVP 0\r\n" +
                                        //"m=audio " + port + " RTP/AVP 0 4 18\r\n" +
                                        "a=rtpmap:0 PCMU/8000\r\n" +
                                        //"a=rtpmap:4 G723/8000\r\n" +
                                        //"a=rtpmap:18 G729A/8000\r\n" +
                                        "a=ptime:20\r\n";
                                sdp = sdpData;
                            }

                            CallIdHeader callIdHeader = sipProvider.getNewCallId();
                            sipHelper.sendInvite(sipURI1, destinationURI, sdpData, "tag", null, null, callIdHeader);
                            if (sipEventsListener != null) {
                                sipEventsListener.onOutgoing();
                            }
                        } catch (SipException e) {
                            Log.e(TAG, "Unable to connect", e);
                            if (sipEventsListener != null) {
                                sipEventsListener.onCallFailed();
                            }
                        }
                    }

                } catch(ParseException e){
                    Log.e(TAG, "Unable to parse", e);
                }
            }
        };

        synchronized (inviteThread){
            inviteThread.start();
        }
    }

    public void start(boolean startCall) {
        if (!init)
        {
            init();
        }
        if (!listeners)
        {
            setupListeners();
        }
        if (!registered)
        {
            register();
        }
        if (startCall && getGroup() != null)
        {
            startTestCall();
        }
    }

    public void stop()
    {
        // make sure the register Thread gets shut down.
        shutdown = true;
        try {
            if (sipProvider != null && sipProvider.getSipStack() != null) {
                sipProvider.getSipStack().stop();
            }
            if (sipStack != null)
            {
                sipStack.stop();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        init = false;
        //stopIce();
    }

    @Override
    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        ServerTransaction serverTransactionId = requestEvent
                .getServerTransaction();

        System.out.println("\n\nRequest " + request.getMethod()
                + " received at " + sipStack.getStackName()
                + " with server transaction id " + serverTransactionId);

        if (request.getMethod().equals(Request.INVITE)) {
            processInvite(requestEvent, serverTransactionId);
        } else if (request.getMethod().equals(Request.ACK)) {
            processAck(requestEvent, serverTransactionId);
        } else if (request.getMethod().equals(Request.CANCEL)) {
            processCancel(requestEvent, serverTransactionId);
        } else if (request.getMethod().equals(Request.OPTIONS)) {
            processOptions(requestEvent, serverTransactionId);
        } else if (request.getMethod().equals(Request.REGISTER)) {
            processRegister(requestEvent, serverTransactionId);
        } else {
            processInDialogRequest(requestEvent, serverTransactionId);
        }
    }

    private void processOptions(RequestEvent requestEvent, ServerTransaction serverTransactionId) {
        Request request = requestEvent.getRequest();
        HeaderAddress contact = (ContactHeader) request.getHeader(ContactHeader.NAME);
        if (contact == null)
        {
            contact = (ToHeader) request.getHeader(ToHeader.NAME);
        }
        SipURI contactUri = (SipURI) contact.getAddress().getURI();
        FromHeader from = (FromHeader) request.getHeader(FromHeader.NAME);
        SipURI fromUri = (SipURI) from.getAddress().getURI();
        try {
            Response response = sipHelper.getMessageFactory().createResponse(200, request);
            // if we have SDP data in our iceManager, we should use it in our OPTIONS ACK
            if (iceManager != null && iceManager.getSdp() != null)
            {
                sdp = iceManager.getSdp();
            }
            // only set the SDP content if we have it, otherwise have no content
            if (sdp != null) {
                response.setContent(sdp,
                        sipHelper.getHeaderFactory().createContentTypeHeader(
                                "application", "sdp"));
            }
            if (serverTransactionId == null)
            {
                serverTransactionId = sipHelper.getServerTransaction(requestEvent);
            }
            serverTransactionId.sendResponse(response);
        } catch (Exception e) {
            Log.e(TAG,"Unable to send back OPTIONS ACK", e);
        }

    }

    public void setupCallAudio(String localIP, String remoteIP, int remotePort) throws UnknownHostException {
        audioManager = (AudioManager) PushToTalkActivity.getMyApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        //audioManager.setMode(AudioManager.MODE_NORMAL);

        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL), AudioManager.FLAG_PLAY_SOUND);

        audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN);

        audioManager.setSpeakerphoneOn(true);
        audioManager.setMicrophoneMute(true);       /***Default is off*/

        publicIp = remoteIP;
        publicPort = remotePort;
        audioStreamIp = localIP;

        audioGroup = new AudioGroup();
        audioGroup.setMode(AudioGroup.MODE_ECHO_SUPPRESSION);

        audioStream = null;
        try {
            audioStream = new AudioStream(InetAddress.getByName(localIP));
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        audioStream.setCodec(AudioCodec.PCMU);
        audioStream.setMode(RtpStream.MODE_NORMAL);
        audioStream.associate(InetAddress.getByName(remoteIP), remotePort);
        audioStream.join(audioGroup);

        audioManager.setSpeakerphoneOn(true);

        /**Try to reconnect multiple times. If you are in "call state", and the audio stream stops, then attempt to reconnect**/
        for (int i = 0; i < 5; i++) {
            checkforRetry("0.0.0.0");
        }
    }

    private void checkforRetry(String localIP) {
        if (audioStream == null || !audioStream.isBusy()) {
            try {
                audioStream = new AudioStream(InetAddress.getByName(localIP));
            } catch (SocketException e) {
                e.printStackTrace();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }
    }

    public void micMuted(boolean mute) {
        if (audioManager != null) {
        }else{
            if (publicIp != null && publicPort != 0) {
                try {
                    setupCallAudio(audioStreamIp, publicIp, publicPort);
                }catch (Exception e){
                    e.printStackTrace();
                }

            }
        }
        audioManager.setMicrophoneMute(mute);
    }

    public Boolean isSipActive(){
        return init;
    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {
        ClientTransaction ct = responseEvent.getClientTransaction();
        ServerTransaction st = null;
        Response response = responseEvent.getResponse();
        if (ct != null) {
            Object appData = ct.getApplicationData();

            if (appData != null) {
                st = (ServerTransaction) appData;
            }
        }
        try {
            if (response.getStatusCode() == 401 || response.getStatusCode() == 407)
            {
                sipHelper.handleChallenge(responseEvent, accountManager);
            }
            else
            {
                Log.d(TAG,"Received response from server: " + response.getStatusCode());
            }
            if(response.getStatusCode() == 200 && ct != null && ct.getRequest() != null && ct.getRequest().getMethod().equals("REGISTER"))
            {
                // check our received line, and determine what the server thinks our remote address is (ie, 127.0.0.1 and 0.0.0.0 are not really acceptable).
                Object viaHeaderObject = response.getHeader("Via");
                String received = null;
                if (viaHeaderObject instanceof Via)
                {
                    Via viaHeader = (Via) viaHeaderObject;
                    received = viaHeader.getReceived();
                }

                if (received != null) {
                    // we need to fake the UDP connection address, using the "received" header and replace with the public IP address
                    // this is a hack, and may not work if the server is behind NAT

                    InetAddress addr = InetAddress.getByName(sipHelper.getListeningPoint().getIPAddress());
                    String host = addr.getHostAddress();
                    if (host != null && (host.equals("0.0.0.0") || host.equals("127.0.0.1"))) {
                        listeningIP = received;
                    }
                }
            }
            else if(response.getStatusCode() == 200 && ct != null && ct.getRequest() != null && ct.getRequest().getMethod().equals("INVITE")) {
                Dialog dialog = ct.getDialog();
                if (dialog != null && dialog.getApplicationData() != null)
                {
                    dialog = (Dialog) dialog.getApplicationData();
                }
                // need to modify the Contact header so that the udp or tcp in the contact is corrected and the hostname set properly
                Object contactObject = responseEvent.getResponse().getHeader("contact");
                if (contactObject instanceof String)
                {
                    String contact = (String) contactObject;
                }
                else if (contactObject instanceof Contact)
                {
                    Contact contact = (Contact) contactObject;
                    SipURI contactURI = (SipURI) contact.getAddress().getURI();
                    contactURI.setTransportParam(transport);
                    contactURI.setHost(sipDomain);
                    contact.getAddress().setURI(contactURI);
                }
                else if (contactObject instanceof ContactList)
                {
                    ContactList contactList = (ContactList) contactObject;
                    for (Contact contact : contactList)
                    {
                        SipURI contactURI = (SipURI) contact.getAddress().getURI();
                        contactURI.setTransportParam(transport);
                        contactURI.setHost(sipDomain);
                    }
                }

                String sdp = new String(response.getRawContent(), "UTF-8");
                Object viaHeaderObject = response.getHeader("Via");
                String received = null;
                if (viaHeaderObject instanceof Via)
                {
                    Via viaHeader = (Via) viaHeaderObject;
                    received = viaHeader.getReceived();
                }
                // we need to fake the UDP connection address, using the "received" header and replace with the public IP address
                // this is a hack, and may not work if the server is behind NAT

                InetAddress addr = InetAddress.getByName(sipDomain);
                String host = addr.getHostAddress();

                String moddedSdp = sdp.replace(received, host);

                if (useIce)
                {
                    iceManager.parseRemoteSdp(moddedSdp);
                }
                else
                {
                    SdpFactory factory = SdpFactory.getInstance();
                    SessionDescription sdess = factory.createSessionDescription(moddedSdp);

                    int remotePort = -1;

                    Vector<MediaDescription> mediaDescs = sdess.getMediaDescriptions(false);
                    for (MediaDescription media: mediaDescs)
                    {
                        if ("audio".equals(media.getMedia().getMediaType()))
                        {
                            remotePort = media.getMedia().getMediaPort();
                        }
                    }

                    setupCallAudio(sipHelper.getListeningPoint().getIPAddress(), sdess.getConnection().getAddress(), remotePort);
                    if (sipEventsListener != null) {
                        sipEventsListener.onConnected();
                    }
                    Log.d(TAG, "Parsed remote sdp: " + sdess.toString());
                }

                // hang up any existing call that hasn't been hung up yet
                if (callByeRequest != null && callDialog != null)
                {
                    sipHelper.sendRequest(callDialog, callByeRequest);
                }
                // store the request and dialog for the call, so we can send BYE when we hang up
                callByeRequest = sipHelper.generateByeRequest(dialog);
                callDialog = dialog;
                sipHelper.sendInviteAck(responseEvent, dialog);
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * Process the ACK request, forward it to the other leg.
     */
    public void processAck(RequestEvent requestEvent,
                           ServerTransaction serverTransaction) {
        try {
            Dialog dialog = serverTransaction.getDialog();
            System.out.println("b2bua: got an ACK! ");
            System.out.println("Dialog State = " + dialog.getState());
            Dialog otherDialog = (Dialog) dialog.getApplicationData();
            Request request = otherDialog.createAck(otherDialog.getLocalSeqNumber());
            otherDialog.sendAck(request);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    /**
     * Process the invite request.
     */
    public void processInvite(RequestEvent requestEvent,
                              ServerTransaction serverTransaction) {
        SipProvider sipProvider = (SipProvider) requestEvent.getSource();
        Request request = requestEvent.getRequest();
        try {
            System.out.println("b2bua: got an Invite sending Trying");
            ServerTransaction st = requestEvent.getServerTransaction();
            if(st == null) {
                st = sipProvider.getNewServerTransaction(request);
            }
            Dialog dialog = st.getDialog();

            ToHeader to = (ToHeader) request.getHeader(ToHeader.NAME);
            SipURI toUri = (SipURI) to.getAddress().getURI();

            //SipURI target = registrar.get(toUri.getUser());

            //if(target == null) {
            //    System.out.println("User " + toUri + " is not registered.");
            //    throw new RuntimeException("User not registered " + toUri);
            //} else {
            //    ClientTransaction otherLeg = call(target);
            //    otherLeg.setApplicationData(st);
            //    st.setApplicationData(otherLeg);
            //    dialog.setApplicationData(otherLeg.getDialog());
            //    otherLeg.getDialog().setApplicationData(dialog);
            //}

        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(0);
        }
    }

    /**
     * Process the any in dialog request - MESSAGE, BYE, INFO, UPDATE.
     */
    public void processInDialogRequest(RequestEvent requestEvent,
                                       ServerTransaction serverTransactionId) {
        SipProvider sipProvider = (SipProvider) requestEvent.getSource();
        Request request = requestEvent.getRequest();
        Dialog dialog = requestEvent.getDialog();
        if (dialog != null) {
            System.out.println("local party = " + dialog.getLocalParty());
            try {
                System.out.println("b2bua:  got a bye sending OK.");
                //Response response = messageFactory.createResponse(200, request);
                //serverTransactionId.sendResponse(response);
                System.out.println("Dialog State is "
                        + serverTransactionId.getDialog().getState());

                Dialog otherLeg = (Dialog) dialog.getApplicationData();
                if (otherLeg != null) {
                    Request otherBye = otherLeg.createRequest(request.getMethod());
                    ClientTransaction clientTransaction = sipProvider.getNewClientTransaction(otherBye);
                    clientTransaction.setApplicationData(serverTransactionId);
                    serverTransactionId.setApplicationData(clientTransaction);
                    otherLeg.sendRequest(clientTransaction);
                } else {
                    // we don't have any "other leg", so just send back a 200
                    Response response = sipHelper.getMessageFactory().createResponse(200, request);
                    serverTransactionId.sendResponse(response);
                }

            } catch (Exception ex) {
                ex.printStackTrace();
                System.exit(0);
            }
        }
    }
    public boolean checkListener(){
        return sipEventsListener != null;
    }

    public void processRegister(RequestEvent requestEvent,
                                ServerTransaction serverTransactionId) {
        Request request = requestEvent.getRequest();
        ContactHeader contact = (ContactHeader) request.getHeader(ContactHeader.NAME);
        SipURI contactUri = (SipURI) contact.getAddress().getURI();
        FromHeader from = (FromHeader) request.getHeader(FromHeader.NAME);
        SipURI fromUri = (SipURI) from.getAddress().getURI();
        //registrar.put(fromUri.getUser(), contactUri);
        try {
            //Response response = this.messageFactory.createResponse(200, request);
            ServerTransaction serverTransaction = sipProvider.getNewServerTransaction(request);
            //serverTransaction.sendResponse(response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void processCancel(RequestEvent requestEvent,
                              ServerTransaction serverTransactionId) {
        Log.d(TAG,"processCancel...");

    }


    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
        Transaction transaction;
        if (timeoutEvent.isServerTransaction()) {
            transaction = timeoutEvent.getServerTransaction();
        } else {
            transaction = timeoutEvent.getClientTransaction();
        }
        Log.d(TAG,"state = " + transaction.getState());
        Log.d(TAG,"dialog = " + transaction.getDialog());
        if (transaction.getDialog() != null) {
            Log.d(TAG, "dialogState = "
                    + transaction.getDialog().getState());
        }
        Log.d(TAG,"Transaction Time out");
    }

    @Override
    public void processIOException(IOExceptionEvent ioExceptionEvent) {
        Log.d(TAG, "processIOException:" + ioExceptionEvent.toString());
        stop();
        start(true);
    }

    public void processTransactionTerminated(
            TransactionTerminatedEvent transactionTerminatedEvent) {
        if (transactionTerminatedEvent.isServerTransaction())
            Log.d(TAG,"Transaction terminated event recieved"
                    + transactionTerminatedEvent.getServerTransaction());
        else
            Log.d(TAG,"Transaction terminated "
                    + transactionTerminatedEvent.getClientTransaction());

    }

    public void processDialogTerminated(
            DialogTerminatedEvent dialogTerminatedEvent) {
        Log.d(TAG,"Dialog terminated event recieved");
        Dialog d = dialogTerminatedEvent.getDialog();
        Log.d(TAG,"Local Party = " + d.getLocalParty());

    }

    @Override
    public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
        Log.d(TAG, "Received property change:" + propertyChangeEvent.toString());
        this.sdp = iceManager.getSdp();

        if(propertyChangeEvent.getSource() instanceof Agent){
            Agent agent = (Agent) propertyChangeEvent.getSource();
            Log.d(TAG, "ICE Agent State:" + agent.getState());
            if(agent.getState().equals(IceProcessingState.TERMINATED)) {
                // Your agent is connected. Terminated means ready to communicate
                for (IceMediaStream stream: agent.getStreams()) {
                    if (stream.getName().contains("audio")) {
                        Component rtpComponent = stream.getComponent(org.ice4j.ice.Component.RTP);
                        CandidatePair rtpPair = rtpComponent.getSelectedPair();
                        // We use IceSocketWrapper, but you can just use the UDP socket
                        // The advantage is that you can change the protocol from UDP to TCP easily
                        // Currently only UDP exists so you might not need to use the wrapper.
                        IceSocketWrapper wrapper  = rtpPair.getIceSocketWrapper();
                        // Get information about remote address for packet settings
                        TransportAddress ta = rtpPair.getRemoteCandidate().getTransportAddress();
                        InetAddress hostname = ta.getAddress();
                        int port = ta.getPort();
                        Log.d(TAG, "Remote RTP is at " + hostname.toString() + " on port " + port);
                        TransportAddress localTa = rtpPair.getLocalCandidate().getTransportAddress();
                        Log.d(TAG, "Local RTP is at " + localTa.getHostAddress() + " on port " + localTa.getPort());
                        try {
                            setupCallAudio(sipHelper.getListeningPoint().getIPAddress(), hostname.getHostAddress(),port);
                            if (sipEventsListener != null) {
                                sipEventsListener.onConnected();
                            }
                        } catch (UnknownHostException e) {
                            Log.e(TAG,"Failed to establish Audio Channel!", e);
                        } catch (SipException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

    }

    public void hangUpCall()
    {

        // stop the audio stream and hung up call
        try {
            if(inviteThread != null) {
                inviteThread.join();
            }
            if (audioStream != null)
            {
                audioStream = null;
                audioGroup = null;
                audioManager = null;
            }
            if (callDialog != null && callByeRequest != null)
            {
                try {
                    sipHelper.sendRequest(callDialog, callByeRequest);
                }
                catch (Exception e)
                {
                    Log.d(TAG,"Failed to send BYE, must be already closed [ " + e.getMessage() + " ]");
                }

                sipEventsListener.onDisconnected();
            }
            callDialog = null;
            callByeRequest = null;
            inviteThread = null;
            //sipHelper = null;
            //sipStack = null;
            destinationURI = null;

            //stop();
            //stopIce();

            if(iceManager == null){
                iceManager = new IceManager();
            }
            if (!init)
                init();
            startupIceDiscovery();
            //setupListeners();
        }
        catch (Exception e)
        {
            Log.e(TAG, "Unable to hang up call", e);
        }
    }

    @Override
    public void onIceCandidates(Collection<LocalCandidate> iceCandidates) {
        Log.d(TAG, "Received IceCandidate:" + (iceCandidates!=null?iceCandidates.size():"---"));
        Log.d(TAG, "ICE Agent State:" + iceManager.getAgent().getState());
        if (iceCandidates != null && iceCandidates.size() > 0) {
            try {
                sdp = SdpUtils.createSDPDescription(iceManager.getAgent());
                // fix up our reference in IceManager too
                iceManager.setSDP(sdp);
                Log.d(TAG, "ICE current SDP:" + sdp);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
    }

    private class SIPRegisterThread extends Thread {

        private final SipProvider sipProvider;
        private final SipHelper sipHelper;
        private final SipURI localProfile;

        private SIPRegisterThread(SipProvider sipProvider, SipHelper sipHelper, SipURI localProfile) {
            this.sipProvider = sipProvider;
            this.sipHelper = sipHelper;
            this.localProfile = localProfile;
        }

        @Override
        public void run() {
            CallIdHeader callIdHeader = sipProvider.getNewCallId();

            // uncomment this and it starts working
//            try {
//                Thread.sleep((long) (Math.random() * 10000));
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }

            try {
                ClientTransaction mClientTransaction = sipHelper.sendRegister(
                        localProfile, String.valueOf(Math.random() * 0x100000000L),
                        3600, callIdHeader);
            } catch (SipException e) {
                e.printStackTrace();
            }

            while (!shutdown)
            {
                try
                {
                    Thread.sleep(30000);
                    ClientTransaction mClientTransaction = sipHelper.sendOptions(
                            localProfile, localProfile, String.valueOf(Math.random() * 0x100000000L), callIdHeader);
                }
                catch (Exception e)
                {
                    Log.e(TAG,"Failure to send OPTIONS ping", e);
                }
            }
        }
    }

    public Boolean getUseIce() {
        return useIce;
    }

    public void setUseIce(Boolean useIce) {
        this.useIce = useIce;
    }

    public void shutdown()
    {
       shutdown = true;
    }
}

