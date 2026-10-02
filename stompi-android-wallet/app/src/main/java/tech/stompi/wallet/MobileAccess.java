package tech.stompi.wallet;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.nio.charset.StandardCharsets;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.*;
import org.json.JSONObject;

/** Distinct installation access key, never a wallet seed. */
final class MobileAccess {
    private static final String ALIAS="stompi.api.installation.v1";
    private static synchronized KeyStore keys() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)) {
            KeyPairGenerator generator=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,"AndroidKeyStore");
            generator.initialize(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256).build());
            generator.generateKeyPair();
        }
        return store;
    }
    private static String hash(byte[] bytes) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result=new StringBuilder();for(byte b:digest)result.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return result.toString();
    }
    private static String sign(KeyStore keys,String message) throws Exception {
        Signature signature=Signature.getInstance("SHA256withECDSA");
        signature.initSign((PrivateKey)keys.getKey(ALIAS,null));signature.update(message.getBytes(StandardCharsets.US_ASCII));
        return Base64.encodeToString(signature.sign(),Base64.NO_WRAP);
    }
    private static JSONObject post(String endpoint,JSONObject body) throws Exception {
        HttpURLConnection con=(HttpURLConnection)new URL(NetworkConfig.base()+"/api/mobile/"+endpoint).openConnection();
        try {
            con.setInstanceFollowRedirects(false);con.setRequestMethod("POST");con.setConnectTimeout(8000);con.setReadTimeout(10000);
            con.setRequestProperty("Content-Type","application/json");con.setDoOutput(true);
            byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);con.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream out=con.getOutputStream()){out.write(bytes);}
            if(con.getResponseCode()!=200)throw new IOException("Mobile access unavailable");
            try(InputStream in=con.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[1024];int n;while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>4096)throw new IOException("Response too large");}
                return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
            }
        } finally {con.disconnect();}
    }
    static String register() throws Exception {
        KeyStore store=keys();byte[] publicKey=store.getCertificate(ALIAS).getPublicKey().getEncoded();String id=hash(publicKey);
        JSONObject reply=post("register",new JSONObject().put("public_key",Base64.encodeToString(publicKey,Base64.NO_WRAP))
                .put("signature",sign(store,"stompi-install-v1\n"+id)));
        if(!id.equals(reply.getString("installation_id")))throw new IOException("Installation mismatch");
        return id;
    }
    static JSONObject broadcastPayload(String hex) throws Exception {
        KeyStore store=keys();String id=register();
        byte[] tx=new byte[hex.length()/2];for(int i=0;i<tx.length;i++)tx[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);
        String digest=hash(tx);
        JSONObject challenge=post("challenge",new JSONObject().put("installation_id",id).put("transaction_sha256",digest)
                .put("signature",sign(store,"stompi-challenge-v1\n"+id+"\n"+digest)));
        String nonce=challenge.getString("nonce");
        if(!nonce.matches("[0-9a-f]{64}"))throw new IOException("Invalid challenge");
        return new JSONObject().put("installation_id",id).put("hex",hex).put("nonce",nonce)
                .put("signature",sign(store,"stompi-broadcast-v1\n"+id+"\n"+nonce+"\n"+digest));
    }
}
