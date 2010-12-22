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

import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;

/**
 * 更简便的 {@link HttpRequest}，默认使用http 1.1版本号，所有的header小写化
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class DefaultHttpRequest extends org.jboss.netty.handler.codec.http.DefaultHttpRequest {

    public DefaultHttpRequest(String method, String uri) {
        super(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), uri);
    }

    public DefaultHttpRequest(HttpMethod method, String uri) {
        super(HttpVersion.HTTP_1_1, method, uri);
    }

    public DefaultHttpRequest(HttpVersion version, HttpMethod method, String uri) {
        super(version, method, uri);
    }

    @Override
    public void addHeader(String name, Object value) {
        super.addHeader(name.toLowerCase(), value);
    }

    @Override
    public void setHeader(String name, Iterable<?> values) {
        super.setHeader(name.toLowerCase(), values);
    }

    @Override
    public void setHeader(String name, Object value) {
        super.setHeader(name.toLowerCase(), value);
    }

    @Override
    public void removeHeader(String name) {
        super.removeHeader(name.toLowerCase());
    }

}
