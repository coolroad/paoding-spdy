package net.paoding.spdy.common.frame;

import net.paoding.spdy.common.frame.frames.ControlFrame;
import net.paoding.spdy.common.frame.frames.DataFrame;
import net.paoding.spdy.common.frame.frames.FlaterConfigurable;
import net.paoding.spdy.common.frame.util.ControlFrameUtil;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

/**
 * spdy帧编码器，配置在netty的 {@link ChannelPipeline}
 * 
 * @author qieqie.wang@gmail.com
 * @author weibo.leo@gmail.com
 */
public class FrameEncoder extends OneToOneEncoder {

    private static Log logger = LogFactory.getLog(FrameEncoder.class);

    private ChannelConfig config;

    public FrameEncoder(ChannelConfig config) {
        this.config = config;
    }

    @Override
    protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg)
            throws Exception {
        if (msg instanceof FlaterConfigurable) {
            ((FlaterConfigurable) msg).setUsingFlater(config.usingFlater);
        }
        // 控制帧编码
        if (msg instanceof ControlFrame) {
            ControlFrame frame = (ControlFrame) msg;
            ChannelBuffer head = channel.getConfig().getBufferFactory().getBuffer(8);
            ChannelBuffer data = frame.encodeData(channel.getConfig().getBufferFactory());
            // control bit&version
            head.writeShort(ControlFrameUtil.encodeVersion(frame.getVersion()));
            head.writeShort(frame.getType());
            head.writeByte(frame.getFlags());
            head.writeMedium(data.writerIndex()); // length
            if (logger.isDebugEnabled()) {
                logger.debug("writing " + frame);
            }
            if (data.readable()) {
                return ChannelBuffers.wrappedBuffer(head, data);
            } else {
                return head;
            }
        }
        // 数据帧编码
        else if (msg instanceof DataFrame) {
            DataFrame frame = (DataFrame) msg;
            ChannelBuffer data = frame.getData();
            ChannelBuffer head = ChannelBuffers.buffer(//
                    channel.getConfig().getBufferFactory().getDefaultOrder(), 8);
            head.writeInt(frame.getStreamId());// streamId肯定是正数，于是能够保证control bit为0
            head.writeByte(frame.getFlags());
            int dataLength = data.readableBytes();
            head.writeMedium(dataLength); // length
            if (logger.isDebugEnabled()) {
                logger.debug("writing " + frame);
            }
            if (dataLength > 0) {
                return ChannelBuffers.wrappedBuffer(head, data);
            } else {
                return head;
            }
        }
        //
        else {
            return msg;
        }
    }
}
