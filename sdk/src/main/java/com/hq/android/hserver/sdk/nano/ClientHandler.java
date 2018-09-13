package com.hq.android.hserver.sdk.nano;

import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class ClientHandler implements Runnable{
    private final static String TAG = "ClientHandler";
    private final HttpServer mHttpServer;
    private final InputStream mInputStream;
    private final Socket mAcceptSocket;
    public ClientHandler(HttpServer server, InputStream inputStream, Socket acceptSocket) {
        this.mHttpServer = server;
        this.mInputStream = inputStream;
        this.mAcceptSocket = acceptSocket;
    }

    public void close() {
        HttpServer.safeClose(mInputStream);
        HttpServer.safeClose(mAcceptSocket);
    }


    @Override
    public void run() {
        OutputStream outputStream = null;
        try {
            outputStream = mAcceptSocket.getOutputStream();
            HTTPSession session = new HTTPSession(mHttpServer, mInputStream, outputStream, mAcceptSocket.getInetAddress());
            while (!mAcceptSocket.isClosed()) {
                session.execute();
            }
        } catch (Exception e) {
            if (!(e instanceof SocketException && "NanoHttpd Shutdown".equals(e.getMessage())) && !(e instanceof SocketTimeoutException)) {
                Log.e(TAG, "Communication with the client broken, or an bug in the handler code", e);
            }
        } finally {
            HttpServer.safeClose(outputStream);
            HttpServer.safeClose(mInputStream);
            HttpServer.safeClose(mAcceptSocket);
        }
    }
}
