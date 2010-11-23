package net.paoding.spdy.common.frame;

import static org.jboss.netty.channel.Channels.write;

import java.util.Arrays;

import net.paoding.spdy.common.frame.frames.ControlFrame;
import net.paoding.spdy.common.frame.frames.DataFrame;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.buffer.CompositeChannelBuffer;
import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.MessageEvent;

/**
 * spdy帧编码器，配置在netty的 {@link ChannelPipeline}
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
@Sharable
public class FrameEncoder implements ChannelDownstreamHandler {

    protected static Log logger = LogFactory.getLog(FrameEncoder.class);

    public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent evt) throws Exception {
        if (!(evt instanceof MessageEvent)) {
            ctx.sendDownstream(evt);
            return;
        }

        MessageEvent e = (MessageEvent) evt;
        if (encode(ctx, e, e.getMessage())) {
            // encoded
        } else {
            ctx.sendDownstream(evt);
        }
    }

    protected boolean encode(ChannelHandlerContext ctx, MessageEvent e, Object msg)
            throws Exception {
        // 控制帧编码
        if (msg instanceof ControlFrame) {
            ControlFrame frame = (ControlFrame) msg;
            ChannelBuffer buffer = ChannelBuffers.dynamicBuffer(//
                    1024, e.getChannel().getConfig().getBufferFactory());
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
                logger.debug("writing " + frame);
            }
            write(ctx, e.getFuture(), buffer, e.getRemoteAddress());
            return true;
        }
        // 数据帧编码
        else if (msg instanceof DataFrame) {
            DataFrame frame = (DataFrame) msg;
            ChannelBuffer data = frame.getData();
            ChannelBuffer head = ChannelBuffers.buffer(//
                    e.getChannel().getConfig().getBufferFactory().getDefaultOrder(), 8);
            int dataLength = data.readableBytes();
            head.writeInt(frame.getStreamId());// streamId肯定是正数，于是能够保证control bit为0
            head.writeByte(frame.getFlags());
            head.writeMedium(dataLength); // length
            if (logger.isDebugEnabled()) {
                logger.debug("writing " + frame);
            }
            if (dataLength == 0) {
                write(ctx, e.getFuture(), head, e.getRemoteAddress());
            } else {
                ChannelBuffer buffer = new CompositeChannelBuffer(//
                        head.order(), Arrays.asList(head, data));
                write(ctx, e.getFuture(), buffer, e.getRemoteAddress());
            }
            return true;
        }
        //
        else {
            return false;
        }
    }

}
