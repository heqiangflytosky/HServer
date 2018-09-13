package com.hq.android.hserver.sdk.nano;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerRunnable implements Runnable {
    private static final String TAG = "ServerRunnable";
    private static ExecutorService mThreadPool = Executors.newFixedThreadPool(5);
    private HttpServer mHttpServer;
    private final int mTimeout;

    public ServerRunnable(HttpServer server, int timeout) {
        mHttpServer = server;
        mTimeout = timeout;
    }

    @Override
    public void run() {
        ServerSocket serverSocket = mHttpServer.getServerSocket();
        String host = mHttpServer.getHostname();
        int port = mHttpServer.getPort();
        try {
            serverSocket.bind(host != null ? new InetSocketAddress(host, port) : new InetSocketAddress(port));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        do {
            Socket finalAccept = null;
            try {
                finalAccept = serverSocket.accept();
                if (finalAccept != null) {
                    if (mTimeout > 0) {
                        finalAccept.setSoTimeout(mTimeout);
                    }
                    final InputStream inputStream = finalAccept.getInputStream();
                    mThreadPool.execute(new ClientHandler(mHttpServer, inputStream, finalAccept));
                }
            } catch (IOException e) {
                Log.e(TAG, "Communication with the client broken", e);
                if (finalAccept != null) {
                    HttpServer.safeClose(finalAccept);
                }
            }
        } while (!serverSocket.isClosed());
        Log.e(TAG,"Http Server stopped");
    }
}
