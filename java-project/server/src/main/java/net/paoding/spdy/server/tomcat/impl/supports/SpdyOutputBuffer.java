package net.paoding.spdy.server.tomcat.impl.supports;

import java.io.IOException;

import net.paoding.spdy.common.frame.frames.DataFrame;
import net.paoding.spdy.common.frame.frames.SpdyFrame;
import net.paoding.spdy.common.frame.frames.SynStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.ByteChunk;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channels;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class SpdyOutputBuffer implements OutputBuffer {

    private static Log logger = LogFactory.getLog(SpdyOutputBuffer.class);

    // 内容字节数
    private int all;

    // 缓存
    private ChannelBuffer buffer;

    private final int bufferSize;

    private final ChannelBufferFactory channelBufferFactory;

    public SpdyOutputBuffer(ChannelBufferFactory channelBufferFactory, int bufferSize) {
        this.bufferSize = bufferSize;
        this.channelBufferFactory = channelBufferFactory;
    }

    @Override
    public int doWrite(ByteChunk chunk, Response response) throws IOException {
        int chunkLength = chunk.getLength();
        if (chunkLength <= 0) {
            return 0;
        }
        final boolean debugEnabled = logger.isDebugEnabled();
        if (this.buffer != null) {
            if (this.buffer.writableBytes() < chunkLength) {
                if (debugEnabled) {
                    logger.debug("flushing before write");
                }
                flush(response);
            }
        } else {
            newBuffer(chunkLength);
        }
        //
        this.buffer.writeBytes(chunk.getBuffer(), chunk.getStart(), chunkLength);
        all += chunkLength;
        if (debugEnabled) {
            logger.debug("append to buffer: chunk=" + chunkLength + ", buffer="
                    + this.buffer.readableBytes() + "; all=" + all);
        }
        if (!buffer.writable()) {
            flush(response);
        }
        return chunkLength;
    }

    public void flush(Response response) {
        flush(response, false);
    }

    private void flush(Response response, boolean last) {
        if (buffer != null) {
            ChannelBuffer buffer = this.buffer;
            this.buffer = null;
            DataFrame frame = createDataFrame(response, buffer);
            final boolean debugEnabled = logger.isDebugEnabled();
            if (last) {
                frame.setFlags(SpdyFrame.FLAG_FIN);
                CoyoteAttributes.setFinished(response);
                if (logger.isInfoEnabled()) {
                    logger.info("fin response (by last): " + response.getRequest());
                }
            } else {
                setFinishedIfNeccessary(response, debugEnabled, frame);
            }
            if (debugEnabled) {
                logger.debug("flush buffer: " + frame);
            }
            Channels.write(frame.getChannel(), frame);
        }
    }

    public void close(Response response) {
        flush(response, true);
        if (!CoyoteAttributes.isFinished(response)) {
            if (logger.isInfoEnabled()) {
                logger.info("fin response (by close): " + response.getRequest());
            }
            CoyoteAttributes.setFinished(response);
            DataFrame frame = new DataFrame();
            frame.setChannel(CoyoteAttributes.getChannel(response));
            frame.setStreamId(CoyoteAttributes.getStreamId(response));
            frame.setFlags(SpdyFrame.FLAG_FIN);
            Channels.write(frame.getChannel(), frame);
        }
    }

    public void reset() {
        this.buffer = null;
    }

    private final void newBuffer(int minLimit) {
        if (logger.isDebugEnabled()) {
            logger.debug("creating a new buffer");
        }
        buffer = ChannelBuffers.buffer(//
                channelBufferFactory.getDefaultOrder(), Math.max(minLimit, bufferSize));
    }

    private DataFrame createDataFrame(Response response, ChannelBuffer data) {
        DataFrame frame = new DataFrame();
        SynStream synStream = CoyoteAttributes.getSynStream(response.getRequest());
        frame.setStreamId(synStream.getStreamId());
        frame.setChannel(synStream.getChannel());
        frame.setData(data);
        return frame;
    }

    private void setFinishedIfNeccessary(Response response, final boolean debugEnabled,
            DataFrame frame) {
        int contentLength = response.getContentLength();
        if (contentLength > 0) { // contentLength==0的情况已经在 Commit.java中处理
            if (all == contentLength) {
                frame.setFlags(SpdyFrame.FLAG_FIN);
                CoyoteAttributes.setFinished(response);
                all = Integer.MIN_VALUE;
                if (debugEnabled) {
                    logger.info("fin response (by length): " + response.getRequest());
                }
            } else if (all > contentLength || all < 0) {
                throw new IllegalArgumentException("wrong contentLegnth=" + contentLength
                        + "; sent=" + all);
            } else {
                if (debugEnabled) {
                    logger.debug(String.format(
                            "setFinishedIfNeccessary: contentLength=%s;  sent=%s", contentLength,
                            all));
                }
            }
        } else {
            if (debugEnabled) {
                logger.debug(String.format("setFinishedIfNeccessary: contentLength=%s;  sent=%s",
                        contentLength, all));
            }
        }
    }

}
