package net.paoding.spdy.common.frame.frames;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Map;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * RST_STREAM
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class RstStream extends ControlFrame implements HeaderStreamFrame {

    public static final int TYPE = 3;

    //-----------------------------------------------------------

    public static final int SC_PROTOCOL_ERROR = 1;

    public static final int SC_INVALID_STREAM = 2;

    public static final int SC_REFUSED_STREAM = 3;

    public static final int SC_UNSUPPORTED_VERSION = 4;

    public static final int SC_CANCEL = 5;

    public static final int SC_INTERNAL_ERROR = 6;

    //-----------------------------------------------------------

    public RstStream() {
        super(TYPE);
    }

    //-----------------------------------------------------------

    private int statusCode;

    protected int streamId;

    private Map<String, String> headers = Collections.emptyMap();

    public int getStreamId() {
        return streamId;
    }

    public void setStreamId(int streamId) {
        this.streamId = streamId;
    }

    @Override
    public Map<String, String> getHeaders() {
        return headers;
    }

    @Override
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    @Override
    public String getHeader(String name) {
        return headers.get(name);
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isProtocolError() {
        return this.statusCode == SC_PROTOCOL_ERROR;
    }

    public boolean isInvalidStream() {
        return this.statusCode == SC_INVALID_STREAM;
    }

    public boolean isRefusedStream() {
        return this.statusCode == SC_REFUSED_STREAM;
    }

    public boolean isUnsupportedVersion() {
        return this.statusCode == SC_UNSUPPORTED_VERSION;
    }

    public boolean isCancel() {
        return this.statusCode == SC_CANCEL;
    }

    public boolean isInternalError() {
        return this.statusCode == SC_INTERNAL_ERROR;
    }

    @Override
    public void decodeData(ChannelBuffer buffer, int length) {
        this.streamId = Math.abs(buffer.readInt());
        this.statusCode = buffer.readInt();
    }

    @Override
    public void encodeData(ChannelBuffer buffer) {
        buffer.writeInt(streamId);
        buffer.writeInt(statusCode);
    }

    @Override
    public String toString() {
        return String.format(
                "RstStream[streamId=%s, flags=%s, statusCode={2}, headers.size=%s, timestamp=%s]",
                streamId, flags, statusCode, headers.size(), new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss").format(getTimestamp()));
    }

}
