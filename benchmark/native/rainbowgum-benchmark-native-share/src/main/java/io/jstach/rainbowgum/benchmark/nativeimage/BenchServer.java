package io.jstach.rainbowgum.benchmark.nativeimage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

/**
 * Starts a plain JDK {@link HttpServer} serving {@link BenchHandler} at {@code /greet},
 * dispatched on virtual threads - the same concurrency model the Spring Boot webapp
 * benchmark's "virtual threads" scenario used, deliberately with nothing else (no
 * framework, no servlet container) in front of it.
 */
public final class BenchServer {

	private BenchServer() {
	}

	/**
	 * Starts the server. Blocks the calling thread forever (the server itself runs on its
	 * own dispatch thread/executor) so a framework's {@code main} method can just call
	 * this last.
	 * @param port port to listen on.
	 * @throws IOException if the server socket cannot be bound.
	 */
	public static void startAndAwait(int port) throws IOException {
		/*
		 * com.sun.net.httpserver's own accepted sockets do not set TCP_NODELAY unless
		 * this internal system property is set before HttpServer.create(...) runs -
		 * without it, every single request pays for the classic Nagle's-algorithm /
		 * delayed-ACK interaction (a consistent ~40ms stall per request, confirmed by
		 * hand against this exact server), which has nothing to do with the application
		 * or the logging backend but completely swamps it if left unset, making every
		 * backend measured through this server look identically (and misleadingly) slow.
		 */
		System.setProperty("sun.net.httpserver.nodelay", "true");
		HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
		server.createContext("/greet", new BenchHandler());
		server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
		server.start();
		System.out.println("listening on :" + port);
		Object lock = new Object();
		synchronized (lock) {
			try {
				lock.wait();
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

}
