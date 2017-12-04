package com.cxria.microtranslate;


import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.internal.Streams;

import org.java_websocket.client.DefaultSSLWebSocketClientFactory;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.security.cert.X509Certificate;

/**
 * Created by whr on 17-3-18.
 */
public class SocketUtils extends WebSocketClient {
    private static Timer timer;
    private static SocketUtils webSocketWorker = null;
    private static ExecutorService executor = Executors.newFixedThreadPool(1);
    private final static String URL="wss://dev.microsofttranslator.com/speech/translate?from=zh-Hans&to=en-US&api-version=1.0";
    private final String TAG="WebSocketClient";
    public SocketUtils(URI serverUri) {
        super(serverUri);
    }

    public SocketUtils(URI serverUri , Draft draft , Map<String,String> headers , int connecttimeout){
        super(serverUri , draft ,headers ,connecttimeout);
    }

    @Override
    public void onClose(int arg0, String arg1, boolean arg2) {
        try {
            if (timer != null) {
                timer.cancel();
            }
            if (arg0 != 1000 && arg0 != -1) {
                    connectToServer();
                    Log.i(TAG,"onClose[断开,正在重连] --> " + arg1 + "," + arg0 + "," + arg2);
            } else {
                Log.i(TAG,"正常断开:"+arg1+arg0+" "+arg2);
            }
        } catch (WebsocketNotConnectedException e) {
            Log.i(TAG,"onClose[断开且没有重连] --> " + arg1 + "," + arg0 + "," + arg2);
        }
    }

    @Override
    public void onError(Exception arg0) {
        try {
            if (timer != null)
                timer.cancel();
            Log.i(TAG,"showErrorMsg --> " + arg0.getMessage());
        } catch (WebsocketNotConnectedException e) {
            Log.i(TAG,"onError[错误] --> " + e.getMessage());
        }
    }

    @Override
    public void onMessage(String arg0) {
        Log.i("recodeResult: ","" +arg0);
        mSocketready.socketReady(arg0);
        try {

        } catch (WebsocketNotConnectedException e) {
            Log.i(TAG,"onMessage[错误] --> " + e.getMessage());
        }
    }
    interface Socketready{
        void socketReady(String txt);
    }

    private static Socketready mSocketready;

    public static void setSocketready(Socketready socketready) {
        mSocketready = socketready;
    }

    @Override
    public void onOpen(final ServerHandshake arg0) {
        //开启成功的回调
        //定时发送消息
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    byte[] bs=new byte[1];
                    bs[0]='i';
                    ByteBuffer byteBuffer=ByteBuffer.wrap(bs);
//                    webSocketWorker.send(byteBuffer.toString());
//                    Log.i(TAG,"socketSend");
                } catch (WebsocketNotConnectedException e) {
                    Log.i(TAG,"connectToServer的TimerTask 错误 --> " + e.toString());
                }
            }
        }, 30000, 30000);
        try {
            Log.i(TAG,"onOpen --> " + arg0.getHttpStatusMessage());
        } catch (WebsocketNotConnectedException e) {
            Log.i(TAG,"onOpen[错误] --> " + e.getMessage());
        }
    }

    /**
     * 连接
     */
    public static void connectToServer() {
        final String TAG="WebSocketClient";
        executor.execute(new Runnable() {
            @Override
            public void run() {
                 Log.i(TAG,"ReConnectToServer -->");
                try {
                    if (webSocketWorker != null)
                        webSocketWorker.closeBlocking();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                URI uri = URI.create(URL);
//                webSocketWorker=new SocketUtils(uri);
                Map<String,String> header =new HashMap<String, String>();
//                header.put("X-ClientTraceId","");
                header.put("Ocp-Apim-Subscription-Key","d6c3bc8fe5a94bac9db92a58efdd0856");
                webSocketWorker=new SocketUtils(uri,new Draft_17(),header,10000);
//                webSocketWorker.connect();
                //支持wss
                TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {

                    }

                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {

                    }

                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[] {};
                    }

                    public void checkClientTrusted(X509Certificate[] chain,
                                                   String authType) throws CertificateException {
                    }

                    public void checkServerTrusted(X509Certificate[] chain,
                                                   String authType) throws CertificateException {
                    }
                } };
                SSLContext sc = null;
                try {
                    sc = SSLContext.getInstance("TLS");
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }
                try {
                    sc.init(null, trustAllCerts, new SecureRandom());

                } catch (KeyManagementException e) {
                    e.printStackTrace();
                }
                // Otherwise the line below is all that is needed.
//                 sc.init(null, null, null);
                webSocketWorker.setWebSocketFactory(new DefaultSSLWebSocketClientFactory(sc));
                webSocketWorker.connect();
            }
        });
    }

    /**
     * 断开链接
     */
    public static void disconnectToServer() {
        try {
            if (webSocketWorker != null)
                webSocketWorker.closeBlocking();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 发送消息
     * @param
     */
    public static void sendMsg(byte[] bytes){

        if(webSocketWorker!=null){
            try {
                webSocketWorker.send(bytes);
            }catch (Exception e){
//                LogUtils.LogNoTag("socket exexption:"+e.toString());
                e.printStackTrace();
            }
        }else {
            Log.i("jjj","WebSocket send error");
        }
    }
}
