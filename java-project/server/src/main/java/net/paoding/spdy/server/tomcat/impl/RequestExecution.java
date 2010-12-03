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
package net.paoding.spdy.server.tomcat.impl;

import static org.apache.coyote.ActionCode.ACTION_CLIENT_FLUSH;
import static org.apache.coyote.ActionCode.ACTION_CLOSE;
import static org.apache.coyote.ActionCode.ACTION_COMMIT;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.ExecutorService;

import net.paoding.spdy.server.tomcat.impl.hook.Action;
import net.paoding.spdy.server.tomcat.impl.hook.ClientFlush;
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
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
@Sharable
public class RequestExecution extends SimpleChannelHandler {

    protected static Log logger = LogFactory.getLog(RequestExecution.class);

    private Adapter coyoteAdapter;

    private ExecutorService requstExecutor;

    private int ouputBufferSize;

    public RequestExecution(ExecutorService executor, Adapter adapter, int ouputBufferSize) {
        this.requstExecutor = executor;
        this.coyoteAdapter = adapter;
        this.ouputBufferSize = ouputBufferSize;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Object msg = e.getMessage();
        if (msg instanceof Request) {
            final Request request = (Request) msg;
            final Response response = createResponse(request, e.getChannel().getConfig()
                    .getBufferFactory());
            requstExecutor.submit(new Runnable() {

                @Override
                public void run() {
                    try {
                        coyoteAdapter.service(request, response);
                    } catch (Exception e) {
                        logger.error("", e);
                    }
                }
            });
        } else {
            ctx.sendUpstream(e);
        }
    }

    private Response createResponse(Request request, ChannelBufferFactory factory) {
        Response response = new Response();
        request.setResponse(response);
        response.setRequest(request);
        response.setHook(new HookDelegate(request, response));
        response.setOutputBuffer(new SpdyOutputBuffer(factory, ouputBufferSize));
        return response;
    }

    private static class HookDelegate implements ActionHook {

        private static Action[] actions = new Action[50];

        private static Field[] codeFields = new Field[50]; // now is 25

        static {
            //
            actions[ACTION_COMMIT.getCode()] = new Commit();
            actions[ACTION_CLOSE.getCode()] = new Close();
            actions[ACTION_CLIENT_FLUSH.getCode()] = new ClientFlush();

            Field[] fields = ActionCode.class.getFields();
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers())) {
                    try {
                        ActionCode code = (ActionCode) field.get(ActionCode.class);
                        codeFields[code.getCode()] = field;
                    } catch (IndexOutOfBoundsException e) {
                        throw e;
                    } catch (Exception e) {
                        logger.error("", e);
                    }
                }

            }
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
                if (logger.isDebugEnabled()) {
                    logger.debug("suck " + codeFields[actionCode.getCode()].getName());
                }
                action.action(request, response, param);
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("ignores " + codeFields[actionCode.getCode()].getName());
                }
            }
        }
    }

}
