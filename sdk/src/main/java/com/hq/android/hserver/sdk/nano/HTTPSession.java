package com.hq.android.hserver.sdk.nano;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import javax.net.ssl.SSLException;

/**
 * Created by heqiang on 18-7-31.
 */

public class HTTPSession {
    private static final String TAG = "HTTPSession";
    public static final int BUFSIZE = 8192;
    private static final String PARA_METHOD = "method";
    private static final String PARA_URI = "uri";
    private final HttpServer mHttpServer;
    private final OutputStream mOutputStream;
    private final BufferedInputStream mInputStream;

    private String mUri;

    private Method mMethod;
    private int mSplitbyte;

    private int mRlen;
    private Map<String, List<String>> mParms;

    private Map<String, String> headers;
    private String mRemoteIp;
    private String mProtocolVersion;
    private String mQueryParameterString;

    public HTTPSession(HttpServer server, InputStream inputStream, OutputStream outputStream, InetAddress inetAddress) {
        this.mHttpServer = server;
        this.mInputStream = new BufferedInputStream(inputStream, HTTPSession.BUFSIZE);
        this.mOutputStream = outputStream;
        this.mRemoteIp = inetAddress.isLoopbackAddress() || inetAddress.isAnyLocalAddress() ? "127.0.0.1" : inetAddress.getHostAddress().toString();
        this.headers = new HashMap<String, String>();
    }

    public void execute() throws IOException {
        Response r = null;
        try{
            byte[] buf = new byte[HTTPSession.BUFSIZE];
            this.mSplitbyte = 0;
            this.mRlen = 0;

            int read = -1;
            this.mInputStream.mark(HTTPSession.BUFSIZE);
            try {
                read = this.mInputStream.read(buf, 0, HTTPSession.BUFSIZE);
            } catch (SSLException e) {
                throw e;
            } catch (IOException e) {
                HttpServer.safeClose(this.mInputStream);
                HttpServer.safeClose(this.mOutputStream);
                throw new SocketException("Connection Shutdown");
            }
            if (read == -1) {
                // socket was been closed
                HttpServer.safeClose(this.mInputStream);
                HttpServer.safeClose(this.mOutputStream);
                throw new SocketException("Connection Shutdown");
            }

            while (read > 0) {
                this.mRlen += read;
                this.mSplitbyte = findHeaderEnd(buf, this.mRlen);
                if (this.mSplitbyte > 0) {
                    break;
                }
                read = this.mInputStream.read(buf, this.mRlen, HTTPSession.BUFSIZE - this.mRlen);
            }

            if (this.mSplitbyte < this.mRlen) {
                this.mInputStream.reset();
                this.mInputStream.skip(this.mSplitbyte);
            }

            this.mParms = new HashMap<String, List<String>>();
            if (null == this.headers) {
                this.headers = new HashMap<String, String>();
            } else {
                this.headers.clear();
            }

            BufferedReader hin = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(buf, 0, this.mRlen)));

            Map<String, String> pre = new HashMap<String, String>();
            decodeHeader(hin, pre, this.mParms, this.headers);

            if (null != this.mRemoteIp) {
                this.headers.put(Header.HEADER_REMOTE_ADDR, this.mRemoteIp);
                this.headers.put(Header.HEADER_HTTP_CLIENT_IP, this.mRemoteIp);
            }

            this.mMethod = Method.lookup(pre.get(PARA_METHOD));
            if (this.mMethod == null) {
                throw new ResponseException(Status.BAD_REQUEST, "BAD REQUEST: Syntax error. HTTP verb " + pre.get("method") + " unhandled.");
            }

            this.mUri = pre.get(PARA_URI);

            String connection = this.headers.get("connection");
            boolean keepAlive = "HTTP/1.1".equals(mProtocolVersion) && (connection == null || !connection.matches("(?i).*close.*"));

            r = mHttpServer.handle(this);

