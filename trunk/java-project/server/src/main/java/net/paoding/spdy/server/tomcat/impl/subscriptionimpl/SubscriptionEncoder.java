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
package net.paoding.spdy.server.tomcat.impl.subscriptionimpl;

import java.util.HashMap;
import java.util.Map;

import net.paoding.spdy.common.frame.frames.DataFrame;
import net.paoding.spdy.common.frame.frames.SpdyFrame;
import net.paoding.spdy.common.frame.frames.SynStream;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class SubscriptionEncoder extends SimpleChannelHandler {

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if (e.getMessage() instanceof SpdyResponse) {
            SpdyResponse resp = (SpdyResponse) e.getMessage();
            HttpResponse http = resp.getMessage();
            SynStream synStream = new SynStream();
            synStream.setChannel(e.getChannel());
            setHeaders(resp, http, synStream);
            //
            synStream.setAssociatedId(resp.getAssociatedId());
            synStream.setFlags(SpdyFrame.FLAG_UNIDIRECTIONAL);
            int streamId = resp.getStreamId();
            synStream.setStreamId(streamId);
            ChannelBuffer content = resp.getMessage().getContent();
            boolean emptyContent = content == null || !content.readable();
            if (emptyContent) {
                synStream.setFlags(SpdyFrame.FLAG_FIN);
                Channels.write(synStream.getChannel(), synStream);
            } else {
                Channels.write(synStream.getChannel(), synStream);
                //
                DataFrame dataFrame = new DataFrame();
                dataFrame.setChannel(synStream.getChannel());
                dataFrame.setStreamId(streamId);
                dataFrame.setFlags(SpdyFrame.FLAG_FIN);
                dataFrame.setData(content);
                // write dataFrame
                Channels.write(synStream.getChannel(), dataFrame);
            }
        } else {
            ctx.sendDownstream(e);
        }
    }

    private void setHeaders(SpdyResponse resp, HttpResponse http, SynStream synStream) {
        Map<String, String> headers = new HashMap<String, String>();
        for (Map.Entry<String, String> header : resp.getMessage().getHeaders()) {
            headers.put(header.getKey(), header.getValue());
        }
        if (http.getStatus() != null) {
            HttpResponseStatus status = http.getStatus();
            headers.put("status", status.getCode() + " " + status.getReasonPhrase());
        } else {
            headers.put("status", "200 OK");
        }
        synStream.setHeaders(headers);
    }
}
