package net.winrob.proteus;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Set;
import java.util.stream.Collectors;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLProtocolException;
import javax.net.ssl.SSLSocket;

import net.winrob.commons.saon.EventDispatcher;
import net.winrob.proteus.api.ProteusApp;
import net.winrob.proteus.api.context.ProteusHttpContext;
import net.winrob.proteus.api.context.ProteusWebSocketContext;
import net.winrob.proteus.api.event.http.ClientKeepAliveEvent;
import net.winrob.proteus.api.event.http.HttpContextRoutedEvent;
import net.winrob.proteus.api.event.server.ClientAcceptEvent;
import net.winrob.proteus.api.event.server.ListenerThreadSpawnedEvent;
import net.winrob.proteus.api.event.server.RequestReceivedEvent;
import net.winrob.proteus.api.event.server.SSLHandshakeCompletedEvent;
import net.winrob.proteus.api.event.websocket.WebSocketContextRoutedEvent;
import net.winrob.proteus.api.request.ProteusH2ConnectionImpl;
import net.winrob.proteus.api.request.ProteusHttpRequest;
import net.winrob.proteus.api.request.ProteusHttpRequestImpl;
import net.winrob.proteus.api.request.ProteusWebSocketConnection;
import net.winrob.proteus.api.request.ProteusWebSocketConnectionImpl;
import net.winrob.proteus.api.request.ProteusWebSocketConnectionManager;
import net.winrob.proteus.api.request.ProteusWebSocketRequestImpl;
import net.winrob.proteus.api.response.ProteusHttpResponse;
import net.winrob.proteus.api.response.ProteusHttpResponseImpl;
import net.winrob.proteus.api.response.ResponseCode;
import net.winrob.proteus.compression.CompressionEncoding;
import net.winrob.proteus.configuration.EndpointType;
import net.winrob.proteus.error.ErrorResponse;
import net.winrob.proteus.header.ProteusHeaderBuilder;
import net.winrob.proteus.header.ProteusHttpHeaders;
import net.winrob.proteus.routing.CompositeRouter;
import net.winrob.proteus.routing.HttpRoute;
import net.winrob.proteus.routing.WebSocketRoute;
import net.winrob.proteus.server.api.ObjectInstantiator;
import net.winrob.proteus.util.StreamUtils;

/**
 * Server class which sets up server socket and listens for new connections.
 * 
 * @author Winter Roberts
 *
 */
public class ProteusServer {
	
	private ProteusApp app;
	private String appName;
	
	private Set<CompositeRouter> routers;
	
	private Thread listenThread;
	
	private boolean running;
	private boolean stopped;
	
	private EventDispatcher dispatcher;
	
	/**
	 * Creates a new server which listens according to the router(s) provided.
	 * 
	 * @param router A {@link CompositeRouter}, which may only contain one {@link Router}.
	 */
	public ProteusServer(Class<? extends ProteusApp> app) {
		this.app = ObjectInstantiator.getInstance().newInstance(app);
		appName = app.getTypeName();
		routers = this.app.build();
		running = false;
		stopped = true;
		dispatcher = new EventDispatcher(appName);
	}
	
