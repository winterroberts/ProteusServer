package net.aionstudios.proteus;

import java.nio.charset.StandardCharsets;

import net.aionstudios.aionlog.AnsiOut;
import net.aionstudios.aionlog.Logger;
import net.aionstudios.aionlog.StandardOverride;
import net.aionstudios.aionlog.SubConsolePrefix;
import net.aionstudios.proteus.api.ProteusAPI;
import net.aionstudios.proteus.api.ProteusImplementer;
import net.aionstudios.proteus.api.context.ProteusHttpContext;
import net.aionstudios.proteus.api.context.ProteusWebSocketContext;
import net.aionstudios.proteus.configuration.EndpointConfiguration;
import net.aionstudios.proteus.configuration.EndpointType;
import net.aionstudios.proteus.request.ProteusHttpRequest;
import net.aionstudios.proteus.request.ProteusWebSocketConnection;
import net.aionstudios.proteus.request.WebSocketBuffer;
import net.aionstudios.proteus.response.ProteusHttpResponse;
import net.aionstudios.proteus.routing.Hostname;
import net.aionstudios.proteus.routing.PathInterpreter;
import net.aionstudios.proteus.routing.Router;
import net.aionstudios.proteus.routing.RouterBuilder;
import net.aionstudios.proteus.server.ProteusServer;
import net.aionstudios.proteus.server.api.ImplementerManager;
import net.aionstudios.proteus.websocket.ClosingCode;

public class Proteus {
	
	public static void main(String[] args) {
		// Pythia Console, Horae Cron
		ProteusAPI.enableBrotli();
		Logger.setup();
		AnsiOut.initialize();
		AnsiOut.setStreamPrefix("Proteus");
		AnsiOut.oneTimeSetSCP(new SubConsolePrefix() {

			@Override
			public String makeSubConsolePrefix() {
				return ImplementerManager.getInstance().getPackagePrefix(new Exception().getStackTrace()[3].getClassName());
			}
			
		});
		StandardOverride.enableOverride();
		ProteusImplementer pi = new ProteusImplementer() {

			@Override
			public Router onEnable() {
				EndpointConfiguration ec = new EndpointConfiguration(EndpointType.MIXED, 80);
				ec.getContextController().addHttpContext(new ProteusHttpContext() {

					@Override
					public void handle(ProteusHttpRequest request, ProteusHttpResponse response) {
						response.sendResponse("<html><body><h1>" + request.getPathComprehension().getPathParameters().getParameter("size") + "</h1></body></html>");
					}
					
				}, new PathInterpreter("/I/*/:size"));
				ec.getContextController().setHttpDefault(new ProteusHttpContext() {

					@Override
					public void handle(ProteusHttpRequest request, ProteusHttpResponse response) {
						response.sendResponse("<html><body><h1>Autumn</h1></body></html>");
					}
					
				});
				ec.getContextController().setWebSocketDefault(new ProteusWebSocketContext() {

					@Override
					public void onOpen(ProteusWebSocketConnection connection) {
						System.out.println("Connected: " + connection.toString());
					}

					@Override
					public void onMessage(ProteusWebSocketConnection connection, WebSocketBuffer message) {
						System.out.println(new String(message.getData(), StandardCharsets.UTF_8));
						connection.close(ClosingCode.NORMAL, new String(message.getData(), StandardCharsets.UTF_8));
					}

					@Override
					public void onClose(ProteusWebSocketConnection connection) {
						// TODO Auto-generated method stub
						
					}

					@Override
					public void onError(ProteusWebSocketConnection connection, Throwable throwable) {
						// TODO Auto-generated method stub
						
					}
					
				});
				RouterBuilder b = new RouterBuilder(ec);
				b.addHostname(Hostname.ANY);
				return b.build();
			}

			@Override
			public void onDisable() {
				// TODO Auto-generated method stub
				
			}
			
		};
		ProteusServer server = new ProteusServer(pi, pi.onEnable());
		server.start();
	}

}
