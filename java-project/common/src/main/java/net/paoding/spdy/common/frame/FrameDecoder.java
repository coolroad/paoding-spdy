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

import java.net.SocketAddress;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

public abstract class FrameDecoder extends SimpleChannelUpstreamHandler {

    private ChannelBuffer cumulation;

    protected FrameDecoder() {
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {

        Object m = e.getMessage();
        if (!(m instanceof ChannelBuffer)) {
            ctx.sendUpstream(e);
            return;
        }

        // 每一次的input都是全新的input
        final ChannelBuffer input = (ChannelBuffer) m;
        if (!input.readable()) {
            return;
        }

        ChannelBuffer cumulation = cumulation(ctx);
        if (cumulation.readable()) {
            cumulation.discardReadBytes();
            cumulation.writeBytes(input);
            callDecode(ctx, e.getChannel(), cumulation, e.getRemoteAddress(), false);
        } else {
            callDecode(ctx, e.getChannel(), input, e.getRemoteAddress(), true);
            if (input.readable()) {
                cumulation.writeBytes(input);
            }
        }
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        cleanup(ctx, e);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        cleanup(ctx, e);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    /**
     * Decodes the received packets so far into a frame.
     * 
     * @param ctx the context of this handler
     * @param channel the current channel
     * @param buffer the cumulative buffer of received packets so far. Note
     *        that the buffer might be empty, which means you should not
     *        make an assumption that the buffer contains at least one byte
     *        in your decoder implementation.
     * 
     * @return the decoded frame if a full frame was received and decoded.
     *         {@code null} if there's not enough data in the buffer to
     *         decode a frame.
     */
    protected abstract Object decode(ChannelHandlerContext ctx, Channel channel,
            ChannelBuffer buffer, boolean isNewBuffer) throws Exception;

    /**
     * Decodes the received data so far into a frame when the channel is
     * disconnected.
     * 
     * @param ctx the context of this handler
     * @param channel the current channel
     * @param buffer the cumulative buffer of received packets so far. Note
     *        that the buffer might be empty, which means you should not
     *        make an assumption that the buffer contains at least one byte
     *        in your decoder implementation.
     * 
     * @return the decoded frame if a full frame was received and decoded.
     *         {@code null} if there's not enough data in the buffer to
     *         decode a frame.
     */
    protected Object decodeLast(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer)
            throws Exception {
        return decode(ctx, channel, buffer, true);
    }

    private void callDecode(ChannelHandlerContext context, Channel channel,
            ChannelBuffer cumulation, SocketAddress remoteAddress, boolean isNewBuffer) throws Exception {

        while (cumulation.readable()) {
            int oldReaderIndex = cumulation.readerIndex();
            Object frame = decode(context, channel, cumulation, isNewBuffer);
            isNewBuffer = false;
            if (frame == null) {
                if (oldReaderIndex == cumulation.readerIndex()) {
                    // Seems like more data is required.
                    // Let us wait for the next notification.
                    break;
                } else {
                    // Previous data has been discarded.
                    // Probably it is reading on.
                    continue;
                }
            } else if (oldReaderIndex == cumulation.readerIndex()) {
                throw new IllegalStateException("decode() method must read at least one byte "
                        + "if it returned a frame (caused by: " + getClass() + ")");
            }

            Channels.fireMessageReceived(context, frame, remoteAddress);
        }
    }

    private void cleanup(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        try {
            ChannelBuffer cumulation = this.cumulation;
            if (cumulation == null) {
                return;
            } else {
                this.cumulation = null;
            }

            if (cumulation.readable()) {
                // Make sure all frames are read before notifying a closed channel.
                callDecode(ctx, ctx.getChannel(), cumulation, null, true);
            }

            // Call decodeLast() finally.  Please note that decodeLast() is
            // called even if there's nothing more to read from the buffer to
            // notify a user that the connection was closed explicitly.
            Object partialFrame = decodeLast(ctx, ctx.getChannel(), cumulation);
            if (partialFrame != null) {
                Channels.fireMessageReceived(ctx, partialFrame, null);
            }
        } finally {
            ctx.sendUpstream(e);
        }
    }

    private ChannelBuffer cumulation(ChannelHandlerContext ctx) {
        ChannelBuffer c = cumulation;
        if (c == null) {
            c = ChannelBuffers.dynamicBuffer(ctx.getChannel().getConfig().getBufferFactory());
            cumulation = c;
        }
        return c;
    }
}