	/**
	 * Opens listener threads and registers closing hooks.
	 */
	public void start() {
		if (!running) {
			if (routers == null) {
				System.err.println("Failed to start " + app.getClass().getCanonicalName() + "! No router");
				return;
			}
			running = true;
			stopped = false;
			for (CompositeRouter router : routers) {
				listenThread = new Thread(() -> {
	
					try {
						ServerSocket server = router.createSocket();
						new ListenerThreadSpawnedEvent(server, router).dispatchImmediately(dispatcher);
						while (running) {
							Socket client = server.accept();
							try {
								if (router.isSecure() && router.getTypes().contains(EndpointType.HTTP2)) {
									SSLSocket sslClient = (SSLSocket) client;
									sslClient.setHandshakeApplicationProtocolSelector((sock, list) -> {
										boolean hasH11 = false;
										boolean hasH2 = false;
										for (String ap : list) {
											switch(ap) {
											case "h2":
												hasH2 = true;
												break;
											case "http/1.1":
												hasH11 = true;
												break;
											default:
												break;
											}
										}
										if (hasH2) return "h2";
										if (hasH11 && router.getTypes().contains(EndpointType.HTTP1_1)) return "http/1.1";
										return null;
									});
									sslClient.addHandshakeCompletedListener(new HandshakeCompletedListener() {

										@Override
										public void handshakeCompleted(HandshakeCompletedEvent event) {
											new SSLHandshakeCompletedEventImpl(sslClient, router).dispatch(dispatcher);;
										}
										
									});
									sslClient.startHandshake();
								} else {
									new ClientAcceptEventImpl(client, router).dispatch(dispatcher);
								}
							} catch (SocketException e) {
								client.close();
							} catch (SSLHandshakeException e) {
								System.err.println("SSL failed: " + e.getMessage());
								client.close();
							} catch (Exception e) {
								e.printStackTrace();
								client.close();
							}
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
						
				});
				listenThread.setName(appName + "-Listener");
				listenThread.start();
				System.out.println("Port " + router.getPort() + " opened in " + String.join(", ", router.getTypes().stream().map(e -> e.getProtocolName()).collect(Collectors.toList())) + " mode(s)" + (router.isSecure() ? " (secure)" : "") + ".");
			}
			Proteus.addServer(this);
		}
	}
	
	public Class<? extends ProteusApp> getApp() {
		return app.getClass();
	}
	
	/**
	 * Handle a new client connection, which must be an HTTP/1.1 connection (but may be a websocket upgrade request).
	 * 
	 * @param client The new client connection to be handled.
	 * @throws IOException If there is an error reading from the socket.
	 */
	private boolean clientHandlerH11(Socket client, CompositeRouter router) throws IOException {
		InputStream inputStream = client.getInputStream();
		
		StringBuilder requestBuilder = new StringBuilder();
        String line;
	    while ((line = StreamUtils.readLine(inputStream, true)).length() < 4) {
	    	if (client.isInputShutdown()) return false;
	    }
	    if (line.equals("PRI * HTTP/2.0") && !router.isSecure()) {
	    	// upgrade insecure connection to h2 (prior knowledge)
	    	return true;
	    }
        do {
        	requestBuilder.append(line + "\r\n");
        } while (!(line = StreamUtils.readLine(inputStream, true)).isEmpty());
        
        
        String requestString = requestBuilder.toString();
        String[] requestsLines = requestString.split("\r\n");
        String[] requestLine = requestsLines[0].split(" ");
        String method = requestLine[0];
        String path = requestLine[1];
        String version = requestLine[2];

        ProteusHeaderBuilder headerBuilder = ProteusHeaderBuilder.newBuilder();
        for (int h = 1; h < requestsLines.length; h++) {
        	String header = requestsLines[h];
        	String[] headerSplit = header.split(":", 2);
        	if (headerSplit.length == 1 && headerSplit[0].length() > 0) {
        		headerBuilder.putHeader(headerSplit[0].trim(), null);
        	} else if (headerSplit.length == 2 && headerSplit[0].length() > 0 && headerSplit[1].length() > 0) {
        		headerBuilder.putHeader(headerSplit[0].trim(), headerSplit[1].trim());
        	} else {
        		// TODO error
        	}
        }
        ProteusHttpHeaders headers = headerBuilder.toHeaders();
        new RequestReceivedEventImpl(client, router, headers, method, path, version).dispatch(dispatcher);
        return false;
	}
	
	private final String[] connectionPrefaceLines = new String[] {"PRI * HTTP/2.0", "", "SM", ""};
	
	private void clientHandlerH2(Socket client, CompositeRouter router, boolean skipFirstPrefaceLine) throws IOException {
		InputStream inputStream = client.getInputStream();
		
		// read connection preface
		if (!skipFirstPrefaceLine) {
			String preface0 = StreamUtils.readLine(inputStream, true);
			if (!preface0.equals(connectionPrefaceLines[0])) {
				client.close();
				return;
			}
		}
		for (int i = 1; i <= 3; i++) {
			String pline = StreamUtils.readLine(inputStream, true);
			if (!pline.equals(connectionPrefaceLines[i])) {
				client.close();
				return;
			}
		}
		
		new ProteusH2ConnectionImpl(client, router).start();
	}
	
	private void routeRequest(RequestReceivedEvent event, Socket client, CompositeRouter router) throws IOException {
		String method = event.getMethod();
		String path = event.getPath();
		String version = event.getVersion();
		ProteusHttpHeaders headers = event.getHeaders();
        if (version.equals("HTTP/1.1")) {
        	if (method.equals("GET") && event.isWebSocket()) {
        		ProteusWebSocketRequestImpl request = new ProteusWebSocketRequestImpl(client, path, headers, router);
        		if (request.routed()) {
        			new WebSocketContextRoutedEventImpl(client, request).dispatch(dispatcher);
        		} else {
        			ErrorResponse.sendUnmodifiableErrorResponse(dispatcher, ResponseCode.NOT_FOUND, client.getOutputStream());
	        	}
        	} else {
	        	CompressionEncoding ce = headers.hasHeader("Accept-Encoding") ? 
	    				CompressionEncoding.forAcceptHeader(headers.getHeader("Accept-Encoding").getFirst().getValue()) : 
	    					CompressionEncoding.NONE;
	        	ProteusHttpRequestImpl request = new ProteusHttpRequestImpl(client, method, version, path, headers, router, dispatcher);
	        	ClientKeepAliveEvent keepAlive = headers.hasHeader("Connection")
	        			&& headers.getHeader("Connection").getFirst().getValue().equalsIgnoreCase("keep-alive")
	        			? new ClientKeepAliveEventImpl(client, router) : null;
	        	if (request.routed()) {
	        		new HttpContextRoutedEventImpl(this, client, request, keepAlive, ce, method).dispatch(dispatcher);
	        	} else {
	        		ErrorResponse.sendUnmodifiableErrorResponse(dispatcher, ResponseCode.NOT_FOUND, client.getOutputStream());
	        	}
        	}
        } else {
        	ErrorResponse.sendUnmodifiableErrorResponse(dispatcher, ResponseCode.HTTP_VERSION_NOT_SUPPORTED, client.getOutputStream());
        }
	}
	
	/**
	 * Stops the server, including running some shutdown events.
	 */
	public void stop() {
		if (running) {
			running = false;
			ProteusWebSocketConnectionManager.getConnectionManager().closeAll();
			stopped = true;
			Proteus.removeServer(this);
		}
	}
	
	/**
	 * @return True if the server accept thread is running, false otherwise.
	 */
	public boolean isRunning() {
		return running;
	}
	
	/**
	 * @return True is the server is stopped, false otherwise.
	 */
	public boolean isStopped() {
		return stopped;
	}
	
	public EventDispatcher getEventDispatcher() {
		return dispatcher;
	}
	
	/**
	 * @return The routers being used by the server.
	 */
	public Set<CompositeRouter> getRouters() {
		return routers;
	}
	
	
	/*
	 * Events
	 */
	
	private void startHandlerThread(Socket client, CompositeRouter router, String ap) {
		Thread handler = new Thread(() -> {
			try {
				if (!client.isClosed()) {
					client.setSoTimeout(30000);
					boolean shouldH2 = ap.equals("h2");
					if (!shouldH2) {
						if (clientHandlerH11(client, router)) {
							clientHandlerH2(client, router, true);
						}
					} else {
						clientHandlerH2(client, router, false);
					}
				}
			} catch(SSLProtocolException e) {
				
			} catch(SSLHandshakeException e) {
				
			} catch(SocketTimeoutException e) {
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
		handler.setName(appName + "-ClientHandler");
		handler.start();
	}
	
	private class ClientAcceptEventImpl extends ClientAcceptEvent {
		
		private final Socket client;
		private final CompositeRouter router;
		
		private final String ap;
		
		public ClientAcceptEventImpl(Socket client, CompositeRouter router) {
			this.client = client;
			this.router = router;
			this.ap = "http/1.1";
		}
		
		public ClientAcceptEventImpl(Socket client, CompositeRouter router, String ap) {
			this.client = client;
			this.router = router;
			this.ap = ap;
		}

		@Override
		public Socket getClientSocket() {
			return client;
		}

		@Override
		public CompositeRouter getRouter() {
			return router;
		}

		@Override
		protected boolean run() {
			startHandlerThread(client, router, ap);
			return true;
		}
		
	}
	
	private class RequestReceivedEventImpl extends RequestReceivedEvent {
		
		private final Socket client;
		private final CompositeRouter router;
		private final ProteusHttpHeaders headers;
		private final String method;
		private final String path;
		private final String version;
		
		public RequestReceivedEventImpl(Socket client, CompositeRouter router, ProteusHttpHeaders headers, String method, String path, String version) {
			this.client = client;
			this.router = router;
			this.headers = headers;
			this.method = method;
			this.path = path;
			this.version = version;
		}

		@Override
		public ProteusHttpHeaders getHeaders() {
			return headers;
		}

		@Override
		public String getMethod() {
			return method;
		}

		@Override
		public String getPath() {
			return path;
		}

		@Override
		public String getVersion() {
			return version;
		}

		@Override
		public boolean isSecure() {
			return router.isSecure();
		}

		@Override
		public boolean isWebSocket() {
			return headers.hasHeader("Sec-WebSocket-Key");
		}

		@Override
		protected boolean run() {
			try {
				routeRequest(this, client, router);
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
			return true;
		}
    	
    }
	
	private class WebSocketContextRoutedEventImpl extends WebSocketContextRoutedEvent {

		private final Socket client;
		private final ProteusWebSocketRequestImpl request;
		
		private final ProteusWebSocketConnection connection;
		
		public WebSocketContextRoutedEventImpl(Socket client, ProteusWebSocketRequestImpl request) throws IOException {
			this.client = client;
			this.request = request;
			this.connection = new ProteusWebSocketConnectionImpl(client, request);
		}
		
		@Override
		public ProteusWebSocketContext getContext() {
			return request.getContext();
		}

		@Override
		public WebSocketRoute getRoute() {
			return request.getRoute();
		}

		@Override
		protected boolean run() {
			try {
				ProteusWebSocketConnectionManager.getConnectionManager().startConnection(connection);
			} catch (IOException e) {
				e.printStackTrace();
			}
			return true;
		}

		@Override
		public ProteusWebSocketConnection getConnection() {
			// TODO Auto-generated method stub
			return null;
		}
		
	}
	
	private class HttpContextRoutedEventImpl extends HttpContextRoutedEvent {
		
		private final Socket client;
		private final ProteusHttpRequestImpl request;
		private final ClientKeepAliveEvent keepAlive;
		private final CompressionEncoding compression;
		private final String method;
		
		private final ProteusHttpResponseImpl response;
		
		public HttpContextRoutedEventImpl(ProteusServer server, Socket client, ProteusHttpRequestImpl request, ClientKeepAliveEvent keepAlive, CompressionEncoding compression, String method) throws IOException {
			this.client = client;
			this.request = request;
			this.compression = compression;
			this.method = method;
			this.keepAlive = keepAlive;
			this.response = new ProteusHttpResponseImpl(server, keepAlive, client.getOutputStream(), compression);
		}

		@Override
		protected boolean run() {
			try {
				switch(method) {
	        	case "GET":
	        	case "POST":
	        		request.getContext().handle(request, response);
	        		break;
	        	default:
	        		ErrorResponse.sendUnmodifiableErrorResponse(dispatcher, ResponseCode.METHOD_NOT_ALLOWED, client.getOutputStream());
	        		return false;
	        	}
				return true;
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		}

		@Override
		public ProteusHttpContext getContext() {
			return request.getContext();
		}

		@Override
		public HttpRoute getRoute() {
			return request.getRoute();
		}

		@Override
		public ProteusHttpRequest getRequest() {
			return request;
		}

		@Override
		public ProteusHttpResponse getResponse() {
			return response;
		}
		
	}
	
	private class ClientKeepAliveEventImpl extends ClientKeepAliveEvent {
		
		private final Socket client;
		private final CompositeRouter router;
		
		public ClientKeepAliveEventImpl(Socket client, CompositeRouter router) {
			this.client = client;
			this.router = router;
		}

		@Override
		public Socket getClientSocket() {
			return client;
		}

		@Override
		public CompositeRouter getRouter() {
			return router;
		}

		@Override
		protected boolean run() {
			startHandlerThread(client, router, "http/1.1");
			return true;
		}
		
	}
	
	private class SSLHandshakeCompletedEventImpl extends SSLHandshakeCompletedEvent {
		
		private final SSLSocket client;
		private final CompositeRouter router;
		
		public SSLHandshakeCompletedEventImpl(SSLSocket client, CompositeRouter router) {
			this.client = client;
			this.router = router;
		}

		@Override
		public SSLSocket getClientSocket() {
			return client;
		}

		@Override
		public CompositeRouter getRouter() {
			return router;
		}

		@Override
		protected boolean run() {
			new ClientAcceptEventImpl(client, router, client.getApplicationProtocol()).dispatchImmediately(dispatcher);
			return true;
		}
		
	}
	
}
