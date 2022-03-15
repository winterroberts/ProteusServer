package net.aionstudios.proteus.server.api;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

import net.aionstudios.proteus.api.ProteusImplementer;
import net.aionstudios.proteus.server.ProteusServer;

public class ImplementerManager {
	
	private static URLClassLoader ucl;
	private static ImplementerManager self;
	private static Set<ProteusImplementer> implementers;
	private static Map<ProteusImplementer, ProteusServer> serverMap;
	private static boolean discoveredImplementers = false;
	private static boolean enabledImplementers = false;
	
	private static Map<String, String> prefixMap = new HashMap<String, String>();
	
	private ImplementerManager() {
		self = this;
		implementers = new HashSet<>();
		serverMap = new HashMap<>();
		prefixMap.put("net.aionstudios.proteus", "");
	}
	
	/**
	 * Initializes a singleton of this class if it does not already exist and returns it.
	 * @return A singleton, this class.
	 */
	public static ImplementerManager getInstance() {
		return self != null ? self : new ImplementerManager();
	}
	
	/**
	 * Searches Proteus's implementers folder and loads implemnter configurations as statically located resource streams.
	 * This process registers implementers by name, adds their code to the classpath, and stores their entry point.
	 */
	public void discoverImplementers() {
		if(discoveredImplementers) {
			return;
		}
		discoveredImplementers = true;
		File folder = new File("./implementers");
		if(!folder.exists()) folder.mkdir();
		File[] listOfFiles = folder.listFiles();
		if(listOfFiles==null) {
			return;
		}
		List<URL> jarUrls = new ArrayList<URL>();
		for(File f : listOfFiles) {
			if(f.getName().endsWith(".jar")) {
				try {
					jarUrls.add(f.toURI().toURL());
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
			}
		}
		ucl = new URLClassLoader(jarUrls.toArray(new URL[0]), ImplementerManager.class.getClassLoader());
		List<String> iPaths = new ArrayList<String>();
		for(InputStream is : loadImplementerConfigs()) {
			BufferedReader br = new BufferedReader(new InputStreamReader(is)); 
			String json = "";
			String line = "";
			try {
				line = br.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
            while (line != null) {
                json = json.concat(line);
                try {
					line = br.readLine();
				} catch (IOException e) {
					e.printStackTrace();
				}
            }
            try {
				JSONObject j = new JSONObject(json);
				if(j.has("path")) {
					String pa = j.getString("path");
					if(pa.startsWith("net.aionstudios.proteus")) {
						System.err.println("Failed registering a implementer path. Namespace contained a system duplicate.");
						System.exit(0);
					}
					String packagePrefix = pa.substring(0, pa.lastIndexOf("."));
					for(String p:prefixMap.keySet()) {
						if(p.startsWith(packagePrefix)||packagePrefix.startsWith(p)) {
							System.err.println("Failed registering a implementer path. Namespace contained a implementer duplicate.");
							System.exit(0);
						}
					}
					if(j.has("implementer")) {
						String ni = j.getString("implementer");
						for(String key:prefixMap.keySet()) {
							if(prefixMap.get(key).equals(ni)||ni.equals("")||ni.equals("Proteus")) {
								System.err.println("Failed registering a implementer path. Namespace assigned prefix is a duplicate.");
								System.exit(0);
							}
						}
						prefixMap.put(packagePrefix, ni);
					}
					iPaths.add(pa);
				}
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		for(String path : iPaths) {
			importImplementer(path);
		}
	}
	
	/**
	 * @param pkg	The package of a class for which the prefix is requested.
	 * @return		The prefix or the package name itself if none is registered.
	 */
	public String getPackagePrefix(String pkg) {
		for(String prefix:prefixMap.keySet()) {
			if(pkg.startsWith(prefix)) {
				return prefixMap.get(prefix).concat("");
			}
		}
		return pkg;
	}
	
	public Set<ProteusImplementer> getImplementers() {
		return implementers;
	}
	
	/**
	 * Enables implementers by calling their entry methods if not already run.
	 * Does not currently implement the ability to disable implementers.
	 */
	public void enableImplementers() {
		if(!enabledImplementers) {
			for(ProteusImplementer h : implementers) {
				h.onEnable();
			}
			enabledImplementers = true;
		}
	}
	
	/**
	 * Disables implementers by calling their closing methods if running.
	 * This process does not need to ensure that implementers actually stop as it should only occur when the host software closes the server.
	 */
	public void disableImplementers() {
		if(enabledImplementers) {
			for(ProteusImplementer h : implementers) {
				h.onDisable();
				if (serverMap.containsKey(h)) {
					serverMap.get(h);
				}
			}
			enabledImplementers = false;
		}
	}
	
	/**
	 * Loads the implementer config files for each registered jar.
	 * @return A list of InputStreams, reading from each jar's config.
	 */
	private List<InputStream> loadImplementerConfigs() {
	    final List<InputStream> list = new ArrayList<InputStream>();
	    Enumeration<URL> systemResources;
		try {
			systemResources = ucl.getResources("proteus.json");
			while (systemResources.hasMoreElements()) {
		        list.add(systemResources.nextElement().openStream());
		    }
		} catch (IOException e) {
			e.printStackTrace();
		}
	    return list;
	}
	
	/**
	 * Loads a implementer that has already been registered in the classpath without instantiation.
	 * @param iPath
	 */
	private void importImplementer(String iPath) {
		try {
			Class<?> classToLoad = Class.forName(iPath, true, ucl);
			//A neat trick to prevent disabled implementers from running their code.
			implementers.add((ProteusImplementer) ObjectInstantiator.getInstance().newInstance(classToLoad));
			System.out.println("Success loading implementer: "+iPath);
		} catch (ClassNotFoundException e) {
			System.err.println("Failed loading implementer at '"+iPath+"'!");
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * @return The names of all implementers from the prefix map, excluding tags for Proteus core components.
	 */
	public String[] getImplementerNames() {
		String[] nis = new String[prefixMap.size()];
		for(int i = 2; i < prefixMap.size(); i++) {
			nis[i] = prefixMap.get(prefixMap.keySet().toArray()[i]);
		}
		return nis;
	}
	
}