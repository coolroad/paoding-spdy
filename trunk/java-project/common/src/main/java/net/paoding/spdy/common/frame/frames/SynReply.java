package net.paoding.spdy.common.frame.frames;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Map;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * SYN_REPLY控制帧
 * 
 * @author qieqie.wang
 * 
 */
public class SynReply extends ControlFrame implements HeaderStreamFrame {

    /** SYN_REPLY的类型值 */
    public static final int TYPE = 2;

    protected int streamId;

    private Map<String, String> headers = Collections.emptyMap();

    /**
     * 
     */
    public SynReply() {
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

    @Override
    public void decodeData(ChannelBuffer buffer) {
        this.streamId = Math.abs(buffer.readInt());
        buffer.skipBytes(2); // Unused
        this.headers = HeaderUtil.decode(buffer);
    }

    @Override
    public void encodeData(ChannelBuffer buffer) {
        buffer.writeInt(streamId);
        buffer.writeShort(0);
        HeaderUtil.encode(headers, buffer);
    }

    @Override
    public String toString() {
        return String.format("SynRely[streamId=%s, flags=%s, headers.size=%s, timestamp=%s]",
                streamId, flags, headers.size(),
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(getTimestamp()));
    }

}
