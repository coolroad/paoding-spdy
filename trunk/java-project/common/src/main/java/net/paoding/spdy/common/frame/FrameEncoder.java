package net.paoding.spdy.common.frame;

import net.paoding.spdy.common.frame.frames.ControlFrame;
import net.paoding.spdy.common.frame.frames.DataFrame;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

/**
 * spdy帧编码器，配置在netty的 {@link ChannelPipeline}
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
@Sharable
public class FrameEncoder extends OneToOneEncoder {

    @Override
    protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg)
            throws Exception {
        // 控制帧编码
        if (msg instanceof ControlFrame) {
            ControlFrame frame = (ControlFrame) msg;
            ChannelBuffer buffer = ChannelBuffers.dynamicBuffer(//
                    1024, channel.getConfig().getBufferFactory());
            // skip 8 bytes for head
            buffer.writerIndex(8);
            frame.encodeData(buffer);
            int limit = buffer.writerIndex();
            // write head
            buffer.writerIndex(0);
            buffer.writeShort(-1);// control bit&version
            buffer.writeShort(frame.getType());
            buffer.writeByte(frame.getFlags());
            buffer.writeMedium(limit - 8); // length
            buffer.writerIndex(limit);
            return buffer;
        }
        // 数据帧编码
        else if (msg instanceof DataFrame) {
            DataFrame frame = (DataFrame) msg;
            ChannelBuffer data = frame.getData().duplicate();
            ChannelBuffer head = ChannelBuffers.buffer(//
                    channel.getConfig().getBufferFactory().getDefaultOrder(), 8);
            int dataLength = data.readableBytes();
            head.writeInt(frame.getStreamId());// streamId肯定是正数，于是能够保证control bit为0
            head.writeByte(frame.getFlags());
            head.writeMedium(dataLength); // length
            return ChannelBuffers.wrappedBuffer(head, data);
        } else {
            return msg;
        }
    }

}
