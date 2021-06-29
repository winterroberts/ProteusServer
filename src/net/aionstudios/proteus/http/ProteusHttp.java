package net.aionstudios.proteus.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import net.aionstudios.proteus.api.context.ProteusHttpContext;
import net.aionstudios.proteus.compression.CompressionEncoding;
import net.aionstudios.proteus.configuration.HttpConfiguration;
import net.aionstudios.proteus.request.ProteusHttpRequest;

public class ProteusHttp {
	
	protected HttpServer httpServer;
	
	protected HttpConfiguration configuration;
	
	private boolean running = false;
	
	public ProteusHttp(HttpConfiguration configuration) {
		this.configuration = configuration;
	}
	
	public void start() {
		if (!running) {
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
			httpServer.stop(0);
			running = false;
		}
	}

}
