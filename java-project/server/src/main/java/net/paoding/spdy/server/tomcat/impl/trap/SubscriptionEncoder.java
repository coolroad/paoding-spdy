package net.paoding.spdy.server.tomcat.impl.trap;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import net.paoding.spdy.common.frame.frames.DataFrame;
import net.paoding.spdy.common.frame.frames.SpdyFrame;
import net.paoding.spdy.common.frame.frames.SynStream;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

public class SubscriptionEncoder extends SimpleChannelHandler {

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if (e.getMessage() instanceof SpdyMessage) {
            SpdyMessage msg = (SpdyMessage) e.getMessage();
            SynStream synStream = new SynStream();
            synStream.setChannel(e.getChannel());
            Map<String, String> headers = new HashMap<String, String>();
            headers.put("url", msg.getUrl());
            synStream.setHeaders(headers);
            synStream.setAssociatedId(msg.getAssociatedId());
            synStream.setFlags(SpdyFrame.FLAG_UNIDIRECTIONAL);
            int streamId = msg.getStreamId();
            synStream.setStreamId(streamId);
            Channels.write(synStream.getChannel(), synStream);
            //
            InputStream in = msg.getMessage().getInputStream();
            DataFrame dataFrame = new DataFrame();
            dataFrame.setChannel(synStream.getChannel());
            dataFrame.setStreamId(streamId);
            dataFrame.setFlags(SpdyFrame.FLAG_FIN);
            if (in != null) {
                int available = in.available();
                ChannelBuffer buffer;
                if (available >= 0) {
                    buffer = ChannelBuffers.buffer(available);
                    buffer.writeBytes(in, available);
                } else {
                    buffer = ChannelBuffers.dynamicBuffer();
                    while (-1 == buffer.writeBytes(in, buffer.writableBytes())) {
                        break;
                    }
                }
                dataFrame.setData(buffer);
            }
            Channels.write(synStream.getChannel(), dataFrame);
        } else {
            ctx.sendDownstream(e);
        }
    }

}
