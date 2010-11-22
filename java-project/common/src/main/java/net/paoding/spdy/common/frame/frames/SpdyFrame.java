package net.paoding.spdy.common.frame.frames;

import org.jboss.netty.channel.Channel;

/**
 * 表示一个spdy协议frame，所有spdy协议frame应该是直接或间接实现他
 * 
 * @author qieqie.wang
 * 
 */
public interface SpdyFrame {

    /**
     * flags值之一，表示完成
     */
    int FLAG_FIN = 0x01;

    /**
     * flags值之一，表示单向
     */
    int FLAG_UNIDIRECTIONAL = 0x02;

    /**
     * 该frame对应的Channel
     * 
     * @return
     */
    Channel getChannel();

    /**
     * 设置该frame对应的Channel
     * 
     * @param channel
     */
    void setChannel(Channel channel);

    /**
     * 设置frame的flags数据
     * 
     * @return
     */
    int getFlags();

    /**
     * 设置frame的flags数据
     * 
     * @param flags
     */
    void setFlags(int flags);

    /**
     * 该frame的时间戳(创建时间)
     * 
     * @return
     */
    long getTimestamp();

}
