package tech.stompi.wallet;
import android.content.Context;
import android.content.SharedPreferences;
import java.net.URI;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
final class NetworkConfig {
 static final String DEFAULT="https://stompi.tech";
 static final String GENESIS="000000004ce46ead4546e107fc01668e9f3dafa15452a0766d9a03bc2167917b";
 private static volatile SharedPreferences prefs;
 static synchronized void init(Context context){prefs=context.getApplicationContext().getSharedPreferences("stompi.network.v1",Context.MODE_PRIVATE);}
 static String base(){return prefs==null?DEFAULT:prefs.getString("base",DEFAULT);}
 static String normalize(String input) throws Exception {
  URI u=new URI(input.trim());String path=u.getRawPath();
  if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getRawUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null||(path!=null&&!path.isEmpty()&&!path.equals("/"))||u.getPort()==0||u.getPort()>65535)throw new IllegalArgumentException("HTTPS origin required");
  return new URI("https",null,u.getHost().toLowerCase(java.util.Locale.ROOT),u.getPort(),null,null,null).toASCIIString();
 }
 static String rewrite(String url){return url.startsWith(DEFAULT+"/")?base()+url.substring(DEFAULT.length()):url;}
 static JSONObject fetch(String origin,String path)throws Exception {
  HttpURLConnection c=(HttpURLConnection)new URL(origin+path).openConnection();
  c.setInstanceFollowRedirects(false);c.setConnectTimeout(8000);c.setReadTimeout(8000);
  try{if(c.getResponseCode()!=200)throw new IOException("HTTP "+c.getResponseCode());
   try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
    byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>65536)throw new IOException("Response too large");}
    return new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
   }
  }finally{c.disconnect();}
 }
 static String validate(String input)throws Exception {
  String origin=normalize(input);JSONObject info=fetch(origin,"/api/network");
  if(!"stompi".equals(info.getString("network"))||!"main".equals(info.getString("chain"))||!GENESIS.equals(info.getString("genesis_hash"))||info.getInt("wallet_api_version")!=1||!info.getBoolean("mobile_access_v1"))throw new IOException("Incompatible Stompi server");
  JSONObject status=fetch(origin,"/api/status");
  if(status.getLong("indexed_height")!=status.getLong("chain_height"))throw new IOException("Index not synchronized");
  return origin;
 }
 static void save(String origin){if(!prefs.edit().putString("base",origin).commit())throw new IllegalStateException("Cannot save server");}
}
