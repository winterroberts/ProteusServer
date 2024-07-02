package net.winrob.proteus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.ECGenParameterSpec;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;

import net.winrob.aionlog.AnsiOut;
import net.winrob.aionlog.Logger;
import net.winrob.aionlog.StandardOverride;
import net.winrob.aionlog.SubConsolePrefix;
import net.winrob.commons.pythia.ConsoleCommandLine;
import net.winrob.proteus.api.ProteusAPI;
import net.winrob.proteus.api.ProteusApp;
import net.winrob.proteus.api.context.ProteusHttpContext;
import net.winrob.proteus.api.fileio.MimeType;
import net.winrob.proteus.api.request.ProteusHttpRequest;
import net.winrob.proteus.api.response.ProteusHttpResponse;
import net.winrob.proteus.configuration.EndpointConfiguration;
import net.winrob.proteus.configuration.EndpointType;
import net.winrob.proteus.publish.PublishContext;
import net.winrob.proteus.routing.CompositeRouter;
import net.winrob.proteus.routing.Hostname;
import net.winrob.proteus.routing.PathInterpreter;
import net.winrob.proteus.routing.RouterBuilder;
import net.winrob.proteus.secure.KeyStoreLoader;
import net.winrob.proteus.server.api.PluginManager;

public class Proteus {
	
	private static boolean init = false;
	
	private static Map<Class<? extends ProteusApp>, ProteusServer> servers;
	
	public static void main(String[] args) {
		init();
		new ProteusServer(ProteusTestApp.class).start();
	}
	
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
		ConsoleCommandLine cli = ConsoleCommandLine.getInstance();
		//cli.addCommand("stop", null);
		cli.startConsoleThread();
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
			Hostname host = new Hostname("localhost");
			EndpointConfiguration ec = new EndpointConfiguration(443, Set.of(EndpointType.HTTP1_1, EndpointType.HTTP2));
			ec.getContextController().addHttpContext(new ProteusHttpContext() {
				
				@Override
				public void handle(ProteusHttpRequest request, ProteusHttpResponse response) {
					if (request.getPathComprehension().getPathParameters().hasParameter("name")) {
						try {
							response.setMimeString(MimeType.getInstance().getMimeString("png"));
							response.sendResponse(new FileInputStream(new File("C:/Users/wrpar/Downloads/z.png")));
						} catch (FileNotFoundException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					} else {
						response.sendResponse("<html><body><h1>Proteus HTTP v1.0.0 special</h1></body></html>");
					}
				}
				
			}, new PathInterpreter("/a/:name"), new PathInterpreter("/a"));
			
			RouterBuilder rb = new RouterBuilder();
			rb.addHostname(host);
			
			File keystore = new File("C:/Users/wrpar/dev/git/proteus/ssl/localhost.jks");
			if (!keystore.exists()) {
				System.out.println("localhost.jks not loaded!");
			}
			SSLServerSocketFactory sslFactory = null;
			try {
				sslFactory = KeyStoreLoader.loadKeyStoreToSSLSocketFactory(keystore, "123456", "123456", "localhost");
			} catch (UnrecoverableKeyException | KeyManagementException | NoSuchAlgorithmException
					| CertificateException | KeyStoreException | IOException | InvalidAlgorithmParameterException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return Set.of(rb.build(ec).toComposite(sslFactory));
		}
		
	}

}
