package net.paoding.spdy.server.tomcat.impl.hook;

import net.paoding.spdy.server.tomcat.impl.supports.SpdyOutputBuffer;

import org.apache.coyote.Request;
import org.apache.coyote.Response;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class ClientFlush implements Action {

    @Override
    public void action(Request request, Response response, Object param) {
        ((SpdyOutputBuffer) response.getOutputBuffer()).flush(response);
    }

}
