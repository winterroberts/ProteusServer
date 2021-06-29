package net.aionstudios.proteus.server;

import java.util.HashSet;
import java.util.Set;

import net.aionstudios.proteus.api.ProteusImplementer;
import net.aionstudios.proteus.configuration.EndpointConfiguration;
import net.aionstudios.proteus.configuration.HttpConfiguration;
import net.aionstudios.proteus.configuration.WebSocketConfiguration;
import net.aionstudios.proteus.http.ProteusHttp;
import net.aionstudios.proteus.websocket.ProteusWebSocket;

public class ProteusServer {
	
	private ProteusImplementer implementer;
	
	private Set<ProteusHttp> httpServers;
	private Set<ProteusWebSocket> webSocketServers;
	
	private boolean running = false;
	
	public ProteusServer(ProteusImplementer application, EndpointConfiguration... configurations) {
		implementer = application;
		httpServers = new HashSet<>();
		webSocketServers = new HashSet<>();
		for (EndpointConfiguration ec : configurations) {
			if (ec instanceof HttpConfiguration) {
				httpServers.add(new ProteusHttp((HttpConfiguration) ec));
			} else if (ec instanceof WebSocketConfiguration) {
				webSocketServers.add(new ProteusWebSocket((WebSocketConfiguration) ec));
			} else {
				// TODO error
			}
		}
	}
	
	public void start() {
		if (!running) {
			for (ProteusHttp http : httpServers) {
				http.start();
			}
			for (ProteusWebSocket websocket : webSocketServers) {
				websocket.start();
			}
		}
	}
	
	public void stop() {
		if (running) {
			for (ProteusHttp http : httpServers) {
				http.stop();
			}
			for (ProteusWebSocket websocket : webSocketServers) {
				websocket.stop();
			}
		}
	}
	
	public ProteusImplementer getImplementer() {
		return implementer;
	}

}
