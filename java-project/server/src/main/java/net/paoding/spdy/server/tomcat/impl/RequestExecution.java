package net.paoding.spdy.server.tomcat.impl;

import static org.apache.coyote.ActionCode.ACTION_CLOSE;
import static org.apache.coyote.ActionCode.ACTION_COMMIT;

import java.util.concurrent.Executor;

import net.paoding.spdy.server.tomcat.impl.hook.Action;
import net.paoding.spdy.server.tomcat.impl.hook.Close;
import net.paoding.spdy.server.tomcat.impl.hook.Commit;
import net.paoding.spdy.server.tomcat.impl.supports.SpdyOutputBuffer;

import org.apache.coyote.ActionCode;
import org.apache.coyote.ActionHook;
import org.apache.coyote.Adapter;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

@Sharable
public class RequestExecution extends SimpleChannelHandler {

    protected static Log logger = LogFactory.getLog(RequestExecution.class);

    private Adapter adapter;

    private Executor executor;

    public RequestExecution(Executor executor, Adapter adapter) {
        this.executor = executor;
        this.adapter = adapter;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Object msg = e.getMessage();
        if (msg instanceof Request) {
            final Request request = (Request) msg;
            final Response response = createResponse(request);
            Runnable command = new Runnable() {

                @Override
                public void run() {
                    try {
                        adapter.service(request, response);
                    } catch (Exception e) {
                        logger.error("", e);
                    }
                }
            };
            executor.execute(command);
        } else {
            ctx.sendUpstream(e);
        }
    }

    private Response createResponse(Request request) {
        Response response = new Response();
        request.setResponse(response);
        response.setRequest(request);
        response.setHook(new HookDelegate(request, response));
        response.setOutputBuffer(new SpdyOutputBuffer());
        return response;
    }

    private static class HookDelegate implements ActionHook {

        private static Action[] actions = new Action[50];
        {
            actions[ACTION_COMMIT.getCode()] = new Commit();
            actions[ACTION_CLOSE.getCode()] = new Close();
        }

        private final Request request;

        private final Response response;

        public HookDelegate(Request request, Response response) {
            this.request = request;
            this.response = response;
        }

        @Override
        public void action(ActionCode actionCode, Object param) {
            Action action = actions[actionCode.getCode()];
            if (action != null) {
                action.action(request, response, param);
            } else {
                logger.debug("not action for code " + actionCode);
            }
        }

    }

}
