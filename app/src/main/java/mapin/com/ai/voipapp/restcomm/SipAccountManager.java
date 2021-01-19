package mapin.com.ai.voipapp.restcomm;

import android.gov.nist.javax.sip.clientauthutils.AccountManager;
import android.gov.nist.javax.sip.clientauthutils.UserCredentials;
import android.javax.sip.ClientTransaction;

public class SipAccountManager implements AccountManager {
    private String user;
    private String userPassword;
    private String sipDomain;

    public SipAccountManager(String user, String userPassword, String sipDomain) {
        this.user = user;
        this.userPassword = userPassword;
        this.sipDomain = sipDomain;
    }

    @Override
    public UserCredentials getCredentials(ClientTransaction challengedTransaction, String realm) {
        UserCredentials uc = new UserCredentials() {
            @Override
            public String getUserName() {
                return user;
            }

            @Override
            public String getPassword() {
                return userPassword;
            }

            @Override
            public String getSipDomain() {
                return sipDomain;
            }
        };
        return uc;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getUserPassword() {
        return userPassword;
    }

    public void setUserPassword(String userPassword) {
        this.userPassword = userPassword;
    }

    public String getSipDomain() {
        return sipDomain;
    }

    public void setSipDomain(String sipDomain) {
        this.sipDomain = sipDomain;
    }
}
