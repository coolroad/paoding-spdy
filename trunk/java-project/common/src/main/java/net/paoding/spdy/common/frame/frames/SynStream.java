package net.paoding.spdy.common.frame.frames;

import java.util.Collections;
import java.util.Map;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * SYN_STREAM
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class SynStream extends ControlFrame implements StreamFrame {

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

    /**
     * 返回当前所有headers
     * 
     * @return 有可能返回的是一个不可修改的Map
     */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * 设置该stream的headers
     * 
     * @param headers
     */
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    /**
     * 返回某个头的值，如果没有则返回null
     * 
     * @param name
     * @return
     */
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

    @Override
    public void decodeData(ChannelBuffer buffer) {
        this.streamId = Math.abs(buffer.readInt());
        this.associatedId = Math.abs(buffer.readInt());
        buffer.skipBytes(2); // skip Priority(2bits) & Unused(14bits) 
        this.headers = Header.decode(buffer);
    }

    @Override
    public void encodeData(ChannelBuffer buffer) {
        buffer.writeInt(streamId);
        buffer.writeInt(associatedId);
        buffer.writeShort(0);
        Header.encode(headers, buffer);
    }

    @Override
    public String toString() {
        return String
                .format("SynStream[streamId={0}, flags={1}, associatedId={2}, headers.size={3}, timestamp={4}]",
                        streamId, flags, associatedId, headers.size(), getTimestamp());
    }

}
