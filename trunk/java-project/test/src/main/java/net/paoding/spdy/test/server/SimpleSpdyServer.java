package net.paoding.spdy.test.server;

public class SimpleSpdyServer {

	public static void main(String[] args) {
		
		if (args.length != 1) {
			System.err.println("Usage: java " + SimpleSpdyServer.class.getSimpleName() + " port");
			return;
		}
		
		int port = Integer.parseInt(args[0]);
		MockServer mockServer = new MockServer(port);
	    mockServer.start();
	}
	
}
