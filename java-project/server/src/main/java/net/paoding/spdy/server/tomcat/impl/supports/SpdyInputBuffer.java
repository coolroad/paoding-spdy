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
import java.util.LinkedList;
import java.util.List;

import net.paoding.spdy.common.frame.frames.DataFrame;
import net.paoding.spdy.common.frame.frames.SpdyFrame;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.jboss.netty.buffer.ChannelBuffer;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class SpdyInputBuffer implements InputBuffer {

    protected static Log logger = LogFactory.getLog(SpdyInputBuffer.class);

    /**
     * 还未被读的dataFrames
     */
    private List<DataFrame> unreadDataFrames;

    private boolean flagFin = false;

    private boolean reset = false;

    public SpdyInputBuffer(SpdyFrame syn) {
        if (syn.getFlags() == SpdyFrame.FLAG_FIN) {
            flagFin = true;
        }
    }

    public void addDataFrame(DataFrame dataFrame) {
        synchronized (this) {
            if (flagFin) {
                throw new IllegalStateException("finished");
            }
            if (unreadDataFrames == null) {
                unreadDataFrames = new LinkedList<DataFrame>();
            }
            this.unreadDataFrames.add(dataFrame);
            if (dataFrame.getFlags() == SpdyFrame.FLAG_FIN) {
                flagFin = true;
            }
        }
    }

    public boolean isFlagFin() {
        return flagFin;
    }

    public void setFlagFin(boolean flagFin) {
        this.flagFin = flagFin;
    }

    @Override
    public int doRead(ByteChunk chunk, Request request) throws IOException {
        if (reset) {
            // 通知调用者，该请求已经被reset
            throw new IOException("has been reset");
        }
        DataFrame dataFrame;
        synchronized (this) {
            if (unreadDataFrames == null || unreadDataFrames.size() == 0) {
                if (flagFin) {
                    // end of buffer
                    return -1;
                } else {
                    // read zero 
                    return 0;
                }
            }
            dataFrame = unreadDataFrames.remove(0);
        }
        ChannelBuffer data = dataFrame.getData();
        if (logger.isDebugEnabled()) {
            logger.debug("reading " + dataFrame);
        }
        int readableBytes = data.readableBytes();
        if (readableBytes == 0) {
            return 0;
        }
        if (data.hasArray()) {
            chunk.setBytes(data.array(), data.arrayOffset() + data.readerIndex(), readableBytes);
        } else {
            byte[] dst = new byte[readableBytes];
            data.readBytes(dst);
            chunk.setBytes(dst, 0, dst.length);
        }
        return readableBytes;
    }

    public void reset() {
        reset = true;
    }
}
