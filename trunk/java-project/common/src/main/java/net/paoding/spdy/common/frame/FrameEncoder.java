package net.paoding.spdy.common.frame;

import java.util.Map;

import net.paoding.spdy.common.frame.frames.ControlFrame;
import net.paoding.spdy.common.frame.frames.DataFrame;
import net.paoding.spdy.common.frame.frames.HeaderStreamFrame;
import net.paoding.spdy.common.frame.frames.HeaderUtil;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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

    private static Log logger = LogFactory.getLog(FrameEncoder.class);

    @Override
    protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg)
            throws Exception {
        // 控制帧编码
        if (msg instanceof ControlFrame) {
            ControlFrame frame = (ControlFrame) msg;
            int estimatedLength = 64;
            if (frame instanceof HeaderStreamFrame) {
                Map<String, String> headers = ((HeaderStreamFrame) frame).getHeaders();
                estimatedLength += HeaderUtil.estimatedLength(headers);
            }
            ChannelBuffer buffer = ChannelBuffers.dynamicBuffer(//
                    estimatedLength, channel.getConfig().getBufferFactory());
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

            if (logger.isDebugEnabled()) {
                logger.debug("estimatedLength=" + estimatedLength + " actual="
                        + buffer.readableBytes());
                logger.debug("writing " + frame);
            }
            return buffer;
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
