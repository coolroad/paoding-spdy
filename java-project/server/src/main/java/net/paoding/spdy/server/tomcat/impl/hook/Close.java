package net.paoding.spdy.server.tomcat.impl.hook;

import net.paoding.spdy.server.tomcat.impl.supports.SpdyOutputBuffer;

import org.apache.coyote.Request;
import org.apache.coyote.Response;

public class Close implements Action {

    @Override
    public void action(Request request, Response response, Object param) {
        SpdyOutputBuffer output = (SpdyOutputBuffer) response.getOutputBuffer();
        output.close(response);
    }

}