package com.hq.android.hserver.sdk.nano;

import android.util.Log;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.GZIPOutputStream;

public class Response implements Closeable {
    private static final String TAG = "Response";
    private static final int BUFFER_SIZE_SEND = 16 * 1024;

    private Status mStatus;
    private String mMimeType;
    private InputStream mData;
    private long mContentLength;

    private final Map<String, String> mLowerCaseHeader = new HashMap<String, String>();
    private Method mRequestMethod;
    private boolean mChunkedTransfer;
    private boolean mKeepAlive;
    private List<String> mCookieHeaders;
    private GzipUsage mGzipUsage = GzipUsage.DEFAULT;

    @SuppressWarnings("serial")
    private final Map<String, String> mHeader = new HashMap<String, String>() {

        public String put(String key, String value) {
            mLowerCaseHeader.put(key == null ? key : key.toLowerCase(), value);
            return super.put(key, value);
        };
    };

    private enum GzipUsage {
        DEFAULT,
        ALWAYS,
        NEVER;
    }
    @Override
    public void close() throws IOException {
        if (mData != null) {
            mData.close();
        }
    }

    protected Response(Status status, String mimeType, InputStream data, long totalBytes) {
        mStatus = status;
        mMimeType = mimeType;
        if (data == null) {
            mData = new ByteArrayInputStream(new byte[0]);
            mContentLength = 0L;
        } else {
            mData = data;
            mContentLength = totalBytes;
        }
        mChunkedTransfer = mContentLength < 0;
        mKeepAlive = true;
        mCookieHeaders = new ArrayList(10);
    }

    public static Response newFixedLengthResponse(String msg) {
        return newFixedLengthResponse(Status.OK, HttpServer.MIME_HTML, msg);
    }

    public static Response newChunkedResponse(Status status, String mimeType, InputStream data) {
        return new Response(status, mimeType, data, -1);
    }

