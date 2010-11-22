package net.paoding.spdy.client.impl;

import java.util.Map;
import java.util.concurrent.Executor;

import net.paoding.spdy.common.frame.frames.DataFrame;
import net.paoding.spdy.common.frame.frames.SpdyFrame;
import net.paoding.spdy.common.frame.frames.StreamFrame;
import net.paoding.spdy.common.frame.frames.SynReply;
import net.paoding.spdy.common.frame.frames.SynStream;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;

@Sharable
public class ResponseExecution extends OneToOneDecoder {

    private SpdyConnector connection;

    private Executor executor;

    public ResponseExecution(Executor executor, SpdyConnector spdyConnection) {
        this.executor = executor;
        this.connection = spdyConnection;
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, Object msg)
            throws Exception {
        if (msg instanceof StreamFrame) {
            int streamId = ((StreamFrame) msg).getStreamId();
            HttpResponseFuture responseFuture = connection.httpRequests.get(streamId);
            if (msg instanceof SynStream) {
                SynStream syn = (SynStream) msg;
                if (syn.getFlags() == SpdyFrame.FLAG_UNIDIRECTIONAL) {
                    final SubscriptionImpl sub = connection.subscriptions
                            .get(syn.getAssociatedId());
                    if (sub != null) {
                        HttpResponseFuture future = new HttpResponseFuture(connection, sub, false);
                        future.addListener(sub.listener);
                        connection.httpRequests.put(streamId, future);
                        doWithSynStream(future, syn);
                        return null;
                    }
                }
            }
            responseFuture = connection.httpRequests.get(streamId);
            if (responseFuture == null) {
                return null;
            }
            if (msg instanceof SynReply) {
                doWithSynReply(responseFuture, (SynReply) msg);
            } else if (msg instanceof DataFrame) {
                doWithDataFrame(responseFuture, (DataFrame) msg);
            }
            return null;
        } else {
            return msg;
        }
    }

    private void doWithDataFrame(final HttpResponseFuture responseFuture, DataFrame frame) {
        HttpResponse response = responseFuture.getTarget();
        ChannelBuffer content = response.getContent();
        if (frame.getData().readable()) {
            if (content.array().length == 0) {
                response.setContent(frame.getData());
            } else {
                content = ChannelBuffers.wrappedBuffer(content, frame.getData());
                response.setContent(content);
            }
        }
        if (frame.getFlags() == SpdyFrame.FLAG_FIN) {
            connection.httpRequests.remove(frame.getStreamId());
            executor.execute(new Runnable() {

                @Override
                public void run() {
                    responseFuture.setSuccess();
                }
            });
        }
    }

    private void doWithSynReply(final HttpResponseFuture responseFuture, SynReply reply) {
        HttpVersion version = HttpVersion.valueOf(reply.getHeader("version"));
        String statusline = reply.getHeader("status");
        int statusCode;
        String reasonPhrase;
        if (statusline.length() > 4) {
            statusCode = Integer.parseInt(statusline.substring(0, 3));
            reasonPhrase = statusline.substring(4);
        } else {
            statusCode = Integer.parseInt(statusline.substring(0, 3));
            reasonPhrase = "";
        }
        HttpResponseStatus status = new HttpResponseStatus(statusCode, reasonPhrase);
        HttpResponse response = new DefaultHttpResponse(version, status);
        responseFuture.setTarget(response);
        for (Map.Entry<String, String> header : reply.getHeaders().entrySet()) {
            String name = header.getKey();
            if (name.equals("version") || name.equals("status")) {
                continue;
            }
            response.setHeader(name, header.getValue());
        }
        if (reply.getFlags() == SpdyFrame.FLAG_FIN) {
            connection.httpRequests.remove(reply.getStreamId());
            executor.execute(new Runnable() {

                @Override
                public void run() {
                    responseFuture.setSuccess();
                }
            });
        }
    }

    private void doWithSynStream(final HttpResponseFuture responseFuture, SynStream syn) {
        HttpResponseStatus status = new HttpResponseStatus(200, "OK");
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);
        responseFuture.setTarget(response);
        for (Map.Entry<String, String> header : syn.getHeaders().entrySet()) {
            String name = header.getKey();
            if (name.equals("version") || name.equals("status")) {
                continue;
            }
            response.setHeader(name, header.getValue());
        }
        if (syn.getFlags() == SpdyFrame.FLAG_FIN) {
            connection.httpRequests.remove(syn.getStreamId());
            executor.execute(new Runnable() {

                @Override
                public void run() {
                    responseFuture.setSuccess();
                }
            });
        }
    }
}
