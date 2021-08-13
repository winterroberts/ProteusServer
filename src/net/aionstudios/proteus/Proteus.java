package net.aionstudios.proteus;

import java.io.IOException;

import net.aionstudios.aionlog.AnsiOut;
import net.aionstudios.aionlog.Logger;
import net.aionstudios.aionlog.StandardOverride;
import net.aionstudios.aionlog.SubConsolePrefix;
import net.aionstudios.proteus.api.ProteusAPI;
import net.aionstudios.proteus.api.ProteusImplementer;
import net.aionstudios.proteus.api.context.ProteusContext;
import net.aionstudios.proteus.api.context.ProteusHttpContext;
import net.aionstudios.proteus.configuration.EndpointConfiguration;
import net.aionstudios.proteus.configuration.EndpointType;
import net.aionstudios.proteus.fileio.MimeType;
import net.aionstudios.proteus.request.MultipartFileStream;
import net.aionstudios.proteus.request.ProteusHttpRequest;
import net.aionstudios.proteus.response.ProteusHttpResponse;
import net.aionstudios.proteus.response.ResponseCode;
import net.aionstudios.proteus.server.ProteusServer;
import net.aionstudios.proteus.server.api.ImplementerManager;

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
			public EndpointConfiguration[] onEnable() {
				EndpointConfiguration ec = new EndpointConfiguration(EndpointType.HTTP, 80);
				ec.getContextController().setHttpDefault(new ProteusHttpContext() {

					@Override
					public void handle(ProteusHttpRequest request, ProteusHttpResponse response) {
						MultipartFileStream file = request.getRequestBody().getFiles().getParameter("d");
						try {
							byte[] bytes = file.readNBytes(file.getSize());
							response.setMimeString(MimeType.getInstance().getMimeString("jpg"));
							response.sendResponse(ResponseCode.OK, bytes);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					
				});
				return new EndpointConfiguration[] { ec };
			}

			@Override
			public void onDisable() {
				// TODO Auto-generated method stub
				
			}
			
		};
		ProteusServer server = new ProteusServer(pi, pi.onEnable()[0]);
		server.start();
	}

}
