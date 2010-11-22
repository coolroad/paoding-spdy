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

public class Commit implements Action {

    private Log logger = LogFactory.getLog(getClass());

    @Override
    public void action(Request request, Response response, Object param) {
        if (response.isCommitted()) {
            return;
        }
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
