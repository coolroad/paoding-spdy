package net.paoding.spdy.server.tomcat.impl.supports;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import net.paoding.spdy.common.frame.frames.DataFrame;
import net.paoding.spdy.common.frame.frames.SpdyFrame;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.tomcat.util.buf.ByteChunk;
import org.jboss.netty.buffer.ChannelBuffer;

public class SpdyInputBuffer implements InputBuffer {

    private List<DataFrame> dataFrames;

    private boolean flagFin = false;

    private boolean reset = false;

    public SpdyInputBuffer(SpdyFrame syn) {
        if (syn.getFlags() == SpdyFrame.FLAG_FIN) {
            flagFin = true;
        }
    }

    public synchronized void addDataFrame(DataFrame dataFrame) {
        if (flagFin) {
            throw new IllegalStateException();
        }
        if (dataFrames == null) {
            dataFrames = new LinkedList<DataFrame>();
        }
        this.dataFrames.add(dataFrame);
        if (dataFrame.getFlags() == SpdyFrame.FLAG_FIN) {
            flagFin = true;
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
            throw new IOException("reset");
        }
        DataFrame dataFrame = null;
        synchronized (this) {
            if (dataFrames == null || dataFrames.size() == 0) {
                if (flagFin) {
                    return -1;
                } else {
                    return 0;
                }
            }
            dataFrame = dataFrames.remove(0);
        }
        ChannelBuffer frame = dataFrame.getData();
        int readableBytes = frame.readableBytes();
        if (readableBytes == 0) {
            return 0;
        }
        chunk.setBytes(frame.array(), frame.readerIndex(), readableBytes);
        return readableBytes;
    }

    public void reset() {
        reset = true;
    }
}
