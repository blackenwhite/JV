package com.nabajyoti.systemdesign.week3.httpslab;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

public class HttpsServerLab {

    public static void main(String[] args) throws Exception {
        Path chainPem = Path.of("certs/live/demo.local/fullchain.pem");
        Path keyPem = Path.of("certs/live/demo.local/privkey.pem");

        AtomicReference<SSLContext> sslRef = new AtomicReference<>(buildSslContext(chainPem, keyPem));

        HttpsServer server = HttpsServer.create(new InetSocketAddress(8443), 0);
        server.setHttpsConfigurator(new ReloadingConfigurator(sslRef));
        server.createContext("/", exchange -> {
            byte[] body = "<h1>Hello from hand-rolled Java TLS</h1>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        System.out.println("HTTPS server on https://demo.local:8443");

        startCertWatcher(sslRef, chainPem, keyPem);
    }

    static class ReloadingConfigurator extends HttpsConfigurator {
        private final AtomicReference<SSLContext> ref;

        ReloadingConfigurator(AtomicReference<SSLContext> ref) {
            super(ref.get());
            this.ref = ref;
        }

        @Override
        public SSLContext getSSLContext() {
            return ref.get();   // every NEW handshake reads the current context
        }

        @Override
        public void configure(HttpsParameters params) {
            params.setSSLParameters(ref.get().getDefaultSSLParameters());
        }
    }

    static void startCertWatcher(AtomicReference<SSLContext> ref, Path chainPem, Path keyPem) {
        Thread watcher = new Thread(() -> {
            long lastChainMtime = -1, lastKeyMtime = -1;
            while (true) {
                try {
                    long chainMtime = Files.getLastModifiedTime(chainPem).toMillis();
                    long keyMtime = Files.getLastModifiedTime(keyPem).toMillis();
                    if (lastChainMtime == -1) {          // first pass: just record
                        lastChainMtime = chainMtime;
                        lastKeyMtime = keyMtime;
                    } else if (chainMtime != lastChainMtime || keyMtime != lastKeyMtime) {
                        ref.set(buildSslContext(chainPem, keyPem));
                        lastChainMtime = chainMtime;
                        lastKeyMtime = keyMtime;
                        System.out.println("[" + java.time.LocalTime.now() + "] cert reloaded");
                    }
                    Thread.sleep(2000);
                } catch (Exception e) {
                    // files may be mid-write during renewal; log and retry next tick
                    System.out.println("reload failed, keeping old cert: " + e.getMessage());
                }
            }
        });
        watcher.setDaemon(true);
        watcher.start();
    }

    static SSLContext buildSslContext(Path chainPem, Path keyPem) throws Exception {
        // 1. cert chain (leaf + intermediate) from PEM
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Collection<? extends Certificate> chain;
        try (InputStream in = Files.newInputStream(chainPem)) {
            chain = cf.generateCertificates(in);
        }

        // 2. private key: certbot's privkey.pem is PKCS#8 (RSA or ECDSA depending on certbot config)
        String pem = Files.readString(keyPem)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        PrivateKey key = parsePrivateKey(Base64.getDecoder().decode(pem));

        // 3. in-memory keystore -> key managers -> SSLContext
        char[] password = "lab".toCharArray();
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        ks.setKeyEntry("demo.local", key, password, chain.toArray(new Certificate[0]));

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }

    static PrivateKey parsePrivateKey(byte[] pkcs8Der) throws Exception {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pkcs8Der);
        for (String algorithm : new String[]{"RSA", "EC"}) {
            try {
                return KeyFactory.getInstance(algorithm).generatePrivate(spec);
            } catch (java.security.spec.InvalidKeySpecException ignored) {
                // not this algorithm — try the next
            }
        }
        throw new IllegalArgumentException("Unsupported private key algorithm");
    }
}