/*
 * Copyright 2010-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License i distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.paoding.spdy.server.tomcat.impl.supports;

import java.io.IOException;

import net.paoding.spdy.common.frame.frames.DataFrame;
import net.paoding.spdy.common.frame.frames.SpdyFrame;
import net.paoding.spdy.common.frame.frames.SynStream;
import net.paoding.spdy.server.tomcat.impl.hook.ClientFlush;
import net.paoding.spdy.server.tomcat.impl.hook.Close;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.coyote.ActionCode;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.ByteChunk;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channels;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class SpdyOutputBuffer implements OutputBuffer {

    private static Log logger = LogFactory.getLog(SpdyOutputBuffer.class);

    // 延迟发送的data
    private ChannelBuffer delay;

    public SpdyOutputBuffer() {
    }

    private int maxSize = Integer.MAX_VALUE - 8; // dataFrame.head.size=8

    private int minSize = maxSize;

    @Override
    public int doWrite(ByteChunk chunk, Response response) throws IOException {
        int chunkLength = chunk.getLength();
        if (chunkLength <= 0) {
            return 0;
        }
        ChannelBuffer old = this.delay;
        int offset = chunk.getStart();
        while (chunkLength > 0) {
            int part = Math.min(chunkLength, old == null ? maxSize : maxSize - old.readableBytes());
            delay = ChannelBuffers.wrappedBuffer(chunk.getBuffer(), offset, part);
            if (old != null) {
                delay = ChannelBuffers.wrappedBuffer(old, delay);
                old = null;
            }
            if (delay.readableBytes() >= minSize) {
                flush(response);
            }
            chunkLength -= part;
            offset += part;
        }
        return chunk.getLength();
    }

    /**
     * {@link ClientFlush}
     * 
     * @param response
     */
    public void flush(Response response) {
        flush(response, false);
    }

    private void flush(Response response, boolean last) {
        if (delay != null) {
            if (!response.isCommitted()) {
                response.action(ActionCode.ACTION_COMMIT, null);
            }
            ChannelBuffer buffer = this.delay;
            this.delay = null;
            DataFrame frame = createDataFrame(response, buffer);
            final boolean debugEnabled = logger.isDebugEnabled();
            if (last) {
                frame.setFlags(SpdyFrame.FLAG_FIN);
                CoyoteAttributes.setFinished(response);
                if (logger.isInfoEnabled()) {
                    logger.info("fin response (by last): " + response.getRequest());
                }
            }
            if (debugEnabled) {
                logger.debug("flush buffer: " + frame);
            }
            Channels.write(frame.getChannel(), frame);
        }
    }

    /**
     * {@link Close}
     * 
     * @param response
     */
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
        this.delay = null;
    }

    private DataFrame createDataFrame(Response response, ChannelBuffer data) {
        DataFrame frame = new DataFrame();
        SynStream synStream = CoyoteAttributes.getSynStream(response.getRequest());
        frame.setStreamId(synStream.getStreamId());
        frame.setChannel(synStream.getChannel());
        frame.setData(data);
        return frame;
    }

}
