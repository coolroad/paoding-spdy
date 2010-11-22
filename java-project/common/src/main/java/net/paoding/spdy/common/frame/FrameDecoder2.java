package net.paoding.spdy.common.frame;

import net.paoding.spdy.common.frame.frames.ControlFrame;
import net.paoding.spdy.common.frame.frames.DataFrame;
import net.paoding.spdy.common.frame.frames.Ping;
import net.paoding.spdy.common.frame.frames.RstStream;
import net.paoding.spdy.common.frame.frames.SpdyFrame;
import net.paoding.spdy.common.frame.frames.SynReply;
import net.paoding.spdy.common.frame.frames.SynStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;

/**
 * spdy帧解码器
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class FrameDecoder2 extends FrameDecoder {

    private static Log logger = LogFactory.getLog(FrameDecoder2.class);

    @Override
    protected SpdyFrame decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer,
            boolean isNewBuffer) throws Exception {
        if (buffer.readableBytes() < 8) {
            return null;
        }
        buffer.markReaderIndex();
        int first = buffer.readByte();
        buffer.resetReaderIndex();
        if (first < 0) {
            // control frame
            return decodeControlFrame(channel, buffer);
        } else {
            // data frame
            return decodeDataFrame(channel, buffer, isNewBuffer);
        }
    }

    private ControlFrame decodeControlFrame(Channel channel, ChannelBuffer buffer) {
        int version = -buffer.readShort();
        if (version != 1) {
            throw new IllegalArgumentException("version should be 1");
        }
        int type = buffer.readUnsignedShort();
        int flags = buffer.readByte();
        int length = buffer.readUnsignedMedium();
        if (buffer.readableBytes() < length) {
            buffer.resetReaderIndex();
            return null;
        }
        ControlFrame frame = newControlFrame(type);
        frame.setFlags(flags);
        frame.setChannel(channel);
        frame.decodeData(buffer);
        if (logger.isDebugEnabled()) {
            logger.debug("decoded control frame: " + frame);
        }
        return frame;
    }

    private DataFrame decodeDataFrame(Channel channel, ChannelBuffer buffer, boolean isNewBuffer) {
        int streamId = buffer.readInt();
        int flags = buffer.readByte();
        int length = buffer.readUnsignedMedium();
        if (buffer.readableBytes() < length) {
            buffer.resetReaderIndex();
            //            System.out.println("FrameDecoder.decodeDataFrame: resetReaderIndex "
            //                    + channel.getRemoteAddress() + " " + buffer);
            return null;
        }
        DataFrame frame = new DataFrame();
        frame.setStreamId(streamId);
        frame.setFlags(flags);
        frame.setChannel(channel);
        if (isNewBuffer && buffer.readableBytes() == length) {
            frame.setData(buffer);
            if (logger.isDebugEnabled()) {
                logger.debug("dataFrame.setData: using the input buffer");
            }
        } else {
            frame.setData(buffer.copy(buffer.readerIndex(), length));
            if (logger.isDebugEnabled()) {
                logger.debug("dataFrame.setData: copy of the input buffer");
            }
        }
        buffer.skipBytes(length);
        //        System.out.println("FrameDecoder.decodeDataFrame: got "
        //                + channel.getRemoteAddress()
        //                + " "
        //                + new String(frame.getData().array(), frame.getData().readerIndex(), frame
        //                        .getData().readableBytes()));
        return frame;
    }

    private ControlFrame newControlFrame(int type) {
        switch (type) {
            case SynStream.TYPE:
                return new SynStream();
            case SynReply.TYPE:
                return new SynReply();
            case RstStream.TYPE:
                return new RstStream();
            case Ping.TYPE:
                return new Ping();
            default:
                throw new IllegalArgumentException("invalid frame type " + type);
        }
    }

}
