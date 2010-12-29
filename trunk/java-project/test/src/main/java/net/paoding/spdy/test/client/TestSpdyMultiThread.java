package net.paoding.spdy.test.client;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.paoding.spdy.client.Bootstrap;
import net.paoding.spdy.client.Connector;
import net.paoding.spdy.client.Future;
import net.paoding.spdy.client.netty.NettyBootstrap;
import net.paoding.spdy.common.http.DefaultHttpRequest;

import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;


/**
 * @author Li Weibo (weibo.leo@gmail.com) //I believe spring-brother
 * @since 2010-12-29 下午04:18:14
 */
public class TestSpdyMultiThread {
	
	private AtomicInteger sent = new AtomicInteger();
	
	private AtomicInteger succ = new AtomicInteger();
	
	private AtomicInteger fail = new AtomicInteger();
	
	private ScheduledExecutorService threadPool = Executors.newScheduledThreadPool(250);
	
	private long startTime;
	
	private Connector connector;
	
	private Bootstrap bootstrap;
	
    public void init(String hostname, int port) {
    	bootstrap = new NettyBootstrap();
        Future<Connector> connecting = bootstrap.connect(hostname, port);
        connector = connecting.awaitUninterruptibly().getConnector();
    }
	
	private Runnable monitor = new Runnable() {
		@Override
		public void run() {
			long currentTime = System.currentTimeMillis();
			int duration = (int)((currentTime - startTime) / 1000);
			int s = sent.get();
			int r = succ.get();
			int f = fail.get();
			System.out.println(duration + "s (sent:" + s + ", " + 1.0 * s / duration
					+ "/s), (received:" + r + ", " + 1.0 * r / duration + "/s), (fail:" + f + ")");
		}
	};
	
	private void startMonitor() {
		startTime = System.currentTimeMillis();
		int duration = 10;
		threadPool.scheduleAtFixedRate(monitor, duration, duration, TimeUnit.SECONDS);
	}
	
	private class TestTask implements Runnable {
		
		private String path;
		
		public TestTask(String path) {
			this.path = path;
		}
		
		@Override
		public void run() {
			while (true) {
				try {
					HttpRequest request = new DefaultHttpRequest(HttpMethod.GET, path);
					request.setHeader("Host", "blog.xoa.renren.com");
		            Future<HttpResponse> responseFuture = connector.doRequest(request);
		            sent.incrementAndGet();
		            responseFuture.await(1000);
		            HttpResponse response = responseFuture.get();
		            if (response != null && response.getStatus().getCode() == 200) {
		            	succ.incrementAndGet();
		            } else {
		            	fail.incrementAndGet();
		            }
				} catch (Exception e) {
					e.printStackTrace();
					fail.incrementAndGet();
				}
			}
		}
	}
	
	private static void multiThreadTest(String host, String path, int nThreads) {
		System.out.println("start paoding-spdy multi-thread test");
		System.out.println(host + path);
		String[] ss = host.split(":");
		TestSpdyMultiThread app = new TestSpdyMultiThread();
		app.init(ss[0], Integer.parseInt(ss[1]));
		app.startMonitor();
		for (int i = 0; i < nThreads; i++) {
			app.threadPool.execute(app.new TestTask( path));
		}
	}
	
	public static void main(String[] args) {
		
		if (args.length != 3) {
			System.out.println("Usage:java " + TestSpdyMultiThread.class.getName() + " host path concurrency");
			return;
		} 
		int con = Integer.parseInt(args[2]);
		if (con > 200) {
			System.out.println("concurrency too big:" + con);
			return;
		}
		multiThreadTest(args[0], args[1], con);
	}
}
