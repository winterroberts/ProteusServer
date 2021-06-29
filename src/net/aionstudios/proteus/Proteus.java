package net.aionstudios.proteus;

import net.aionstudios.aionlog.AnsiOut;
import net.aionstudios.aionlog.Logger;
import net.aionstudios.aionlog.StandardOverride;
import net.aionstudios.aionlog.SubConsolePrefix;
import net.aionstudios.proteus.api.ProteusImplementer;
import net.aionstudios.proteus.api.context.ProteusContext;
import net.aionstudios.proteus.api.context.ProteusHttpContext;
import net.aionstudios.proteus.configuration.EndpointConfiguration;
import net.aionstudios.proteus.configuration.HttpConfiguration;
import net.aionstudios.proteus.request.ProteusHttpRequest;
import net.aionstudios.proteus.request.ProteusHttpResponse;
import net.aionstudios.proteus.server.ProteusServer;
import net.aionstudios.proteus.server.api.ImplementerManager;

public class Proteus {
	
	public static void main(String[] args) {
		System.setProperty("java.net.preferIPv4Stack" , "true");
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
			public EndpointConfiguration[] onEnable() {
				return new EndpointConfiguration[] { new HttpConfiguration(80, false, new ProteusHttpContext() {

					@Override
					public void handle(ProteusHttpRequest request) {
						ProteusHttpResponse r = ProteusHttpResponse.getResponserForRequest(request);
						r.setMimeString("text/html");
						r.sendResponse("<html>"
								+ "<body>"
								+ "<h1>Dominique</h1>"
								+ "</body>"
								+ "</html>");
					}
					
				}, new @ProteusContext(path={"/p", "/g"}, preserveType=true) H() {} )};
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
