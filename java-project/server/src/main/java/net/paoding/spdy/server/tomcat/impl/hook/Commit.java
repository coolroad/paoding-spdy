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
package net.paoding.spdy.server.tomcat.impl.hook;

import java.util.HashMap;
import java.util.Map;

import net.paoding.spdy.common.frame.frames.SpdyFrame;
import net.paoding.spdy.common.frame.frames.SynReply;
import net.paoding.spdy.server.tomcat.impl.supports.CoyoteAttributes;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.jboss.netty.channel.Channels;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class Commit implements Action {

    private Log logger = LogFactory.getLog(getClass());

    @Override
    public void action(Request request, Response response, Object param) {
        commit(request, response);
    }

    private void commit(Request request, Response response) {
        if (logger.isDebugEnabled()) {
            logger.debug("commiting response: " + request);
        }
        SynReply frame = new SynReply();
        frame.setChannel(CoyoteAttributes.getChannel(request));
        frame.setStreamId(CoyoteAttributes.getStreamId(request));
        Map<String, String> headers = new HashMap<String, String>();
        if (response.getMessage() != null) {
            headers.put("status", valueOf(response.getStatus(), response.getMessage()));
        } else {
            headers.put("status", valueOf(response.getStatus()));
        }
        headers.put("version", "HTTP/1.1");
        String contentType = response.getContentType();
        if (contentType != null) {
            headers.put("Content-Type", contentType);
        }
        String contentLang = response.getContentLanguage();
        if (contentLang != null) {
            headers.put("Content-Language", contentLang);
        }
        int contentLength = response.getContentLength();
        headers.put("Content-Length", String.valueOf(contentLength));
        if (contentLength == 0) {
            frame.setFlags(SpdyFrame.FLAG_FIN);
            CoyoteAttributes.setFinished(response);
            if (logger.isInfoEnabled()) {
                logger.info("closing response (by commit): " + response.getRequest());
            }
        }

        MimeHeaders coyoteHeaders = response.getMimeHeaders();
        for (int i = 0; i < coyoteHeaders.size(); i++) {
            MessageBytes key = coyoteHeaders.getName(i);
            MessageBytes value = coyoteHeaders.getValue(i);
            headers.put(key.getString(), value.getString());
        }
        //

        //
        frame.setHeaders(headers);
        Channels.write(frame.getChannel(), frame);
        response.setCommitted(true);
    }

    static String valueOf(int sc) {
        switch (sc) {
            case 200:
                return "200";
            case 404:
                return "404";
            case 302:
                return "302";
            case 301:
                return "301";
            case 500:
                return "500";

            default:
                return String.valueOf(sc);
        }
    }

    static String valueOf(int sc, String message) {
        switch (sc) {
            case 200:
                if (message.equals("OK")) {
                    return "200 OK";
                }
            default:
                return valueOf(sc) + " " + message;
        }
    }

}
