package net.paoding.spdy.test.request;

import net.paoding.spdy.client.Bootstrap;
import net.paoding.spdy.client.Connector;
import net.paoding.spdy.client.Future;
import net.paoding.spdy.client.netty.NettyBootstrap;
import net.paoding.spdy.common.http.DefaultHttpRequest;
import net.paoding.spdy.test.util.TestUtil;

import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.junit.Assert;
import org.junit.Test;

public class TestConnectOldServer {

	private Connector connector;
	
	private Bootstrap bootstrap;
	
	public void init(String hostname, int port) {
    	bootstrap = new NettyBootstrap();
        Future<Connector> connecting = bootstrap.connect(hostname, port);
        connector = connecting.awaitUninterruptibly().getConnector();
    }
	
	
	@Test
	public void testGet() throws InterruptedException {
		
		//init("10.22.200.140", 8188);
		init("localhost", 8188);
		
		HttpRequest request = new DefaultHttpRequest("get", "/text/2/0");
        Future<HttpResponse> responseFuture = connector.doRequest(request);
        //responseFuture.await(1000);
        responseFuture.awaitUninterruptibly();
		
        HttpResponse response = responseFuture.get();
        Assert.assertNotNull(response);
        System.out.println(TestUtil.getContentAsString(response));
	}
	
}
