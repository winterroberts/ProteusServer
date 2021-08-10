package net.aionstudios.proteus.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import net.aionstudios.proteus.api.context.ProteusHttpContext;
import net.aionstudios.proteus.compression.CompressionEncoding;
import net.aionstudios.proteus.configuration.HttpConfiguration;
import net.aionstudios.proteus.configuration.WebSocketConfiguration;
import net.aionstudios.proteus.proxy.ProxyClient;
import net.aionstudios.proteus.request.ProteusHttpRequest;
import net.aionstudios.proteus.websocket.ProteusWebSocket;

public class ProteusHttp {
	
	protected HttpServer httpServer;
	
	protected HttpConfiguration configuration;
	protected ProteusWebSocket loopbackWebSocket;
	
	private boolean running = false;
	
	public ProteusHttp(HttpConfiguration configuration) {
		this.configuration = configuration;
	}
	
	public ProteusHttp(HttpConfiguration configuration, ProteusWebSocket loopbackWebSocket) {
		this.configuration = configuration;
		this.loopbackWebSocket = loopbackWebSocket;
	}
	
	public void start() {
		if (!running) {
			if (loopbackWebSocket != null) loopbackWebSocket.start();
			if (!configuration.isSecure()) {
				try {
					httpServer = HttpServer.create(new InetSocketAddress(configuration.getPort()), 0);
				} catch (IOException e) {
					System.err.println("Failed to start HTTP Server!");
					e.printStackTrace();
				}
			} else {
				try {
					httpServer = HttpsServer.create(new InetSocketAddress(configuration.getPort()), 0);
					((HttpsServer) httpServer).setHttpsConfigurator(new HttpsConfigurator(configuration.getSslContext()) {
						
						@Override
						public void configure(HttpsParameters params) {
				        	 try {
				        		 // initialize the SSL context
				        		 SSLContext c = SSLContext.getDefault();
				        		 SSLEngine engine = c.createSSLEngine();
				        		 params.setNeedClientAuth(false);
				        		 params.setCipherSuites(engine.getEnabledCipherSuites());
				        		 params.setProtocols(engine.getEnabledProtocols());
				        		 // get the default parameters
				        		 SSLParameters defaultSSLParameters = c.getDefaultSSLParameters();
				        		 params.setSSLParameters(defaultSSLParameters);
				        	 } catch (Exception ex) {
				        		 ex.printStackTrace();
				        		 System.out.println("Failed to create HTTPS server");
				        	 }
				         }
						
					});
				} catch (IOException e) {
					System.err.println("Failed to start HTTPS Server!");
					e.printStackTrace();
				}
			}
			httpServer.createContext("/", new HttpHandler() {
	
				@Override
				public void handle(HttpExchange exchange) throws IOException {
					Headers requestHeaders = exchange.getRequestHeaders();
					if (loopbackWebSocket != null && requestHeaders.containsKey("Connection") && requestHeaders.containsKey("Upgrade") &&
							requestHeaders.getFirst("Connection").equals("Upgrade") && requestHeaders.getFirst("Upgrade").equals("websocket")) {
						InputStream in = exchange.getRequestBody();
						OutputStream out = exchange.getResponseBody();
						exchange.setStreams(new ProteusInputStream(new ProteusInputStream(in)), new ProteusOutputStream(new ProteusOutputStream(out)));
						String method = exchange.getRequestMethod();
						String requestURI = exchange.getRequestURI().toString();
						ProxyClient proxy = new ProxyClient(InetAddress.getLoopbackAddress().toString(), loopbackWebSocket.getConfiguration().getPort(), in, out);
						proxy.write(method + " " + requestURI + " HTTP/1.1");
						for (Entry<String, List<String>> entry : requestHeaders.entrySet()) {
							for (String s : entry.getValue()) {
								proxy.write(entry.getKey() + ": " + s);
							}
						}
						proxy.open();
					} else {
						// TODO Auto-generated method stub
						// Setup stuff
						CompressionEncoding ce = exchange.getRequestHeaders().containsKey("Accept-Encoding") ? 
								CompressionEncoding.forAcceptHeader(exchange.getRequestHeaders().getFirst("Accept-Encoding"), configuration.isEnableBrotli()) : 
									CompressionEncoding.NONE;
						ProteusHttpRequest request = new ProteusHttpRequest(exchange, ce);
						String path = request.getPath();
						ProteusHttpContext context = configuration.getContextController().getPathContext(path);
						boolean defContext = false;
						if (context == null) {
							context = configuration.getContextController().getDefault();
							defContext = true;
						}
						if (context != null && (defContext || context.pathMatch(path))) {
							context.handle(request);
							// TODO this returns a gz download and it shouldnt
						}
						
						// Closing stuff
					}
				}
				
			});
			httpServer.setExecutor(Executors.newCachedThreadPool());
			httpServer.start();
			System.out.println((configuration.isSecure() ? "Secure " : "") + "HTTP server started on port " + configuration.getPort());
			// TODO get implementer name to display start path
			running = true;
		}
	}
	
	public void stop() {
		if (running) {
			if (loopbackWebSocket != null) loopbackWebSocket.stop();
			httpServer.stop(0);
			running = false;
		}
	}
	
	public HttpConfiguration getHttpConfiguration() {
		return configuration;
	}
	
	public ProteusWebSocket getLoopbackWebSocket() {
		return loopbackWebSocket;
	}

}
