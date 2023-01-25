package net.winrob.proteus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Set;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLProtocolException;

import net.winrob.commons.saon.EventDispatcher;
import net.winrob.proteus.api.ProteusApp;
import net.winrob.proteus.api.context.ProteusHttpContext;
import net.winrob.proteus.api.context.ProteusWebSocketContext;
import net.winrob.proteus.api.event.ClientAcceptEvent;
import net.winrob.proteus.api.event.ClientKeepAliveEvent;
import net.winrob.proteus.api.event.HttpContextRoutedEvent;
import net.winrob.proteus.api.event.ListenerThreadSpawnedEvent;
import net.winrob.proteus.api.event.RequestReceivedEvent;
import net.winrob.proteus.api.event.WebSocketContextRoutedEvent;
import net.winrob.proteus.api.request.ProteusHttpRequest;
import net.winrob.proteus.api.request.ProteusHttpRequestImpl;
import net.winrob.proteus.api.request.ProteusWebSocketConnection;
import net.winrob.proteus.api.request.ProteusWebSocketConnectionImpl;
import net.winrob.proteus.api.request.ProteusWebSocketConnectionManager;
import net.winrob.proteus.api.request.ProteusWebSocketRequest;
import net.winrob.proteus.api.request.ProteusWebSocketRequestImpl;
import net.winrob.proteus.api.response.ProteusHttpResponse;
import net.winrob.proteus.api.response.ProteusHttpResponseImpl;
import net.winrob.proteus.api.response.ResponseCode;
import net.winrob.proteus.compression.CompressionEncoding;
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
				listenThread = new Thread(new Runnable() {
	
					@Override
					public void run() {
						try {
							ServerSocket server = router.createSocket();
							new ListenerThreadSpawnedEvent(server, router).dispatchImmediately(dispatcher);
							while (running) {
								Socket client = server.accept();
								new ClientAcceptEvent() {

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
										try {
											clientHandler(client, router);
										} catch(SSLProtocolException e) {
											
										} catch(SSLHandshakeException e) {
											
										} catch (IOException e) {
											e.printStackTrace();
										}
										return true;
									}
									
								}.dispatch(dispatcher);
							}
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
						
				});
				listenThread.start();
				System.out.println("Port " + router.getPort() + " opened in " + router.getType().toString() + " mode" + (router.isSecure() ? " (secure)" : "") + ".");
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
	private void clientHandler(Socket client, CompositeRouter router) throws IOException {
		InputStream inputStream = client.getInputStream();
		
		StringBuilder requestBuilder = new StringBuilder();
        String line;
	    if ((line = StreamUtils.readLine(inputStream, true)).length() < 4) {
	    	if (!client.isInputShutdown()) keepAlive(client, router);
	    	return;
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
        new RequestReceivedEvent() {

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
				routeRequest(this, client, router);
				return true;
			}
        	
        }.dispatch(dispatcher);
	}
	
	private void keepAlive(Socket client, CompositeRouter router) {
		new ClientKeepAliveEvent() {

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
				try {
					if (!client.isClosed()) {
						client.setSoTimeout(30000);
						clientHandler(client, router);
					}
				} catch(SSLProtocolException e) {
					
				} catch(SSLHandshakeException e) {
					
				} catch(SocketTimeoutException e) {
					
				} catch (IOException e) {
					e.printStackTrace();
				}
				return true;
			}
			
		}.dispatch(dispatcher);
	}
	
	private void routeRequest(RequestReceivedEvent event, Socket client, CompositeRouter router) {
		String method = event.getMethod();
		String path = event.getPath();
		String version = event.getVersion();
		ProteusHttpHeaders headers = event.getHeaders();
        if (version.equals("HTTP/1.1")) {
        	if (method.equals("GET") && event.isWebSocket()) {
        		ProteusWebSocketRequestImpl request = new ProteusWebSocketRequestImpl(client, path, headers, router);
        		if (request.routed()) {
        			new WebSocketContextRoutedEvent() {

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
							startWebSocketConnection(client, request);
							return true;
						}
        				
        			}.dispatch(dispatcher);
        		} else {
        			ErrorResponse.sendUnmodifiableErrorResponse(dispatcher, ResponseCode.NOT_FOUND, client);
	        	}
        	} else {
	        	CompressionEncoding ce = headers.hasHeader("Accept-Encoding") ? 
	    				CompressionEncoding.forAcceptHeader(headers.getHeader("Accept-Encoding").getFirst().getValue()) : 
	    					CompressionEncoding.NONE;
	        	ProteusHttpRequestImpl request = new ProteusHttpRequestImpl(client, method, version, path, headers, router);
	        	boolean keepAlive = headers.hasHeader("Connection") ? headers.getHeader("Connection").getFirst().getValue().equalsIgnoreCase("keep-alive") : false;
	        	System.out.println(keepAlive);
	        	if (request.routed()) {
	        		new HttpContextRoutedEvent() {

						@Override
						protected boolean run() {
							try {
								switch(method) {
					        	case "GET":
					        		respondWithContext(request, keepAlive, client.getOutputStream(), ce);
					        		break;
					        	case "POST":
					        		respondWithContext(request, keepAlive, client.getOutputStream(), ce);
					        		break;
					        	default:
					        		ErrorResponse.sendUnmodifiableErrorResponse(dispatcher, ResponseCode.METHOD_NOT_ALLOWED, client);
					        		return false;
					        	}
								if (keepAlive) keepAlive(client, router);
								else client.close();
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
	        			
	        		}.dispatch(dispatcher);
	        	} else {
	        		ErrorResponse.sendUnmodifiableErrorResponse(dispatcher, ResponseCode.NOT_FOUND, client);
	        	}
        	}
        } else {
        	ErrorResponse.sendUnmodifiableErrorResponse(dispatcher, ResponseCode.HTTP_VERSION_NOT_SUPPORTED, client);
        }
	}
	
	/**
	 * Directs a compiled request which matched a context on the endpoint to be handled by the context.
	 * 
	 * @param request The request which is being processed, including the context it matched.
	 * @param outputStream The output stream which the response will be written to.
	 * @param encoding The compression which will be used (as stipulated by mutual server-client support for it).
	 */
	private void respondWithContext(ProteusHttpRequest request, boolean keepAlive, OutputStream outputStream, CompressionEncoding encoding) {
		ProteusHttpResponse response = new ProteusHttpResponseImpl(this, keepAlive, outputStream, encoding);
		request.getContext().handle(request, response);
	}
	
	/**
	 * Opens a new websocket connection on a matched context.
	 * 
	 * @param client The client socket which connected.
	 * @param request The request which is being processed, including the context it matched.
	 */
	private void startWebSocketConnection(Socket client, ProteusWebSocketRequest request) {
		try {
			ProteusWebSocketConnection websocket = new ProteusWebSocketConnectionImpl(client, request);
			ProteusWebSocketConnectionManager.getConnectionManager().startConnection(websocket);
		} catch (IOException e) {
			e.printStackTrace();
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
	
}
