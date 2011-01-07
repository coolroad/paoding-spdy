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
package net.paoding.spdy.server.tomcat.impl.supports;

import net.paoding.spdy.common.frame.frames.SynStream;

import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.jboss.netty.channel.Channel;

public class CoyoteAttributes {

    public static void setSynStream(Request request, SynStream SynStream) {
        request.setAttribute("net.paoding.spdy.synStream", SynStream);
    }

    public static SynStream getSynStream(Request request) {
        return (SynStream) request.getAttribute("net.paoding.spdy.synStream");
    }

    public static int getStreamId(Request request) {
        return getSynStream(request).getStreamId();
    }

    public static int getStreamId(Response response) {
        return getStreamId(response.getRequest());
    }

    public static Channel getChannel(Request request) {
        return (Channel) getSynStream(request).getChannel();
    }

    public static Channel getChannel(Response response) {
        return getChannel(response.getRequest());
    }

}
