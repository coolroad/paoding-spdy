package net.paoding.spdy.server.tomcat.impl.hook;

import org.apache.coyote.Request;
import org.apache.coyote.Response;

public interface Action {

    public void action(Request request, Response response, Object param);
}
