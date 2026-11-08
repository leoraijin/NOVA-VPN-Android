package com.nova.vpn.android;

import android.app.Instrumentation;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;

/** Fixed external diagnostic, normal app network and system TLS; never accepts TCP fallback. */
public final class Http3PhysicalSmoke {
    public static String run(Instrumentation instrumentation) throws Exception {
        return runContext(instrumentation.getTargetContext(),true);
    }
    public static String runContext(Context context, boolean physical) throws Exception {
        return runContext(context,physical,"1.1.1.1");
    }
    public static String runContext(Context context, boolean physical, String host) throws Exception {
        if(!host.equals("1.1.1.1")&&!host.equals("one.one.one.one"))throw new IllegalArgumentException("Fixed HTTP3 hosts only");
        if(android.os.Build.VERSION.SDK_INT<34 || !(context.getPackageName().equals("com.nova.vpn.android.usbtest.completionpreview") || context.getPackageName().equals("com.nova.vpn.android.usbtest.completionpreview.test")))throw new IllegalStateException("Isolated API34+ package required");
        ConnectivityManager manager=context.getSystemService(ConnectivityManager.class);
        Network before=manager.getActiveNetwork();NetworkCapabilities caps=manager.getNetworkCapabilities(before);
        if(caps==null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==physical)throw new IllegalStateException("Unexpected default network capture");
        String version=HttpEngine.getVersionString();
        ExecutorService executor=Executors.newSingleThreadExecutor();
        HttpEngine engine=null;UrlRequest request=null;CountDownLatch done=new CountDownLatch(1);
        AtomicReference<Integer> networkErrorCode=new AtomicReference<>();AtomicReference<String> error=new AtomicReference<>();AtomicReference<UrlResponseInfo> response=new AtomicReference<>();
        java.io.ByteArrayOutputStream body=new java.io.ByteArrayOutputStream();
        long started=android.os.SystemClock.elapsedRealtime();
        try {
            engine=new HttpEngine.Builder(context).setUserAgent("NOVA-Routing-HTTP3-Test/1")
                .setEnableQuic(true).setEnableHttp2(false).setEnableHttpCache(HttpEngine.Builder.HTTP_CACHE_DISABLED,0)
                .addQuicHint(host,443,443).build();
            UrlRequest.Callback callback=new UrlRequest.Callback() {
                public void onRedirectReceived(UrlRequest r,UrlResponseInfo i,String location){error.set("redirect rejected");r.cancel();}
                public void onResponseStarted(UrlRequest r,UrlResponseInfo i){
                    response.set(i);
                    if(i.getHttpStatusCode()!=200){error.set("HTTP status "+i.getHttpStatusCode());r.cancel();return;}
                    r.read(ByteBuffer.allocateDirect(4096));
                }
                public void onReadCompleted(UrlRequest r,UrlResponseInfo i,ByteBuffer buffer){
                    buffer.flip();byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);body.write(chunk,0,chunk.length);
                    if(body.size()>8192){error.set("response too large");r.cancel();return;}
                    buffer.clear();r.read(buffer);
                }
                public void onSucceeded(UrlRequest r,UrlResponseInfo i){response.set(i);done.countDown();}
                public void onFailed(UrlRequest r,UrlResponseInfo i,HttpException failure){if(failure instanceof NetworkException)networkErrorCode.set(((NetworkException)failure).getErrorCode());error.set(failure.getClass().getSimpleName()+": "+failure.getMessage());done.countDown();}
                public void onCanceled(UrlRequest r,UrlResponseInfo i){done.countDown();}
            };
            request=engine.newUrlRequestBuilder("https://"+host+"/cdn-cgi/trace?nova="+java.util.UUID.randomUUID(),executor,callback)
                .setCacheDisabled(true).addHeader("Cache-Control","no-cache").build();
            request.start();
            if(!done.await(12,TimeUnit.SECONDS)){error.set("request timeout");request.cancel();if(!done.await(2,TimeUnit.SECONDS))throw new IllegalStateException("Cancellation not acknowledged");}
            UrlResponseInfo info=response.get();String text=new String(body.toByteArray(),StandardCharsets.UTF_8);
            StringBuilder digest=new StringBuilder();for(byte value:java.security.MessageDigest.getInstance("SHA-256").digest(body.toByteArray()))digest.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
            JSONObject result=new JSONObject().put("host",host).put("engineVersion",version).put("sdk",android.os.Build.VERSION.SDK_INT)
                .put("status",info==null?0:info.getHttpStatusCode()).put("protocol",info==null?"none":info.getNegotiatedProtocol())
                .put("networkErrorCode",networkErrorCode.get()==null?JSONObject.NULL:networkErrorCode.get()).put("cached",info!=null&&info.wasCached()).put("bodyBytes",body.size()).put("bodySha256",digest.toString())
                .put("serverReportsHttp3",text.contains("http=http/3\n")).put("elapsedMillis",android.os.SystemClock.elapsedRealtime()-started)
                .put("samePhysicalNetwork",physical&&before!=null&&before.equals(manager.getActiveNetwork())).put("sameDefaultNetwork",before!=null&&before.equals(manager.getActiveNetwork())).put("physicalExpected",physical).put("error",error.get()==null?JSONObject.NULL:error.get());
            if(error.get()!=null || info==null || !"h3".equals(info.getNegotiatedProtocol()) || info.wasCached() || !text.contains("http=http/3\n") || !before.equals(manager.getActiveNetwork()))
                throw new IllegalStateException("HTTP3 not verified: "+result);
            return result.toString();
        } finally {
            if(request!=null&&!request.isDone()){request.cancel();done.await(2,TimeUnit.SECONDS);}
            if(engine!=null)engine.shutdown();
            executor.shutdownNow();if(!executor.awaitTermination(2,TimeUnit.SECONDS))throw new IllegalStateException("HTTP3 callback executor still active");
        }
    }
}