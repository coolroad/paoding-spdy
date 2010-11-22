/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
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
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

public class FrameDecoder3 extends SimpleChannelUpstreamHandler {

    private static Log logger = LogFactory.getLog(FrameDecoder3.class);

    private ChannelBuffer cumulation;

    public FrameDecoder3() {
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Object m = e.getMessage();
        if (!(m instanceof ChannelBuffer)) {
            ctx.sendUpstream(e);
            return;
        }

        final ChannelBuffer input = (ChannelBuffer) m;
        if (!input.readable()) {
            return;
        }
        final ChannelBuffer buffer;
        if (cumulation != null && cumulation.readable()) {
            cumulation.discardReadBytes();
            cumulation.writeBytes(input);
            buffer = cumulation;
        } else {
            buffer = input;
        }
        while (true) {
            if (buffer.readableBytes() < 8) {
                if (buffer == input) {
                    if (cumulation == null) {
                        cumulation = ChannelBuffers.dynamicBuffer(//
                                ctx.getChannel().getConfig().getBufferFactory());
                    }
                    cumulation.writeBytes(buffer);
                }
                return;
            }
            buffer.markReaderIndex();
            SpdyFrame frame = decode(ctx, buffer);
            if (frame == null) {
                buffer.resetReaderIndex();
            } else {
                Channels.fireMessageReceived(ctx, frame, e.getRemoteAddress());
            }
        }
    }

    private SpdyFrame decode(ChannelHandlerContext ctx, ChannelBuffer buffer) {
        int first = buffer.readByte();
        buffer.resetReaderIndex();
        SpdyFrame frame;
        if (first < 0) {
            frame = decodeControlFrame(ctx, buffer);
        } else {
            frame = decodeDataFrame(ctx, buffer);
        }
        return frame;
    }

    private ControlFrame decodeControlFrame(ChannelHandlerContext ctx, ChannelBuffer buffer) {
        int version = -buffer.readShort();
        if (version != 1) {
            throw new IllegalArgumentException("version should be 1");
        }
        int type = buffer.readUnsignedShort();
        int flags = buffer.readByte();
        int length = buffer.readUnsignedMedium();
        int expectedExceed;
        if ((expectedExceed = buffer.readableBytes() - length) < 0) {
            return null;
        }
        ControlFrame frame = newControlFrame(type);
        frame.setFlags(flags);
        frame.setChannel(ctx.getChannel());
        frame.decodeData(buffer);
        if (buffer.readableBytes() != expectedExceed) {
            throw new IllegalStateException("expected read " + length + " bytes, actuall "
                    + (length - buffer.readableBytes() + expectedExceed));
        }
        if (logger.isDebugEnabled()) {
            logger.debug("decoded control frame: " + frame);
        }
        return frame;
    }

    private DataFrame decodeDataFrame(ChannelHandlerContext ctx, ChannelBuffer buffer) {
        int streamId = buffer.readInt();
        int flags = buffer.readByte();
        int length = buffer.readUnsignedMedium();
        if (buffer.readableBytes() < length) {
            return null;
        }
        DataFrame frame = new DataFrame();
        frame.setStreamId(streamId);
        frame.setFlags(flags);
        frame.setChannel(ctx.getChannel());
        if (cumulation == buffer) {
            frame.setData(buffer.copy(buffer.readerIndex(), length));
        } else {
            frame.setData(buffer.slice(buffer.readerIndex(), length));
        }
        buffer.skipBytes(length);
        if (logger.isDebugEnabled()) {
            logger.debug("decoded data frame(" //
                    + (cumulation == buffer ? "c" : "s") + "): " + frame);
        }
        return frame;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        ctx.sendUpstream(e);
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
                throw new IllegalArgumentException("invalid control type " + type);
        }
    }
}
