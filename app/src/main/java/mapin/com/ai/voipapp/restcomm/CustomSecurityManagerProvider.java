package mapin.com.ai.voipapp.restcomm;

import android.gov.nist.core.CommonLogger;
import android.gov.nist.core.LogWriter;
import android.gov.nist.core.StackLogger;
import android.gov.nist.core.net.SecurityManagerProvider;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.Security;
import java.util.Enumeration;
import java.util.Properties;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

public class CustomSecurityManagerProvider implements SecurityManagerProvider {
    private static final StackLogger logger = CommonLogger.getLogger(CustomSecurityManagerProvider.class);

    private KeyManagerFactory keyManagerFactory;
    private TrustManagerFactory trustManagerFactory;

    public CustomSecurityManagerProvider() {
    }

    public void init(Properties properties)
            throws GeneralSecurityException, IOException {
        // required, could use default keyStore, but it is better practice to explicitly specify
        final String keyStoreFilename = properties.getProperty("javax.net.ssl.keyStore");
        // required
        final String keyStorePassword = properties.getProperty("javax.net.ssl.keyStorePassword");
        // optional, uses default if not specified
        String keyStoreType =  KeyStore.getDefaultType();
        if (keyStoreType == null) {
            keyStoreType = KeyStore.getDefaultType();
            logger.logWarning("Using default keystore type " + keyStoreType);
        }
        if (keyStoreFilename == null || keyStorePassword == null) {
            logger.logWarning("TLS server settings will be inactive - TLS key store will use JVM defaults"
                    + " keyStoreType=" +  keyStoreType
                    + " javax.net.ssl.keyStore=" + keyStoreFilename
                    + " javax.net.ssl.keyStorePassword=" + (keyStorePassword == null? null: "***"));
        }

        // required, could use default trustStore, but it is better practice to explicitly specify
        final String trustStoreFilename = properties.getProperty("javax.net.ssl.trustStore");
        // optional, if not specified using keyStorePassword
        String trustStorePassword = properties.getProperty("javax.net.ssl.trustStorePassword");
        if(trustStorePassword == null) {
            logger.logInfo("javax.net.ssl.trustStorePassword is null, using the password passed through javax.net.ssl.keyStorePassword");
            trustStorePassword = keyStorePassword;
        }
        // optional, uses default if not specified
        String trustStoreType =  KeyStore.getDefaultType();
        if (trustStoreType == null) {
            trustStoreType = KeyStore.getDefaultType();
            logger.logWarning("Using default truststore type " + trustStoreType);
        }
        if (trustStoreFilename == null || trustStorePassword == null) {
            logger.logWarning("TLS trust settings will be inactive - TLS trust store will use JVM defaults."
                    + " trustStoreType=" +  trustStoreType
                    + " javax.net.ssl.trustStore=" +  trustStoreFilename
                    + " javax.net.ssl.trustStorePassword=" + (trustStorePassword == null? null: "***"));
        }

        String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
        if (algorithm == null) {
            algorithm = "SunX509";
        }
        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
            logger.logDebug("SecurityManagerProvider " + this.getClass().getCanonicalName() + " will use algorithm " + algorithm);
        }

        KeyStore systemKs = KeyStore.getInstance("AndroidCAStore");
        systemKs.load(null);

        keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
        Security.addProvider(new BouncyCastleProvider());
        final KeyStore ks = KeyStore.getInstance(keyStoreType);
        if(keyStoreFilename != null) {

            if(keyStorePassword != null) {
                ks.load(new FileInputStream(new File(keyStoreFilename)), keyStorePassword.toCharArray());
            } else {
                ks.load(new FileInputStream(new File(keyStoreFilename)), null);
            }
            if (keyStorePassword != null) {
                keyManagerFactory.init(ks, keyStorePassword.toCharArray());
            } else {
                keyManagerFactory.init(ks, null);
            }
        } else {
            if (keyStorePassword != null) {
                // load an empty keystore
                ks.load(null, keyStorePassword.toCharArray());
                keyManagerFactory.init(ks, keyStorePassword.toCharArray());
            } else {
                ks.load(null, null);
                Enumeration<String> aliases = systemKs.aliases();
                // load all the system certs into our custom empty one
                while (aliases.hasMoreElements())
                {
                    String alias = aliases.nextElement();
                    ks.setCertificateEntry(alias, systemKs.getCertificate(alias));
                }
                keyManagerFactory.init(ks, null);
            }
        }

        trustManagerFactory = TrustManagerFactory.getInstance(algorithm);
        final KeyStore ts = KeyStore.getInstance(trustStoreType);
        if(trustStoreFilename != null) {

            if(trustStorePassword != null) {
                ts.load(new FileInputStream(new File(trustStoreFilename)), trustStorePassword.toCharArray());
            } else {
                ts.load(new FileInputStream(new File(trustStoreFilename)), null);
            }
            trustManagerFactory.init((KeyStore) ts);
        } else {
            if (trustStorePassword != null) {
                // load an empty keystore
                ts.load(null, trustStorePassword.toCharArray());
                trustManagerFactory.init(ts);
            } else {
                try {
                    ts.load(null, null);

                    Enumeration<String> aliases = systemKs.aliases();
                    // load all the system certs into our custom empty one
                    while (aliases.hasMoreElements())
                    {
                        String alias = aliases.nextElement();
                        ts.setCertificateEntry(alias, systemKs.getCertificate(alias));
                    }

                    trustManagerFactory.init(ts);
                } catch (Exception e)
                {
                    logger.logError("Unable to instantiate TrustManagerFactory", e);
                }
            }
        }

        String fileName = "keystore";
        File file = File.createTempFile(fileName, null, new File(System.getProperty("org.restcomm.CustomSecurityManagerProvider.cacheDir")));
        FileOutputStream fop = new FileOutputStream(file);
        ts.store(fop, null);


        // set the system properties so that OKHttp can load it
        System.setProperty("javax.net.ssl.keyStore", file.getAbsolutePath());
        System.setProperty("javax.net.ssl.trustStore", file.getAbsolutePath());
        // System.setProperty("javax.net.ssl.keyStorePassword", null );
        System.setProperty("javax.net.ssl.keyStoreType", "BKS" );

        if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
            logger.logDebug("TLS settings OK. SecurityManagerProvider " + this.getClass().getCanonicalName() + " initialized.");
        }
    }

    public KeyManager[] getKeyManagers(boolean client) {
        if(keyManagerFactory == null) return null;
        return keyManagerFactory.getKeyManagers();
    }

    public TrustManager[] getTrustManagers(boolean client) {
        if(trustManagerFactory == null) return null;
        return trustManagerFactory.getTrustManagers();
    }
}