    public static Response newFixedLengthResponse(Status status, String mimeType, InputStream data, long totalBytes) {
        return new Response(status, mimeType, data, totalBytes);
    }
    public static Response newFixedLengthResponse(Status status, String mimeType, String txt) {
        ContentType contentType = new ContentType(mimeType);
        if (txt == null) {
            return newFixedLengthResponse(status, mimeType, new ByteArrayInputStream(new byte[0]), 0);
        } else {
            byte[] bytes;
            try {
                CharsetEncoder newEncoder = Charset.forName(contentType.getEncoding()).newEncoder();
                if (!newEncoder.canEncode(txt)) {
                    contentType = contentType.tryUTF8();
                }
                bytes = txt.getBytes(contentType.getEncoding());
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "encoding problem, responding nothing", e);
                bytes = new byte[0];
            }
            return newFixedLengthResponse(status, contentType.getContentTypeHeader(), new ByteArrayInputStream(bytes), bytes.length);
        }
    }

    public void send(OutputStream outputStream) {
        SimpleDateFormat gmtFrmt = new SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        gmtFrmt.setTimeZone(TimeZone.getTimeZone("GMT"));

        try {
            if (mStatus == null) {
                throw new Error("sendResponse(): Status can't be null.");
            }
            PrintWriter pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(outputStream, new ContentType(mMimeType).getEncoding())), false);
            pw.append("HTTP/1.1 ").append(mStatus.getDescription()).append(" \r\n");
            if (mMimeType != null) {
                printHeader(pw, Header.HEADER_CONTENT_TYPE, mMimeType);
            }
            if (getHeader("date") == null) {
                printHeader(pw, "Date", gmtFrmt.format(new Date()));
            }
            for (Map.Entry<String, String> entry : mHeader.entrySet()) {
                printHeader(pw, entry.getKey(), entry.getValue());
            }
            for (String cookieHeader : mCookieHeaders) {
                printHeader(pw, Header.HEADER_SET_COOKIE, cookieHeader);
            }
            if (getHeader("connection") == null) {
                printHeader(pw, Header.HEADER_CONNECTION, (mKeepAlive ? "keep-alive" : "close"));
            }
            if (getHeader("content-length") != null) {
                setUseGzip(false);
            }
            if (useGzipWhenAccepted()) {
                printHeader(pw, Header.HEADER_CONTENT_ENCODING, "gzip");
                setChunkedTransfer(true);
            }
            long pending = mData != null ? mContentLength : 0;
            if (mRequestMethod != Method.HEAD && mChunkedTransfer) {
                printHeader(pw, Header.HEADER_TRANSFER_ENCODING, "chunked");
            } else if (!useGzipWhenAccepted()) {
                pending = sendContentLengthHeaderIfNotAlreadyPresent(pw, pending);
            }
            pw.append("\r\n");
            pw.flush();
            sendBodyWithCorrectTransferAndEncoding(outputStream, pending);
            outputStream.flush();
            HttpServer.safeClose(mData);
        } catch (IOException ioe) {
            Log.e(TAG, "Could not send response to the client", ioe);
        }
    }

    public Response setUseGzip(boolean useGzip) {
        mGzipUsage = useGzip ? GzipUsage.ALWAYS : GzipUsage.NEVER;
        return this;
    }

    public void setChunkedTransfer(boolean chunkedTransfer) {
        mChunkedTransfer = chunkedTransfer;
    }

    public void setRequestMethod(Method requestMethod) {
        mRequestMethod = requestMethod;
    }

    public void setKeepAlive(boolean useKeepAlive) {
        mKeepAlive = useKeepAlive;
    }

    public void addHeader(String name, String value) {
        mHeader.put(name, value);
    }

    protected long sendContentLengthHeaderIfNotAlreadyPresent(PrintWriter pw, long defaultSize) {
        String contentLengthString = getHeader("content-length");
        long size = defaultSize;
        if (contentLengthString != null) {
            try {
                size = Long.parseLong(contentLengthString);
            } catch (NumberFormatException ex) {
                Log.e(TAG,"content-length was no number " + contentLengthString);
            }
        }else{
            pw.print("Content-Length: " + size + "\r\n");
        }
        return size;
    }

    private void sendBodyWithCorrectTransferAndEncoding(OutputStream outputStream, long pending) throws IOException {
        if (mRequestMethod != Method.HEAD && mChunkedTransfer) {
            ChunkedOutputStream chunkedOutputStream = new ChunkedOutputStream(outputStream);
            sendBodyWithCorrectEncoding(chunkedOutputStream, -1);
            chunkedOutputStream.finish();
        } else {
            sendBodyWithCorrectEncoding(outputStream, pending);
        }
    }

    private void sendBodyWithCorrectEncoding(OutputStream outputStream, long pending) throws IOException {
        if (useGzipWhenAccepted()) {
            GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream);
            sendBody(gzipOutputStream, -1);
            gzipOutputStream.finish();
        } else {
            sendBody(outputStream, pending);
        }
    }

    public boolean useGzipWhenAccepted() {
        if (mGzipUsage == GzipUsage.DEFAULT)
            return getMimeType() != null && (getMimeType().toLowerCase().contains("text/") || getMimeType().toLowerCase().contains("/json"));
        else
            return mGzipUsage == GzipUsage.ALWAYS;
    }

    private void sendBody(OutputStream outputStream, long pending) throws IOException {
        if (mData == null) {
            return;
        }
        long BUFFER_SIZE = BUFFER_SIZE_SEND;
        byte[] buff = new byte[(int) BUFFER_SIZE];
        boolean sendEverything = pending == -1;
        while (pending > 0 || sendEverything) {
            long bytesToRead = sendEverything ? BUFFER_SIZE : Math.min(pending, BUFFER_SIZE);
            int read = mData.read(buff, 0, (int) bytesToRead);
            if (read <= 0) {
                break;
            }
            try {
                outputStream.write(buff, 0, read);
            } catch (Exception e) {
                if(mData != null) {
                    mData.close();
                }
                break;
            }
            if (!sendEverything) {
                pending -= read;
            }
        }
    }

    protected void printHeader(PrintWriter pw, String key, String value) {
        pw.append(key).append(": ").append(value).append("\r\n");
    }

    public String getHeader(String name) {
        return mLowerCaseHeader.get(name.toLowerCase());
    }

    public String getMimeType() {
        return mMimeType;
    }

    public boolean isCloseConnection() {
        return "close".equals(getHeader("connection"));
    }
}
