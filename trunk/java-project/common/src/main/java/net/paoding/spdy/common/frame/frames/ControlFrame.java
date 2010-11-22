package net.paoding.spdy.common.frame.frames;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;

/**
 * 所有具体的控制帧都继承此类
 * 
 * @author qieqie.wang
 * 
 */
public abstract class ControlFrame implements SpdyFrame {

    //----------------------------------------------------------------

    protected int flags;

    protected Channel channel;

    private long timestamp;

    private final int type;

    public ControlFrame(int type) {
        this.timestamp = System.currentTimeMillis();
        this.type = type;
    }

    /**
     * 控制帧的类型值标识
     * 
     * @return
     */
    public int getType() {
        return type;
    }

    /**
     * 解码：从buffer中解出该frame的数据
     * 
     * @param buffer
     */
    public abstract void decodeData(ChannelBuffer buffer);

    /**
     * 编码：将该frame体内容写入buffer中
     * 
     * @param buffer
     */
    public abstract void encodeData(ChannelBuffer buffer);

    @Override
    public int getFlags() {
        return flags;
    }

    @Override
    public void setFlags(int flags) {
        this.flags = flags;
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    @Override
    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    public abstract String toString();

}
