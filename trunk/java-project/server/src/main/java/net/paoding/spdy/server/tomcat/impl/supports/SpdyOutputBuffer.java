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

import javax.servlet.http.HttpServletResponse;

import net.paoding.spdy.common.frame.frames.DataFrame;
import net.paoding.spdy.common.frame.frames.SpdyFrame;
import net.paoding.spdy.common.frame.frames.SynStream;
import net.paoding.spdy.server.tomcat.impl.hook.ClientFlush;
import net.paoding.spdy.server.tomcat.impl.hook.Close;

import org.apache.catalina.connector.CoyoteAdapter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.coyote.ActionCode;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.ByteChunk;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.Channels;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class SpdyOutputBuffer implements OutputBuffer {

    private static Log logger = LogFactory.getLog(SpdyOutputBuffer.class);

    private ChannelBuffer delay;

    private boolean fin = false;

    private final boolean debugEnabled = logger.isDebugEnabled();

    public SpdyOutputBuffer() {
    }

    @Override
    public int doWrite(ByteChunk chunk, Response coyoteResponse) throws IOException {
        int chunkLength = chunk.getLength();
        if (chunkLength <= 0) {
            return 0;
        }
        // @see org.apache.catalina.connector.CoyoteAdapter#service
        HttpServletResponse response = (HttpServletResponse) coyoteResponse
                .getNote(CoyoteAdapter.ADAPTER_NOTES);
        // 满了代表chunk.getBuffer()这个byte数组可能还会被下一个write用
        if (response != null && chunk.getStart() + chunkLength == response.getBufferSize()) {
            byte[] copied = new byte[chunkLength];
            System.arraycopy(chunk.getBuffer(), chunk.getStart(), copied, 0, chunkLength);
            delay = ChannelBuffers.wrappedBuffer(copied);
            if (debugEnabled) {
                logger.debug("writing copied buffer: offset=" + chunk.getStart() + " len="
                        + chunkLength + "  outputBufferSize=" + response.getBufferSize());
            }
            flush(coyoteResponse);
        }
        // 没满就来代表以后不再write了，这是最后一个，可以直接用chunk.buffer这个byte数组了
        else {
            if (fin) {
                throw new Error("spdy buffer error: buffer closed");
            }
            if (debugEnabled) {
                logger.debug("writing writing buffer: offset=" + chunk.getStart() + " len="
                        + chunkLength);
            }
            delay = ChannelBuffers.wrappedBuffer(chunk.getBuffer(), chunk.getStart(), chunkLength);
            flushAndClose(coyoteResponse);
        }
        return chunk.getLength();
    }

    /**
     * {@link ClientFlush}
     * 
     * @param response
     */
    public ChannelFuture flush(Response response) {
        return flush(response, false);
    }

    private ChannelFuture flush(Response response, boolean last) {
        if (delay != null) {
            if (!response.isCommitted()) {
                response.action(ActionCode.ACTION_COMMIT, null);
            }
            ChannelBuffer data = this.delay;
            this.delay = null;
            DataFrame frame = createDataFrame(response, data);
            if (last) {
                frame.setFlags(SpdyFrame.FLAG_FIN);
                fin = true;
                if (logger.isInfoEnabled()) {
                    logger.info("fin response (by last): " + response.getRequest());
                }
            }
            if (debugEnabled) {
                logger.debug("flush buffer: " + frame);
            }
            return Channels.write(frame.getChannel(), frame);
        }
        return null;
    }

    /**
     * {@link Close}
     * 
     * @param response
     */
    public void flushAndClose(Response response) {
        if (fin) {
            return;
        }
        if (delay == null) {
            delay = ChannelBuffers.EMPTY_BUFFER;
        }
        flush(response, true);
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
