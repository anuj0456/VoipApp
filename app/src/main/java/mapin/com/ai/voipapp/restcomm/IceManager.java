package mapin.com.ai.voipapp.restcomm;

import android.util.Log;

import org.ice4j.StackProperties;
import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Agent;
import org.ice4j.ice.CheckListState;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.LocalCandidate;
import org.ice4j.ice.harvest.StunCandidateHarvester;
import org.ice4j.ice.harvest.TrickleCallback;
import org.ice4j.ice.harvest.TurnCandidateHarvester;
import org.ice4j.security.LongTermCredential;
import org.ice4j.stack.PacketLogger;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import mapin.com.ai.voipapp.Constants;


public class IceManager {
    private static final String TAG = "IceManager";
    private String sdp;



    private Agent agent;
    private Logger logger;
    private Boolean started = false;

    public synchronized void doIceLookup(PropertyChangeListener stateListener, TrickleCallback candidateTrickleListener) throws Exception {

        try {
            if (agent == null) {
                agent = new Agent(Level.FINEST); // A simple ICE Agent
                agent.setTrickling(true);
            }

            agent.getStunStack().setPacketLogger(new PacketLogger() {
                @Override
                public void logPacket(byte[] sourceAddress, int sourcePort, byte[] destinationAddress, int destinationPort, byte[] packetContent, boolean sender) {
                    try {
                        Log.d(TAG, "logPacket: sourceAddress: " + InetAddress.getByAddress(sourceAddress).getHostAddress() +
                                " sourcePort: " + sourcePort + " destinationAddress:" + InetAddress.getByAddress(destinationAddress).getHostAddress() +
                                " destinationPort:" + destinationPort + " content:" +  bytesToHex(packetContent) + " sender:" + sender);
                    } catch (UnknownHostException e) {
                        Log.e(TAG, "Unable to log packet" + e);
                    }
                }

                @Override
                public boolean isEnabled() {
                    return true;
                }
            });

            /*** Disable ipv6 ***/
            System.setProperty(StackProperties.DISABLE_IPv6, "true");
            System.setProperty(StackProperties.MAX_CTRAN_RETRANSMISSIONS, "1");
            //System.setProperty(StackProperties.MAX_CTRAN_RETRANS_TIMER, "2000");
            //System.setProperty(StackProperties.FIRST_CTRAN_RETRANS_AFTER, "50");

            if (!started) {

                /*** Setup the STUN servers: ***/
//                String[] hostnames = new String[]{"stun.faktortel.com.au",
//                        "jitsi.org", "numb.viagenie.ca", "stun.ekiga.net",
//                        "stun.schlund.de", "stun.voiparound.com", "stun.voipbuster.com", "stun.voipstunt.com", "turn01.hubl.in", "turn02.hubl.in"};
                String[] hostnames;
                if(Constants.ENV_ICE_HOSTNAME != null && !Constants.ENV_ICE_HOSTNAME.equals( "")) {
                    hostnames = new String[]{Constants.ENV_ICE_HOSTNAME};
                }else{
                    hostnames = new String[]{"turn.mobilityplatform.net:443" };
                }
                // Look online for actively working public STUN Servers. You can find
                // free servers.
                // Now add these URLS as Stun Servers with standard 3478 port for STUN
                // servrs.
                for (String hostname : hostnames) {
                    try {
                        String[] hostnameSplit = null;
                        TransportAddress ta = null;
                        if (hostname.contains(":")) {
                            hostnameSplit = hostname.split(":");
                        }

                        if (hostnameSplit != null && hostnameSplit.length == 2) {
                                ta = new TransportAddress(InetAddress.getByName(hostnameSplit[0]), Integer.parseInt(hostnameSplit[1]), Transport.UDP);
                        } else if(hostname.contains("turn01")) {
                            ta = new TransportAddress(InetAddress.getByName(hostnameSplit != null ? hostnameSplit[0] : null), Integer.parseInt(hostnameSplit != null ? hostnameSplit[1] : "3478"), Transport.UDP);
                        } else if(hostname.contains("turn02")) {
                            ta = new TransportAddress(InetAddress.getByName(hostnameSplit != null ? hostnameSplit[0] : null), Integer.parseInt(hostnameSplit != null ? hostnameSplit[1] : "3478"), Transport.TCP);
                        }else {
                            // InetAddress qualifies a url to an IP Address, if you have an
                            // error here, make sure the url is reachable and correct
                            ta = new TransportAddress(InetAddress.getByName(hostname), 3478, Transport.UDP);
                        }
                        // Currently Ice4J only supports UDP and will throw an Error
                        // otherwise
//                        ta = new TransportAddress("mp-dev-proxy.mobilityplatform.net", 3478, Transport.TCP);
                        if (hostname.contains("turn"))
                        {
                            LongTermCredential lt = new LongTermCredential("turn","turn");
                            agent.addCandidateHarvester(new TurnCandidateHarvester(ta, lt));
                        }
                        else {
                            agent.addCandidateHarvester(new StunCandidateHarvester(ta));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                /*
                 * Now you have your Agent setup. The agent will now be able to know its
                 * IP Address and Port once you attempt to connect. You do need to setup
                 * Streams on the Agent to open a flow of information on a specific
                 * port.
                 */

                createLocalCandidate();

                /*The String "toSend" should be sent to a server. You need to write a PHP, Java or any server.
                 * It should be able to have this String posted to a database.
                 * Each program checks to see if another program is requesting a call.
                 * If it is, they can both post this "toSend" information and then read eachother's "toSend" SDP string.
                 * After you get this information about the remote computer do the following for ice4j to build the connection:*/


                //Hopefully now your Agent is totally setup. Now we need to start the connections:

                agent.addStateChangeListener(stateListener); // We will define this class soon
                started = true;
                agent.startCandidateTrickle(candidateTrickleListener);
            }

        } catch (Exception e)
        {
            Log.e(TAG, "Unable to start ICE!", e);
        }
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    private void createLocalCandidate() {
        IceMediaStream stream = agent.getStream("audio");
        if (stream != null)
        {
            Log.d(TAG,"IceMediaStraem already exists, don't create a new one yet!");
        }
        if (stream == null) {
            stream = agent.createMediaStream("audio");
            int port = (new Random()).nextInt((32768 - 16384) + 1) + 16384;
            ; // Choose any port between 16384 and 32768
            try {
                agent.createComponent(stream, Transport.UDP, port, port, port + 100);
                // The three last arguments are: preferredPort, minPort, maxPort
            } catch (BindException e) {
                Log.e(TAG, "createLocalCandidate: failed to create", e);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "createLocalCandidate: failed to create", e);
            } catch (IOException e) {
                Log.e(TAG, "createLocalCandidate: failed to create", e);
            }

            /*
             * Now we have our port and we have our stream to allow for information
             * to flow. The issue is that once we have all the information we need
             * each computer to get the remote computer's information. Of course how
             * do you get that information if you can't connect? There might be a
             * few ways, but the easiest with just ICE4J is to POST the information
             * to your public sever and retrieve the information. I even use a
             * simple PHP server I wrote to store and spit out information.
             */
            try {
                sdp = SdpUtils.createSDPDescription(agent);
                // Each computersends this information
                // This information describes all the possible IP addresses and
                // ports
            } catch (Throwable e) {
                Log.e(TAG, "createLocalCandidate: failed to create SDP", e);
            }
        }

    }

    public String getSdp() {
        return sdp;
    }

    public void parseRemoteSdp(String remoteReceived)
    {
        try {
            SdpUtils.parseSDP(agent, remoteReceived); // This will add the remote information to the agent.
            checkForCommunication();
        }
        catch (Exception e)
        {
            Log.e(TAG, "Unable to parse SDP contact", e);
        }
    }

    public void checkForCommunication()
    {
        // You need to listen for state change so that once connected you can then use the socket.
        agent.startConnectivityEstablishment(); // This will do all the work for you to connect
    }

    public void refreshLocalComponent() {
        createLocalCandidate();
        List<IceMediaStream> streams = agent.getStreams();
        for (IceMediaStream stream : streams)
        {
            if (stream.getCheckList().getState() == CheckListState.FAILED)
            {
                // if we have a failed state here, we should see whether our local component is fresh
                LocalCandidate localCandidate = agent.getSelectedLocalCandidate("audio");
            }
        }
    }

    public Agent getAgent() {
        return agent;
    }

    public void setAgent(Agent agent) {
        this.agent = agent;
    }

    public void setSDP(String sdp) {
        this.sdp = sdp;
    }
}
