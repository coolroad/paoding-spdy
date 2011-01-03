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

import java.util.zip.DataFormatException;

import net.paoding.spdy.common.frame.frames.ControlFrame;
import net.paoding.spdy.common.frame.frames.DataFrame;
import net.paoding.spdy.common.frame.frames.FlaterConfigurable;
import net.paoding.spdy.common.frame.frames.Ping;
import net.paoding.spdy.common.frame.frames.RstStream;
import net.paoding.spdy.common.frame.frames.SpdyFrame;
import net.paoding.spdy.common.frame.frames.SynReply;
import net.paoding.spdy.common.frame.frames.SynStream;
import net.paoding.spdy.common.frame.util.ControlFrameUtil;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

/**
 * 基于SimpleChannelUpstreamHandler实现的spdy帧解码器
 * <p>
 * 对DataFrame的解码上，尽量不再copy 已有的buffer到dataFrame的data上
 * 
 * @author qieqie.wang@gmail.com
 * @author weibo.leo@gmail.com
 */
public class FrameDecoder extends SimpleChannelUpstreamHandler {

    private static Log logger = LogFactory.getLog(FrameDecoder.class);

    private final boolean debugEnabled;

    // messageReceived时用于decode的buffer,messageReceived完毕后buffer归null
    private ChannelBuffer buffer;

    // historyBuffer是一个动态的buffer，messageReceived剩下的buffer将被保存到historyBuffer
    // 新的messageReceived的读入input将合并到historyBuffer中，以供decode
    private ChannelBuffer historyBuffer;

    private ChannelConfig config;

    public FrameDecoder(ChannelConfig config) {
        this.config = config;
        this.debugEnabled = logger.isDebugEnabled();
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
        if (debugEnabled) {
            logger.debug("received: " + input);
        }
        if (historyBuffer != null && historyBuffer.readable()) {
            historyBuffer = ChannelBuffers.wrappedBuffer(historyBuffer, input);
            buffer = historyBuffer;
            if (debugEnabled) {
                logger.debug("wrappedBuffer: " + historyBuffer);
            }

        } else {
            buffer = input;
        }
        while (true) {
            // 如果buffer被直接作为frame内部使用，此时buffer将被设置为null
            if (buffer == null || !buffer.readable()) {
                break;
            }
            // 连head都不够，累积到历史buffer中
            if (buffer.readableBytes() < 8) {
                saveAsHistory(ctx);
                break;
            }
            //
            int markReader = buffer.readerIndex();
            SpdyFrame frame = decode(ctx);
            if (frame == null) {
                // 无法解出东西，累积到历史buffer中
                buffer.readerIndex(markReader);
                saveAsHistory(ctx);
                break;
            } else {
                Channels.fireMessageReceived(ctx, frame, e.getRemoteAddress());
            }
        }
    }

    private void saveAsHistory(ChannelHandlerContext ctx) {
        if (buffer == null || buffer == historyBuffer) {
            return;
        }
        historyBuffer = buffer;
        buffer = null;
    }

    private SpdyFrame decode(ChannelHandlerContext ctx) throws Exception {
        int first = buffer.readByte();
        buffer.readerIndex(buffer.readerIndex() - 1);
        SpdyFrame frame;
        if (first < 0) {
            frame = decodeControlFrame(ctx);
        } else {
            frame = decodeDataFrame(ctx);
        }
        return frame;
    }

    private ControlFrame decodeControlFrame(ChannelHandlerContext ctx) throws Exception {
        int version = ControlFrameUtil.extractVersion(buffer.readShort());
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
        int readerIndex = buffer.readerIndex();
        try {
            frame.decodeData(buffer, length);
        } catch (DataFormatException e) {
            if (config.usingFlater) {
                logger.error(
                        "dataFormateException happened, now we change usingFlater to false for client "
                                + ctx.getChannel().getRemoteAddress(), e);
                config.usingFlater = false;
                ((FlaterConfigurable) frame).setUsingFlater(false);
                buffer.readerIndex(readerIndex);
                frame.decodeData(buffer, length);
            } else {
                throw e;
            }
        }
        if (buffer.readableBytes() != expectedExceed) {
            throw new IllegalStateException("expected read " + length + " bytes, actuall "
                    + (length - buffer.readableBytes() + expectedExceed));
        }
        if (debugEnabled) {
            logger.debug("ControlFrame: " + frame);
        }
        return frame;
    }

    private DataFrame decodeDataFrame(ChannelHandlerContext ctx) {
        final ChannelBuffer _buffer = this.buffer;
        final ChannelBuffer _historyBuffer = this.historyBuffer;
        int streamId = _buffer.readInt();
        int flags = _buffer.readByte();
        int length = _buffer.readUnsignedMedium();
        if (_buffer.readableBytes() < length) {
            return null;
        }
        DataFrame frame = new DataFrame();
        frame.setStreamId(streamId);
        frame.setFlags(flags);
        frame.setChannel(ctx.getChannel());
        // historyBuffer == buffer表示前一次messageReceived还有些数据没解完
        if (_historyBuffer == _buffer) {
            // 如果historyBuffer刚好只包含一个这个frame的数据，那就直接用，不copy！
            if (_historyBuffer.readableBytes() == length) {
                frame.setData(_buffer);
                historyBuffer = null;
                buffer = null;
                if (debugEnabled) {
                    logger.debug("DataFrame(by cumulation.direct): " + frame);
                }
                return frame;
            }
            // 否则就copy前length部分的数据
            else {
                frame.setData(_buffer.copy(_buffer.readerIndex(), length));
                _buffer.skipBytes(length);
                if (debugEnabled) {
                    logger.debug("DataFrame(by cumulation.copy): " + frame);
                }
                return frame;
            }
        }
        // 否则表示没有历史牵绊，buffer就是本次messageReceived最新接收的
        else {
            if (_buffer.readableBytes() == length) {
                frame.setData(_buffer);
                buffer = null;
                if (debugEnabled) {
                    logger.debug("DataFrame(by input.direct): " + frame);
                }
                return frame;
            } else {
                frame.setData(_buffer.slice(_buffer.readerIndex(), length));
                _buffer.skipBytes(length);
                if (debugEnabled) {
                    logger.debug("DataFrame(by input.slice): " + frame);
                }
                return frame;
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    private ControlFrame newControlFrame(int type) {
        ControlFrame frame;
        switch (type) {
            case SynStream.TYPE:
                frame = new SynStream();
                break;
            case SynReply.TYPE:
                frame = new SynReply();
                break;
            case RstStream.TYPE:
                frame = new RstStream();
                break;
            case Ping.TYPE:
                frame = new Ping();
                break;
            default:
                throw new IllegalArgumentException("invalid control type " + type);
        }
        if (frame instanceof FlaterConfigurable) {
            ((FlaterConfigurable) frame).setUsingFlater(config.usingFlater);
        }
        return frame;
    }
}
