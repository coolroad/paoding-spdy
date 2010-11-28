package net.paoding.spdy.client;

import java.io.IOException;

import net.paoding.spdy.client.impl.NettyConnectorFactory;

import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        NettyConnectorFactory factory = new NettyConnectorFactory();
        HttpFuture<Connector> connecting = factory.get("localhost", 8081).connect();
        Connector connector = connecting.awaitUninterruptibly().getConnector();
        //        //
        HttpRequest request = new DefaultHttpRequest("post", "/the8/test");
        HttpParameters parameters = new HttpParameters();
        setParameters(args, parameters).copyTo(request);

        //
        HttpFuture<HttpResponse> responseFuture = connector.doRequest(request);
        responseFuture.awaitUninterruptibly();
        HttpResponse response = responseFuture.getTarget();

        //
        System.out.println("status=" + response.getStatus());
        System.out.println("content=" + getContentAsString(response));
        //
        System.out.println();
        if (true) {
            connector.close().awaitUninterruptibly();
            System.out.println("closed");
            return;
        }

        System.out.println(".... enter subscribe");
        //            Thread.sleep(10000);
        System.out.println("entered");

        DefaultHttpRequest request2 = new DefaultHttpRequest("get", "/the8/register");
        Subscription sub = connector.subscribe(request2, new SubscriptionListener() {

            @Override
            public void responseReceived(Subscription subscription, HttpResponse response) {

                System.out.println("subscription.content=" + getContentAsString(response));
            }
        });
        HttpResponse response2 = sub.getSubscriptionFutrue().awaitUninterruptibly().getTarget();

        System.out.println(getContentAsString(response2));

        Thread.sleep(100000);

        //
        connector.close().awaitUninterruptibly();
        System.out.println("closed");

    }

    public static void main1(String[] args) throws IOException {
        ConnectorFactory factory = new NettyConnectorFactory();
        HttpFuture<Connector> connecting = factory.get("localhost", 8081).connect();
        Connector connector = connecting.awaitUninterruptibly().getConnector();
        //
        HttpRequest request = new DefaultHttpRequest("get", "/the8/test");
        HttpParameters parameters = new HttpParameters();
        setParameters(args, parameters).copyTo(request);
        //
        HttpFuture<HttpResponse> responseFuture = connector.doRequest(request);
        responseFuture.awaitUninterruptibly();
        HttpResponse response = responseFuture.getTarget();
        //
        System.out.println("status=" + response.getStatus());
        System.out.println("content=" + getContentAsString(response));
        connector.close().awaitUninterruptibly();
        System.out.println("closed");

    }

    private static String getContentAsString(HttpResponse response) {
        return new String(response.getContent().array(), response.getContent().readerIndex(),
                response.getContent().readableBytes());
    }

    private static HttpParameters setParameters(String[] args, HttpParameters parameters) {
        parameters.setParameter("p", "hello world");
        parameters.setParameter("view", "not-view");
        return parameters;
    }

    public static void main0(String[] args) throws IOException {
        ConnectorFactory factory = new NettyConnectorFactory();
        HttpFuture<Connector> connecting = factory.get("localhost", 8081).connect();
        Connector connection = connecting.awaitUninterruptibly().getTarget();
        //
        HttpRequest request = new DefaultHttpRequest("GET", "/blog/123456");
        HttpParameters parameters = new HttpParameters();
        parameters.setParameter("view", "preview");
        parameters.copyTo(request);
        HttpFuture<HttpResponse> responseFuture = connection.doRequest(request);
        HttpResponse response = responseFuture.awaitUninterruptibly().getTarget();
        System.out.println(response.getStatus());
    }
}
