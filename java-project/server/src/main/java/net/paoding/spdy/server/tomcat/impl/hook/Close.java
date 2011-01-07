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

import net.paoding.spdy.server.subscription.Subscription;
import net.paoding.spdy.server.tomcat.impl.supports.SpdyOutputBuffer;

import org.apache.coyote.Request;
import org.apache.coyote.Response;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class Close implements Action {

    @Override
    public void action(Request request, Response response, Object param) {
        // output
        if (response.getContentLength() != 0) {
            SpdyOutputBuffer output = (SpdyOutputBuffer) response.getOutputBuffer();
            output.flushAndClose(response);
        }

        // subscription
        Subscription subscription = (Subscription) request.getAttribute(Subscription.REQUEST_ATTR);
        if (subscription != null && !subscription.isAccepted()) {
            subscription.close();
        }
    }

}
