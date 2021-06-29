package net.aionstudios.proteus.websocket;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import net.aionstudios.proteus.api.context.ProteusWebSocketContext;

public class ProteusWebSocketServer extends WebSocketServer {
	
	private Map<WebSocket, ProteusWebSocketContext> active;
	
	public ProteusWebSocketServer(int port) {
		super(new InetSocketAddress(port));
		active = new HashMap<>();
	}

	@Override
	public void onClose(WebSocket conn, int code, String reason, boolean remote) {
		if (conn != null) {
			active.remove(conn);
		}
		// TODO Auto-generated method stub
	}

	@Override
	public void onError(WebSocket conn, Exception ex) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onMessage(WebSocket conn, String message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		if (conn != null) {
			active.put(conn, null); // TODO Actually get the context this is associated with.
		}
		// TODO connect to correct WebSocketContext.
	}

	@Override
	public void onStart() {
		// TODO Auto-generated method stub
		
	}

}
