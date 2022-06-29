package net.aionstudios.proteus.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import net.aionstudios.proteus.api.context.ProteusHttpContext;
import net.aionstudios.proteus.api.context.ProteusWebSocketContext;
import net.aionstudios.proteus.api.event.HttpContextRoutedEvent;
import net.aionstudios.proteus.api.event.WebSocketContextRoutedEvent;
import net.aionstudios.proteus.api.request.ProteusHttpRequest;
import net.aionstudios.proteus.api.request.ProteusHttpRequestImpl;
import net.aionstudios.proteus.api.request.ProteusWebSocketConnection;
import net.aionstudios.proteus.api.request.ProteusWebSocketConnectionImpl;
import net.aionstudios.proteus.api.request.ProteusWebSocketConnectionManager;
import net.aionstudios.proteus.api.request.ProteusWebSocketRequest;
import net.aionstudios.proteus.api.request.ProteusWebSocketRequestImpl;
import net.aionstudios.proteus.api.response.ProteusHttpResponse;
import net.aionstudios.proteus.api.response.ProteusHttpResponseImpl;
import net.aionstudios.proteus.api.response.ResponseCode;
import net.aionstudios.proteus.compression.CompressionEncoding;
import net.aionstudios.proteus.error.ErrorResponse;
import net.aionstudios.proteus.header.ProteusHeaderBuilder;
import net.aionstudios.proteus.header.ProteusHttpHeaders;
import net.aionstudios.proteus.routing.CompositeRouter;
import net.aionstudios.proteus.routing.HttpRoute;
import net.aionstudios.proteus.routing.WebSocketRoute;
import net.aionstudios.proteus.util.StreamUtils;
import net.winrob.commons.saon.EventDispatcher;

/**
 * Server class which sets up server socket and listens for new connections.
 * 
 * @author Winter Roberts
 *
 */
public class ProteusServer {
	
	private CompositeRouter router;
	
	private ServerSocket server;
	private Thread listenThread;
	
	private boolean running;
	private boolean stopped;
	
	private Executor executor;
	
	private EventDispatcher dispatcher;
	
	/**
	 * Creates a new server which listens according to the router(s) provided.
	 * 
	 * @param router A {@link CompositeRouter}, which may only contain one {@link Router}.
	 */
	public ProteusServer(CompositeRouter router) {
		this.router = router;
		running = false;
		stopped = true;
		executor = Executors.newCachedThreadPool();
		dispatcher = new EventDispatcher("Proteus");
	}
	
	/**
	 * Opens listener threads and registers closing hooks.
	 */
	public void start() {
		if (!running) {
			running = true;
			stopped = false;
			listenThread = new Thread(new Runnable() {

				@Override
				public void run() {
					try {
						server = new ServerSocket(router.getPort());
						while (running) {
							Socket client = server.accept();
							executor.execute(new Runnable() {

								@Override
								public void run() {
									try {
										clientHandler(client);
									} catch (IOException e) {
										e.printStackTrace();
									}
								}
							
							});
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
					
			});
			listenThread.start();
			System.out.println("Port " + router.getPort() + " opened in " + router.getType().toString() + " mode.");
		}
	}
	
	/**
	 * Handle a new client connection, which must be an HTTP/1.1 connection (but may be a websocket upgrade request).
	 * 
	 * @param client The new client connection to be handled.
	 * @throws IOException If there is an error reading from the socket.
	 */
	private void clientHandler(Socket client) throws IOException {
		InputStream inputStream = client.getInputStream();
		
		StringBuilder requestBuilder = new StringBuilder();
        String line;
        while (inputStream.available() == 0) {
        	
        }
        while (!(line = StreamUtils.readLine(inputStream, true)).isBlank()) {
        	requestBuilder.append(line + "\r\n");
        }
        
        String requestString = requestBuilder.toString();
        String[] requestsLines = requestString.split("\r\n");
        String[] requestLine = requestsLines[0].split(" ");
        String method = requestLine[0];
        String path = requestLine[1];
        String version = requestLine[2];
        String host = requestsLines[1].split(" ")[1];

        ProteusHeaderBuilder headerBuilder = ProteusHeaderBuilder.newBuilder();
        for (int h = 2; h < requestsLines.length; h++) {
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
        
        if (version.equals("HTTP/1.1")) {
        	if (method.equals("GET") && headers.hasHeader("Sec-WebSocket-Key")) {
        		ProteusWebSocketRequestImpl request = new ProteusWebSocketRequestImpl(client, path, host, headers, router);
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
        			ErrorResponse.sendUnmodifiableErrorResponse(ResponseCode.NOT_FOUND, client);
	        	}
        	} else {
	        	CompressionEncoding ce = headers.hasHeader("Accept-Encoding") ? 
	    				CompressionEncoding.forAcceptHeader(headers.getHeader("Accept-Encoding").getFirst().getValue()) : 
	    					CompressionEncoding.NONE;
	        	ProteusHttpRequestImpl request = new ProteusHttpRequestImpl(client, method, version, path, host, headers, router);
	        	if (request.routed()) {
	        		new HttpContextRoutedEvent() {

						@Override
						protected boolean run() {
							try {
								switch(method) {
					        	case "GET":
					        		respondWithContext(request, client.getOutputStream(), ce);
					        		break;
					        	case "POST":
					        		respondWithContext(request, client.getOutputStream(), ce);
					        		break;
					        	default:
					        		ErrorResponse.sendUnmodifiableErrorResponse(ResponseCode.METHOD_NOT_ALLOWED, client);
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
	        			
	        		}.dispatch(dispatcher);
	        	} else {
	        		ErrorResponse.sendUnmodifiableErrorResponse(ResponseCode.NOT_FOUND, client);
	        	}
        	}
        } else {
        	ErrorResponse.sendUnmodifiableErrorResponse(ResponseCode.HTTP_VERSION_NOT_SUPPORTED, client);
        }
	}
	
	/**
	 * Directs a compiled request which matched a context on the endpoint to be handled by the context.
	 * 
	 * @param request The request which is being processed, including the context it matched.
	 * @param outputStream The output stream which the response will be written to.
	 * @param encoding The compression which will be used (as stipulated by mutual server-client support for it).
	 */
	private void respondWithContext(ProteusHttpRequest request, OutputStream outputStream, CompressionEncoding encoding) {
		ProteusHttpResponse response = new ProteusHttpResponseImpl(outputStream, encoding);
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
	 * @return The router being used by the server.
	 */
	public CompositeRouter getRouter() {
		return router;
	}
	
}
