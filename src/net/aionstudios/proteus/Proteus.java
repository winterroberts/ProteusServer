package net.aionstudios.proteus;

import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.aionstudios.aionlog.AnsiOut;
import net.aionstudios.aionlog.Logger;
import net.aionstudios.aionlog.StandardOverride;
import net.aionstudios.aionlog.SubConsolePrefix;
import net.aionstudios.proteus.api.ProteusAPI;
import net.aionstudios.proteus.api.ProteusImplementer;
import net.aionstudios.proteus.api.context.ProteusHttpContext;
import net.aionstudios.proteus.configuration.EndpointConfiguration;
import net.aionstudios.proteus.configuration.EndpointType;
import net.aionstudios.proteus.request.ProteusHttpRequest;
import net.aionstudios.proteus.response.ProteusHttpResponse;
import net.aionstudios.proteus.routing.CompositeRouter;
import net.aionstudios.proteus.routing.Hostname;
import net.aionstudios.proteus.routing.PathInterpreter;
import net.aionstudios.proteus.routing.Router;
import net.aionstudios.proteus.routing.RouterBuilder;
import net.aionstudios.proteus.server.ProteusServer;
import net.aionstudios.proteus.server.api.ImplementerManager;

public class Proteus {
	
	private static Map<Integer, CompositeRouter> routers;
	
	private static Set<ProteusServer> servers;
	
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
		routers = new HashMap<>();
		for (ProteusImplementer implementer : ImplementerManager.getInstance().getImplementers()) {
			// Check enabled
			Router r = implementer.onEnable();
			if (r != null) {
				if (!routers.containsKey(r.getPort())) {
					routers.put(r.getPort(), new CompositeRouter(r));
				}
			}
		}
		servers = new HashSet<>();
		
		ProteusImplementer system = new ProteusImplementer() {

			@Override
			public Router onEnable() {
				Hostname host = new Hostname("10.0.0.70");
				EndpointConfiguration ec = new EndpointConfiguration(EndpointType.HTTP, 80);
				ec.getContextController().setHttpDefault(new ProteusHttpContext() {

					@Override
					public void handle(ProteusHttpRequest request, ProteusHttpResponse response) {
						response.sendResponse("<html><body><h1>Proteus HTTP v1.0.0</h1></body></html>");
					}
				
				});
				ec.getContextController().addHttpContext(new ProteusHttpContext() {
					
					@Override
					public void handle(ProteusHttpRequest request, ProteusHttpResponse response) {
						if (request.getPathComprehension().getPathParameters().hasParameter("name")) {
							byte[] decodedBytes = Base64.getDecoder().decode(request.getPathComprehension().getPathParameters().getParameter("name"));
							String decodedString = new String(decodedBytes);
							response.sendResponse("<html><body><h1>Proteus HTTP v1.0.0 " + decodedString + "</h1></body></html>");
						} else {
							response.sendResponse("<html><body><h1>Proteus HTTP v1.0.0 special</h1></body></html>");
						}
					}
					
				}, new PathInterpreter("/a/:name"), new PathInterpreter("/a"));
				RouterBuilder rb = new RouterBuilder(ec);
				rb.addHostname(host);
				return rb.build();
			}

			@Override
			public void onDisable() {
				// TODO Auto-generated method stub
				
			}
			
		};
		ProteusServer s = new ProteusServer(system.onEnable().toComposite());
		s.start();
		servers.add(s);
		
		for (Entry<Integer, CompositeRouter> e : routers.entrySet()) {
			ProteusServer server = new ProteusServer(e.getValue());
			server.start();
			servers.add(server);
		}
	}

}
