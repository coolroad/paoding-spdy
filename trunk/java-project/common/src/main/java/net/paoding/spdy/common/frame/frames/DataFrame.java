package net.paoding.spdy.common.frame.frames;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;

/**
 * 代表一个数据帧
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class DataFrame implements StreamFrame {

    //-----------------------------------------------------------

    private int streamId;

    private int flags;

    protected Channel channel;

    private static ChannelBuffer empty = ChannelBuffers.buffer(0);

    private ChannelBuffer data = empty;

    private long timestamp;

    //-----------------------------------------------------------

    /**
     * 创建一个空内容的数据帧
     */
    public DataFrame() {
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public int getStreamId() {
        return streamId;
    }

    @Override
    public void setStreamId(int streamId) {
        this.streamId = streamId;
    }

    @Override
    public int getFlags() {
        return flags;
    }

    @Override
    public void setFlags(int flags) {
        this.flags = flags;
    }

    /**
     * 返回数据帧的数据内容
     * 
     * @return
     */
    public ChannelBuffer getData() {
        return data;
    }

    /**
     * 设置数据帧的数据内容
     * 
     * @param data
     */
    public void setData(ChannelBuffer data) {
        if (data == null) {
            data = empty;
        }
        this.data = data;
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("DataFrame[streamId={0}, flags={1}, data={2}]", streamId, flags, data);
    }

}
