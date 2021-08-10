package net.aionstudios.proteus.websocket;

import java.io.IOException;

import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;

import net.aionstudios.proteus.configuration.WebSocketConfiguration;
import net.aionstudios.proteus.configuration.WebSocketLoopbackConfiguration;

public class ProteusWebSocket {
	
	private WebSocketConfiguration configuration;
	private ProteusWebSocketServer webSocketServer;
	
	private boolean running = false;
	
	public ProteusWebSocket(WebSocketConfiguration configuration) {
		this.configuration = configuration;
	}
	
	public void start() {
		if (!running) {
			if (configuration instanceof WebSocketLoopbackConfiguration) {
				webSocketServer = new ProteusWebSocketServer(configuration.getPort(), true);
			} else {
				webSocketServer = new ProteusWebSocketServer(configuration.getPort());
			}
			if (configuration.getSslContext() != null) webSocketServer.setWebSocketFactory(new DefaultSSLWebSocketServerFactory(configuration.getSslContext()));
			webSocketServer.start();
			System.out.println((configuration.isSecure() ? "Secure " : "") + "Web Socket server started on port " + configuration.getPort());
			// TODO get implementer name to display start path
			running  = true;
		}
	}
	
	public void stop() {
		if (running) {
			try {
				webSocketServer.stop();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			running = false;
		}
	}
	
	public WebSocketConfiguration getConfiguration() {
		return configuration;
	}

}
