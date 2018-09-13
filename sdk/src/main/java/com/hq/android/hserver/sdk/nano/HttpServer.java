package com.hq.android.hserver.sdk.nano;

import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class HttpServer {
    private static final String TAG = "HttpServer";
    public static final int SOCKET_READ_TIMEOUT = 5000;
    public static final String MIME_PLAINTEXT = "text/plain";
    public static final String MIME_HTML = "text/html";
    public static final String MIME_IMAGE_PNG = "image/png";
    private final String mHostname;
    private final int mPort;
    private volatile ServerSocket mServerSocket;
    private Thread mThread;
    private IHandler<HTTPSession, Response> mHttpHandler;
    public HttpServer(String hostname, int port){
        mHostname = hostname;
        mPort = port;
    }

    public void start() throws IOException {
        start(HttpServer.SOCKET_READ_TIMEOUT);
    }

    public void start(final int timeout) throws IOException {
        start(timeout, true);
    }

    public void start(final int timeout, boolean daemon) throws IOException {
        mServerSocket = new ServerSocket();
        mServerSocket.setReuseAddress(true);

        ServerRunnable serverRunnable = new ServerRunnable(this, timeout);
        mThread = new Thread(serverRunnable);
        mThread.setDaemon(daemon);
        mThread.setName("Http Server Listener");
        mThread.start();
    }

    public ServerSocket getServerSocket() {
        return mServerSocket;
    }

    public int getPort() {
        return mPort;
    }

    public String getHostname() {
        return mHostname;
    }

    public void setHTTPHandler(IHandler<HTTPSession, Response> handler) {
        this.mHttpHandler = handler;
    }

    public Response handle(HTTPSession session) {
        if (mHttpHandler == null) {
            return null;
        }
        return mHttpHandler.handle(session);
    }

    public static final void safeClose(Object closeable) {
        try {
            if (closeable != null) {
                if (closeable instanceof Closeable) {
                    ((Closeable) closeable).close();
                } else if (closeable instanceof Socket) {
                    ((Socket) closeable).close();
                } else if (closeable instanceof ServerSocket) {
                    ((ServerSocket) closeable).close();
                } else {
                    throw new IllegalArgumentException("Unknown object to close");
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not close", e);
        }
    }

    public void stop() {
        try {
            safeClose(mServerSocket);
            if (mThread != null) {
                mThread.join();
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not stop all connections", e);
        }
    }

    public final boolean wasStarted() {
        return mServerSocket != null && mThread != null;
    }
}
