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
package net.paoding.spdy.test.request;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import net.paoding.spdy.client.Bootstrap;
import net.paoding.spdy.client.Connector;
import net.paoding.spdy.client.Future;
import net.paoding.spdy.client.SubscriptionListener;
import net.paoding.spdy.client.SubscriptionStub;
import net.paoding.spdy.client.netty.NettyBootstrap;
import net.paoding.spdy.common.http.DefaultHttpRequest;
import net.paoding.spdy.test.server.MockServer;

import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class RequestTest {

    private static final int port = 28080;

    private static MockServer mockServer;

    private static Bootstrap bootstrap;

    private static Connector connector;

    @BeforeClass
    public static void start() {
        // server
        mockServer = new MockServer(port);
        mockServer.start();

        // client
        bootstrap = new NettyBootstrap();
        Future<Connector> connecting = bootstrap.connect("localhost", port);
        connector = connecting.awaitUninterruptibly().getConnector();
    }

    @AfterClass
    public static void destroy() {
        // destroy client
        if (connector != null) {
            connector.close().awaitUninterruptibly();
            bootstrap.destroy();
        }
        // destory server
        mockServer.destroy();
    }

    //------------------

    @Test
    public void testHello() {
        //
        HttpRequest request = new DefaultHttpRequest("get", "/hello");
        Future<HttpResponse> responseFuture = connector.doRequest(request);
        responseFuture.awaitUninterruptibly();
        HttpResponse response = responseFuture.get();
        //
        Assert.assertEquals(200, response.getStatus().getCode());
        Assert.assertEquals("hello", getContentAsString(response));
    }

    @Test
    public void testHelloMultiThread() throws Exception {
    	ExecutorService exe = Executors.newFixedThreadPool(100);
    	
    	int n = 10000;
    	List<java.util.concurrent.Future<HttpResponse>> futures = new ArrayList<java.util.concurrent.Future<HttpResponse>>();
    	while (n-- > 0) {
    		java.util.concurrent.Future<HttpResponse> future = exe.submit(new Callable<HttpResponse>() {
				@Override
				public HttpResponse call() throws Exception {
					HttpRequest request = new DefaultHttpRequest("get", "/hello");
		            Future<HttpResponse> responseFuture = connector.doRequest(request);
		            responseFuture.await(1000);
		            HttpResponse response = responseFuture.get();
					return response;
				}
			});
    		futures.add(future);
            
    	}
    	HttpResponse response;
		for (java.util.concurrent.Future<HttpResponse> f : futures) {
    		response = f.get();
    		// 如果response为null，可以适当增大上面responseFuture.await(n)中n的值
            Assert.assertNotNull(response);
    		Assert.assertEquals(200, response.getStatus().getCode());
            Assert.assertEquals("hello", getContentAsString(response));
        }
        exe.shutdown();
        Thread.sleep(10);
    }

    @Test
    public void testSubscribe() throws InterruptedException {
        //
        HttpRequest request = new DefaultHttpRequest("get", "/subscribe");
        final BlockingQueue<HttpResponse> pushed = new LinkedBlockingQueue<HttpResponse>();
        SubscriptionListener listener = new SubscriptionListener() {

            @Override
            public void responseReceived(SubscriptionStub subscription, HttpResponse response) {
                pushed.offer(response);
            }
        };
        SubscriptionStub sub = connector.subscribe(request, listener);
        sub.getResponseFuture().awaitUninterruptibly();
        HttpResponse response = sub.getResponseFuture().get();
        //
        Assert.assertEquals(200, response.getStatus().getCode());
        Assert.assertEquals("accepted", getContentAsString(response));

        HttpResponse onepushed = pushed.poll(10, TimeUnit.SECONDS);
        Assert.assertNotNull(onepushed);
        Assert.assertEquals(200, onepushed.getStatus().getCode());
        Assert.assertEquals("pushed", getContentAsString(onepushed));
    }

    private static String getContentAsString(HttpResponse response) {
        try {
            return new String(response.getContent().array(), response.getContent().arrayOffset()
                    + response.getContent().readerIndex(), response.getContent().readableBytes(),
                    "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return "error";
        }
    }

}
