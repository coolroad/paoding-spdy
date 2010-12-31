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
package net.paoding.spdy.common.frame.frames;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Map;
import java.util.zip.DataFormatException;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;

/**
 * SYN_REPLY控制帧
 * 
 * @author qieqie.wang
 * 
 */
public class SynReply extends ControlFrame implements HeaderStreamFrame, FlaterConfigurable {

    /** SYN_REPLY的类型值 */
    public static final int TYPE = 2;

    protected int streamId;

    private Map<String, String> headers = Collections.emptyMap();

    /**
     * 
     */
    public SynReply() {
        super(TYPE);
    }

    public int getStreamId() {
        return streamId;
    }

    public void setStreamId(int streamId) {
        this.streamId = streamId;
    }

    @Override
    public Map<String, String> getHeaders() {
        return headers;
    }

    @Override
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    @Override
    public String getHeader(String name) {
        return headers.get(name);
    }

    private boolean usingDecompressing = true;

    @Override
    public void setUsingFlater(boolean usingDecompressing) {
        this.usingDecompressing = usingDecompressing;
    }

    @Override
    public void decodeData(ChannelBuffer buffer, int length) throws DataFormatException {
        this.streamId = Math.abs(buffer.readInt());
        buffer.skipBytes(2); // Unused
        this.headers = HeaderUtil.decode(buffer, length - 6, usingDecompressing);
    }

    @Override
    public ChannelBuffer encodeData(ChannelBufferFactory factory) {
        ChannelBuffer buffer = HeaderUtil.encode(6, headers, usingDecompressing, factory);
        int writerIndex = buffer.writerIndex();
        buffer.writerIndex(0);
        buffer.writeInt(streamId);
        buffer.writeShort(0);
        buffer.writerIndex(writerIndex);
        return buffer;
    }

    @Override
    public String toString() {
        return String.format(
                "SynRely[streamId=%s, flags=%s, headers.size=%s, timestamp=%s, deflate=%s]",
                streamId, flags, headers.size(),
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(getTimestamp()),
                usingDecompressing);
    }

}
