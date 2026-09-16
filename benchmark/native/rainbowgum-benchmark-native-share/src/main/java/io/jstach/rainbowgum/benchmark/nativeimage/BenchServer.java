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
