package net.paoding.spdy.common.frame.frames;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Map;
import java.util.zip.DataFormatException;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * SYN_STREAM
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class SynStream extends ControlFrame implements HeaderStreamFrame, FlaterConfigurable {

    public static final int TYPE = 1;

    //-----------------------------------------------------------

    private int associatedId;

    protected int streamId;

    private Map<String, String> headers = Collections.emptyMap();

    public SynStream() {
        super(TYPE);
    }

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

    /**
     * 
     * @return
     */
    public int getAssociatedId() {
        return associatedId;
    }

    /**
     * 
     * @param associatedId
     */
    public void setAssociatedId(int associatedId) {
        this.associatedId = associatedId;
    }

    private boolean usingDecompressing = true;

    @Override
    public void setUsingFlater(boolean usingDecompressing) {
        this.usingDecompressing = usingDecompressing;
    }

    @Override
    public void decodeData(ChannelBuffer buffer, int length) throws DataFormatException {
        this.streamId = Math.abs(buffer.readInt());
        this.associatedId = Math.abs(buffer.readInt());
        buffer.skipBytes(2); // skip Priority(2bits) & Unused(14bits) 
        this.headers = HeaderUtil.decode(buffer, length - 10, usingDecompressing);
    }

    @Override
    public void encodeData(ChannelBuffer buffer) {
        buffer.writeInt(streamId);
        buffer.writeInt(associatedId);
        buffer.writeShort(0);
        HeaderUtil.encode(headers, buffer, usingDecompressing);
    }

    @Override
    public String toString() {
        return String
                .format("SynStream[streamId=%s, flags=%s, associatedId=%s, headers.size=%s, timestamp=%s, deflate=%s]",
                        streamId, flags, associatedId, headers.size(), new SimpleDateFormat(
                                "yyyy-MM-dd HH:mm:ss").format(getTimestamp()), usingDecompressing);
    }

}
