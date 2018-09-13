package com.hq.android.hserver;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.hq.android.hserver.sdk.nano.HTTPSession;
import com.hq.android.hserver.sdk.nano.HttpServer;
import com.hq.android.hserver.sdk.nano.IHandler;
import com.hq.android.hserver.sdk.nano.Method;
import com.hq.android.hserver.sdk.nano.Response;
import com.hq.android.hserver.sdk.nano.Status;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = "MainActivity";

    private WebView mWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        testNano();

        mWebView = (WebView) findViewById(R.id.webview);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.setWebChromeClient(new WebChromeClient());
        mWebView.setWebViewClient(new WebViewClient());
        mWebView.loadUrl("http://127.0.0.1:8081");
    }

    private void testNano() {
        HttpServer server = new HttpServer("127.0.0.1", 8081);
        server.setHTTPHandler(new IHandler<HTTPSession, Response>() {
            @Override
            public Response handle(HTTPSession input) {
                return handleNanoResponse(input);
            }
        });
        try {
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Response handleNanoResponse(HTTPSession session) {
        Method method = session.getMethod();
        String uri = session.getUri();

        Log.e(TAG, "uri = "+uri);

        StringBuilder builder = new StringBuilder();
        builder.append("</body></html>\n");

        builder.append("<h1>Hello World</h1>\n");
        builder.append("<p>数据来自本地服务器：http://127.0.0.1:8081</p>\n");

        builder.append("</body></html>\n");

        return Response.newFixedLengthResponse(String.valueOf(builder));
    }
}