            if (r == null) {
                throw new ResponseException(Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR: Serve() returned a null response.");
            } else {
                String acceptEncoding = this.headers.get(Header.HEADER_ACCEPT_ENCODING);
                r.setRequestMethod(this.mMethod);
                if (acceptEncoding == null || !acceptEncoding.contains("gzip")) {
                    r.setUseGzip(false);
                }
                r.setKeepAlive(keepAlive);
                r.send(this.mOutputStream);
            }
            if (!keepAlive || r.isCloseConnection()) {
                throw new SocketException("NanoHttpd Shutdown");
            }
        } catch (SocketException e) {
            throw e;
        } catch (SocketTimeoutException ste) {
            throw ste;
        } catch (SSLException ssle) {
            Response resp = Response.newFixedLengthResponse(Status.INTERNAL_ERROR, HttpServer.MIME_PLAINTEXT, "SSL PROTOCOL FAILURE: " + ssle.getMessage());
            resp.send(this.mOutputStream);
            HttpServer.safeClose(this.mOutputStream);
        } catch (IOException ioe) {
            Response resp = Response.newFixedLengthResponse(Status.INTERNAL_ERROR, HttpServer.MIME_PLAINTEXT, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
            resp.send(this.mOutputStream);
            HttpServer.safeClose(this.mOutputStream);
        } catch (ResponseException re) {
            Response resp = Response.newFixedLengthResponse(re.getStatus(), HttpServer.MIME_PLAINTEXT, re.getMessage());
            resp.send(this.mOutputStream);
            HttpServer.safeClose(this.mOutputStream);
        } finally {
            HttpServer.safeClose(r);
        }
    }

    private int findHeaderEnd(final byte[] buf, int rlen) {
        int splitbyte = 0;
        while (splitbyte + 1 < rlen) {

            // RFC2616
            if (buf[splitbyte] == '\r' && buf[splitbyte + 1] == '\n' && splitbyte + 3 < rlen && buf[splitbyte + 2] == '\r' && buf[splitbyte + 3] == '\n') {
                return splitbyte + 4;
            }

            // tolerance
            if (buf[splitbyte] == '\n' && buf[splitbyte + 1] == '\n') {
                return splitbyte + 2;
            }
            splitbyte++;
        }
        return 0;
    }

    private void decodeHeader(BufferedReader in, Map<String, String> pre, Map<String, List<String>> parms, Map<String, String> headers) throws ResponseException {
        try {
            String inLine = in.readLine();
            if (inLine == null) {
                return;
            }

            StringTokenizer st = new StringTokenizer(inLine);
            if (!st.hasMoreTokens()) {
                throw new ResponseException(Status.BAD_REQUEST, "BAD REQUEST: Syntax error. Usage: GET /example/file.html");
            }

            pre.put(PARA_METHOD, st.nextToken());

            if (!st.hasMoreTokens()) {
                throw new ResponseException(Status.BAD_REQUEST, "BAD REQUEST: Missing URI. Usage: GET /example/file.html");
            }

            String uri = st.nextToken();

            int qmi = uri.indexOf('?');
            if (qmi >= 0) {
                decodeParms(uri.substring(qmi + 1), parms);
                uri = decodePercent(uri.substring(0, qmi));
            } else {
                uri = decodePercent(uri);
            }

            if (st.hasMoreTokens()) {
                mProtocolVersion = st.nextToken();
            } else {
                mProtocolVersion = "HTTP/1.1";
                Log.e(TAG, "no protocol version specified, strange. Assuming HTTP/1.1.");
            }
            String line = in.readLine();
            while (line != null && !line.trim().isEmpty()) {
                int p = line.indexOf(':');
                if (p >= 0) {
                    headers.put(line.substring(0, p).trim().toLowerCase(Locale.US), line.substring(p + 1).trim());
                }
                line = in.readLine();
            }

            pre.put(PARA_URI, uri);
        } catch (IOException ioe) {
            throw new ResponseException(Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage(), ioe);
        }
    }

    private void decodeParms(String parms, Map<String, List<String>> p) {
        if (parms == null) {
            this.mQueryParameterString = "";
            return;
        }

        this.mQueryParameterString = parms;
        StringTokenizer st = new StringTokenizer(parms, "&");
        while (st.hasMoreTokens()) {
            String e = st.nextToken();
            int sep = e.indexOf('=');
            String key = null;
            String value = null;

            if (sep >= 0) {
                key = decodePercent(e.substring(0, sep));
                if(key != null) {
                    key = key.trim();
                }
                value = decodePercent(e.substring(sep + 1));
            } else {
                key = decodePercent(e);
                if(key != null) {
                    key = key.trim();
                }
                value = "";
            }

            List<String> values = p.get(key);
            if (values == null) {
                values = new ArrayList<String>();
                p.put(key, values);
            }

            values.add(value);
        }
    }

    private String decodePercent(String str) {
        String decoded = null;
        try {
            decoded = URLDecoder.decode(str, "UTF8");
        } catch (UnsupportedEncodingException ignored) {
            Log.e(TAG, "Encoding not supported, ignored", ignored);
        }
        return decoded;
    }

    public final Method getMethod() {
        return this.mMethod;
    }

    public final String getUri() {
        return this.mUri;
    }

    public final Map<String, String> getParms() {
        Map<String, String> result = new HashMap<String, String>();
        for (String key : this.mParms.keySet()) {
            result.put(key, this.mParms.get(key).get(0));
        }

        return result;
    }

}
