package net.aionstudios.proteus.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import net.aionstudios.proteus.api.ProteusAPI;
import net.aionstudios.proteus.api.ProteusImplementer;
import net.aionstudios.proteus.api.context.ProteusHttpContext;
import net.aionstudios.proteus.api.util.StreamUtils;
import net.aionstudios.proteus.compression.CompressionEncoding;
import net.aionstudios.proteus.configuration.EndpointConfiguration;
import net.aionstudios.proteus.error.ErrorResponse;
import net.aionstudios.proteus.header.ProteusHeaderBuilder;
import net.aionstudios.proteus.header.ProteusHttpHeaders;
import net.aionstudios.proteus.request.ProteusHttpRequest;
import net.aionstudios.proteus.response.ProteusHttpResponse;
import net.aionstudios.proteus.response.ResponseCode;

public class ProteusServer {
	
	private ProteusImplementer implementer;
	private EndpointConfiguration configuration;
	
	private ServerSocket server;
	private Thread listenThread;
	
	private boolean running;
	private boolean stopped;
	
	private Executor executor;

	public ProteusServer(ProteusImplementer implementer, EndpointConfiguration configuration) {
		this.implementer = implementer;
		this.configuration = configuration;
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
						server = new ServerSocket(configuration.getPort());
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
						stopped = true;
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
					
			});
			listenThread.start();
			System.out.println("Server started!");
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
        
        CompressionEncoding ce = headers.hasHeader("Accept-Encoding") ? 
				CompressionEncoding.forAcceptHeader(headers.getHeader("Accept-Encoding").getFirst().getValue()) : 
					CompressionEncoding.NONE;
        
        if (version.equals("HTTP/1.1")) {
        	ProteusHttpRequest request = new ProteusHttpRequest(client, method, version, path, host, headers);
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
        	ErrorResponse.sendUnmodifiableErrorResponse(ResponseCode.HTTP_VERSION_NOT_SUPPORTED, client);
        }
	}
	
	private void respondWithContext(ProteusHttpRequest request, OutputStream outputStream, CompressionEncoding encoding) {
		String path = request.getPath();
		ProteusHttpContext context = configuration.getContextController().getHttpContext(path);
		boolean defContext = false;
		if (context == null) {
			context = configuration.getContextController().getHttpDefault();
			defContext = true;
		}
		if (context != null && (defContext || context.pathMatch(path))) {
			ProteusHttpResponse response = new ProteusHttpResponse(outputStream, encoding);
			context.handle(request, response);
			// TODO this returns a gz download and it shouldnt
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
	
	public ProteusImplementer getImplementer() {
		return implementer;
	}
	
	public EndpointConfiguration getConfiguration() {
		return configuration;
	}
	
}
