package net.aionstudios.proteus;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
import net.aionstudios.proteus.api.ProteusApp;
import net.aionstudios.proteus.api.ProteusPlugin;
import net.aionstudios.proteus.api.context.ProteusHttpContext;
import net.aionstudios.proteus.api.event.HttpContextRoutedEvent;
import net.aionstudios.proteus.api.request.ProteusHttpRequest;
import net.aionstudios.proteus.api.response.ProteusHttpResponse;
import net.aionstudios.proteus.configuration.EndpointConfiguration;
import net.aionstudios.proteus.configuration.EndpointType;
import net.aionstudios.proteus.routing.CompositeRouter;
import net.aionstudios.proteus.routing.Hostname;
import net.aionstudios.proteus.routing.PathInterpreter;
import net.aionstudios.proteus.routing.Router;
import net.aionstudios.proteus.routing.RouterBuilder;
import net.aionstudios.proteus.server.api.PluginManager;
import net.winrob.commons.saon.EventDispatcher;
import net.winrob.commons.saon.EventListener;

public class Proteus {
	
	private static boolean init = false;
	
	private static Map<Class<? extends ProteusApp>, ProteusServer> servers;
	
	public static void init() {
		if (init) return;
		// Pythia Console, Horae Cron
		ProteusAPI.enableBrotli();
		Logger.setup();
		AnsiOut.initialize();
		AnsiOut.oneTimeSetSCP(new SubConsolePrefix() {

			@Override
			public String makeSubConsolePrefix() {
				return PluginManager.getInstance().getPackagePrefix(new Exception().getStackTrace()[3].getClassName());
			}
			
		});
		StandardOverride.enableOverride();
		
		servers = new HashMap<>();
		init = true;
	}
	
	protected static void addServer(ProteusServer server) {
		servers.put(server.getApp(), server);
	}
	
	protected static void removeServer(ProteusServer server) {
		servers.remove(server.getApp());
	}
	
	public class ProteusTestApp implements ProteusApp {

		@Override
		public Set<CompositeRouter> build() {
			Hostname host = new Hostname("127.0.0.1");
			EndpointConfiguration ec = new EndpointConfiguration(EndpointType.HTTP, 80);
			ec.getContextController().addHttpContext(new ProteusHttpContext() {
				
				@Override
				public void handle(ProteusHttpRequest request, ProteusHttpResponse response) {
					if (request.getPathComprehension().getPathParameters().hasParameter("name")) {
						String decodedString = URLDecoder.decode(request.getPathComprehension().getPathParameters().getParameter("name"), StandardCharsets.UTF_8);
						response.sendResponse("<html><body><h1>Proteus HTTP v1.0.0 " + decodedString + "</h1></body></html>");
					} else {
						response.sendResponse("<html><body><h1>Proteus HTTP v1.0.0 special</h1></body></html>");
					}
				}
				
			}, new PathInterpreter("/a/:name"), new PathInterpreter("/a"));
			RouterBuilder rb = new RouterBuilder();
			rb.addHostname(host);
			return Set.of(rb.build(ec).toComposite());
		}
		
	}

}
