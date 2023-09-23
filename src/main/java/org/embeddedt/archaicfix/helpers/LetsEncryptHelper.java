package org.embeddedt.archaicfix.helpers;

import org.embeddedt.archaicfix.ArchaicLogger;
import org.embeddedt.archaicfix.config.ArchaicConfig;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

/**
 * Add Let's Encrypt root certificates to the default SSLContext.
 * CurseForge launcher uses the vanilla JDK for 1.7.10, which is version 8u51.
 * This version does not include these certificates, support for ISRG Root X1 was added in 8u141.
 * Based on <a href="https://github.com/Cloudhunter/LetsEncryptCraft/blob/2471391f7d081a8b7faed9e22051cab6352966fe/src/main/java/uk/co/cloudhunter/letsencryptcraft/LetsEncryptAdder.java">LetsEncryptCraft</a> by Cloudhunter (MIT)
 */
public class LetsEncryptHelper {
    private LetsEncryptHelper() {}
    @SuppressWarnings("java:S6437")
    public static void replaceSSLContext() {
        if (!ArchaicConfig.enableLetsEncryptRoot) {
            return;
        }

        try (InputStream x1 = LetsEncryptHelper.class.getResourceAsStream("/letsencrypt/isrgrootx1.pem");
             InputStream x2 = LetsEncryptHelper.class.getResourceAsStream("/letsencrypt/isrg-root-x2.pem")) {
            KeyStore merged = KeyStore.getInstance(KeyStore.getDefaultType());
            Path cacerts = Paths.get(System.getProperty("java.home"),"lib", "security", "cacerts");
            merged.load(Files.newInputStream(cacerts), "changeit".toCharArray());

            CertificateFactory cf = CertificateFactory.getInstance("X.509");

            Certificate cx1 = cf.generateCertificate(x1);
            merged.setCertificateEntry("archaicfix-isrgx1", cx1);

            Certificate cx2 = cf.generateCertificate(x2);
            merged.setCertificateEntry("archaicfix-isrgx2", cx2);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(merged);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);
            SSLContext.setDefault(sslContext);
            ArchaicLogger.LOGGER.info("[LetsEncryptHelper] Added certificates to trust store.");
        } catch (IOException e) {
            ArchaicLogger.LOGGER.error("[LetsEncryptHelper] Failed to load certificates from classpath.", e);
        } catch (GeneralSecurityException e) {
            ArchaicLogger.LOGGER.error("[LetsEncryptHelper] Failed to load default keystore.", e);
        }
    }
}
