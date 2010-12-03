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
package net.paoding.spdy.common.http;

import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;

/**
 * 更简便的 {@link HttpResponse}，默认使用http 1.1版本号、200 OK 状态的
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class DefaultHttpResponse extends org.jboss.netty.handler.codec.http.DefaultHttpResponse {

    public static HttpResponseStatus STATUS_OK = new HttpResponseStatus(200, "OK");

    public DefaultHttpResponse() {
        super(HttpVersion.HTTP_1_1, STATUS_OK);
    }
}
