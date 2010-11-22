package net.paoding.spdy.server.tomcat.impl.supports;

import java.io.IOException;

import net.paoding.spdy.common.frame.frames.DataFrame;
import net.paoding.spdy.common.frame.frames.SpdyFrame;

import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.ByteChunk;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channels;

public class SpdyOutputBuffer implements OutputBuffer {

    private int sent;

    public SpdyOutputBuffer() {
    }

    @Override
    public int doWrite(ByteChunk chunk, Response response) throws IOException {
        int chunkLength = chunk.getLength();
        if (chunkLength <= 0) {
            return 0;
        }
        ChannelBuffer buffer = ChannelBuffers.buffer(chunkLength);
        buffer.writeBytes(chunk.getBuffer(), chunk.getStart(), chunkLength);
        DataFrame frame = new DataFrame();
        frame.setStreamId(CoyoteAttributes.getStreamId(response));
        frame.setChannel(CoyoteAttributes.getChannel(response));
        int contentLength = response.getContentLength();
        if (contentLength > 0) {
            sent += chunkLength;
            if (sent == contentLength) {
                frame.setFlags(SpdyFrame.FLAG_FIN);
                CoyoteAttributes.setFinished(response);
                sent = -1;
            } else if (sent > contentLength) {
                throw new IllegalArgumentException("wrong contentLegnth=" + contentLength
                        + "; sent=" + sent);
            }
        }
        frame.setData(buffer);
        Channels.write(frame.getChannel(), frame);
        return chunkLength;
    }

}
