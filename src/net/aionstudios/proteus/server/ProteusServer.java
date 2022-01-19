package net.aionstudios.proteus.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import net.aionstudios.proteus.api.ProteusImplementer;
import net.aionstudios.proteus.api.util.StreamUtils;
import net.aionstudios.proteus.compression.CompressionEncoding;
import net.aionstudios.proteus.error.ErrorResponse;
import net.aionstudios.proteus.header.ProteusHeaderBuilder;
import net.aionstudios.proteus.header.ProteusHttpHeaders;
import net.aionstudios.proteus.request.ProteusHttpRequest;
import net.aionstudios.proteus.request.ProteusWebSocketConnection;
import net.aionstudios.proteus.request.ProteusWebSocketConnectionManager;
import net.aionstudios.proteus.request.ProteusWebSocketRequest;
import net.aionstudios.proteus.response.ProteusHttpResponse;
import net.aionstudios.proteus.response.ResponseCode;
import net.aionstudios.proteus.routing.CompositeRouter;

public class ProteusServer {
	
	private CompositeRouter router;
	
	private ServerSocket server;
	private Thread listenThread;
	
	private boolean running;
	private boolean stopped;
	
	private Executor executor;
	
	public ProteusServer(CompositeRouter router) {
		this.router = router;
		running = false;
		stopped = true;
		executor = Executors.newCachedThreadPool();
	}
	
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
						ProteusWebSocketConnectionManager.getConnectionManager().closeAll();
						stopped = true;
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
					
			});
			listenThread.start();
			System.out.println("Port " + router.getPort() + " opened in " + router.getType().toString() + " mode.");
		}
	}
	
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

        ProteusHeaderBuilder headerBuilder = new ProteusHeaderBuilder();
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
        		ProteusWebSocketRequest request = new ProteusWebSocketRequest(client, path, host, headers, router);
        		if (request.routed()) {
        			startWebSocketConnection(client, request);
        		} else {
        			ErrorResponse.sendUnmodifiableErrorResponse(ResponseCode.NOT_FOUND, client);
	        	}
        	} else {
	        	CompressionEncoding ce = headers.hasHeader("Accept-Encoding") ? 
	    				CompressionEncoding.forAcceptHeader(headers.getHeader("Accept-Encoding").getFirst().getValue()) : 
	    					CompressionEncoding.NONE;
	        	ProteusHttpRequest request = new ProteusHttpRequest(client, method, version, path, host, headers, router);
	        	if (request.routed()) {
		        	switch(method) {
		        	case "GET":
		        		respondWithContext(request, client.getOutputStream(), ce);
		        		break;
		        	case "POST":
		        		respondWithContext(request, client.getOutputStream(), ce);
		        		break;
		        	default:
		        		ErrorResponse.sendUnmodifiableErrorResponse(ResponseCode.METHOD_NOT_ALLOWED, client);
		        	}
	        	} else {
	        		ErrorResponse.sendUnmodifiableErrorResponse(ResponseCode.NOT_FOUND, client);
	        	}
        	}
        } else {
        	ErrorResponse.sendUnmodifiableErrorResponse(ResponseCode.HTTP_VERSION_NOT_SUPPORTED, client);
        }
	}
	
	private void respondWithContext(ProteusHttpRequest request, OutputStream outputStream, CompressionEncoding encoding) {
		ProteusHttpResponse response = new ProteusHttpResponse(outputStream, encoding);
		request.getContext().handle(request, response);
	}
	
	private void startWebSocketConnection(Socket client, ProteusWebSocketRequest request) {
		try {
			ProteusWebSocketConnection websocket = new ProteusWebSocketConnection(client, request);
			ProteusWebSocketConnectionManager.getConnectionManager().startConnection(websocket);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void stop() {
		if (running) {
			running = false;
		}
	}
	
	public boolean isRunning() {
		return running;
	}
	
	public boolean isStopped() {
		return stopped;
	}
	
	public CompositeRouter getRouter() {
		return router;
	}
	
}
