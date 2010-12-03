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
package net.paoding.spdy.client.netty;

import java.util.Map;

import net.paoding.spdy.client.SubscriptionStub;
import net.paoding.spdy.common.frame.frames.DataFrame;
import net.paoding.spdy.common.frame.frames.HeaderStreamFrame;
import net.paoding.spdy.common.frame.frames.SpdyFrame;
import net.paoding.spdy.common.frame.frames.StreamFrame;
import net.paoding.spdy.common.frame.frames.SynReply;
import net.paoding.spdy.common.frame.frames.SynStream;
import net.paoding.spdy.common.http.DefaultHttpResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

/**
 * 接受服务器下发的各种frame，解析为response对象，并提交给executor通知源操作的future告知成功
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
@Sharable
public class ResponseExecution implements ChannelUpstreamHandler {

    private static Log logger = LogFactory.getLog(ResponseExecution.class);

    private NettyConnector connector;

    public ResponseExecution(NettyConnector connector) {
        this.connector = connector;
    }

    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent evt) throws Exception {
        if (!(evt instanceof MessageEvent)) {
            ctx.sendUpstream(evt);
            return;
        }

        MessageEvent e = (MessageEvent) evt;
        Object msg = e.getMessage();
        if (!(msg instanceof StreamFrame)) {
            ctx.sendUpstream(evt);
            return;
        }
        final StreamFrame frame = (StreamFrame) msg;
        final int streamId = frame.getStreamId();
        final ResponseFuture<?, HttpResponse> responseFuture;

        // (客户端)接收到单向的synStream代表有一个server push过来了！
        if (msg instanceof SynStream) {
            responseFuture = doWithSynFrame(streamId, (SynStream) msg);
            if (responseFuture != null) {
                // serverpush时要把future注册到requests中，使得后续的dataframe能够正确塞出来
                // !!放进来到futures的这个future会不会和客户端的future frameId相同，从而互相覆盖而郁闷？
                // 不会!因为服务器端来的streamId和客户端发送的frameId奇偶性质不一样，不会互相覆盖
                connector.requests.put(streamId, responseFuture);
            }
        } else {
            // 
            responseFuture = connector.requests.get(streamId);
            if (responseFuture == null) {
                logger.warn("not found future for " + msg);
            } else {
                if (msg instanceof SynReply) {
                    responseFuture.setResponse(extractResponse((SynReply) msg));
                } else if (msg instanceof DataFrame) {
                    HttpResponse response = responseFuture.get();
                    ChannelBuffer content = response.getContent();
                    ChannelBuffer chunk = ((DataFrame) frame).getData();
                    if (chunk.readable()) {
                        if (content.array().length == 0) {
                            response.setContent(chunk);
                        } else {
                            content = ChannelBuffers.wrappedBuffer(content, chunk);
                            response.setContent(content);
                        }
                    }
                }
            }
        }
        if (responseFuture != null && frame.getFlags() == SpdyFrame.FLAG_FIN) {
            connector.requests.remove(streamId);
            responseFuture.setSuccess();
        }
    }

    private ResponseFuture<SubscriptionStub, HttpResponse> doWithSynFrame(int streamId,
            SynStream syn) {
        if (syn.getFlags() == SpdyFrame.FLAG_UNIDIRECTIONAL) {
            SubscriptionStubImpl subscription = connector.subscriptions.get(syn.getAssociatedId());
            if (subscription != null) {
                ResponseFuture<SubscriptionStub, HttpResponse> f = new ResponseFuture<SubscriptionStub, HttpResponse>(
                        connector, subscription);
                f.addListener(subscription.listener);
                f.setResponse(extractResponse(syn));
                return f;
            } else {
                logger.warn("not found subscription for SynFrame:" + syn + "  url="
                        + syn.getHeader("url"));
            }
        } else {
            logger.warn("wrong SynFrame from server: should be unidirectional " + syn + "  url="
                    + syn.getHeader("url"));
        }
        return null;
    }

    private HttpResponse extractResponse(HeaderStreamFrame frame) {
        HttpResponse response = new DefaultHttpResponse();
        String statusline = frame.getHeader("status");
        if (statusline != null && !"200 OK".equals(statusline)) {
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
            response.setStatus(status);
        }
        for (Map.Entry<String, String> header : frame.getHeaders().entrySet()) {
            String name = header.getKey();
            if (name.equals("version") || name.equals("status")) {
                continue;
            }
            response.setHeader(name, header.getValue());
        }
        return response;
    }

}
