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

import net.aionstudios.proteus.api.ProteusPlugin;
import net.aionstudios.proteus.server.ProteusServer;

public class PluginManager {
	
	private static URLClassLoader ucl;
	private static PluginManager self;
	private static Set<ProteusPlugin> plugins;
	private static Map<ProteusPlugin, Set<ProteusServer>> serverMap;
	private static boolean discoveredPlugins = false;
	private static boolean enabledPlugins = false;
	
	private static Map<String, String> prefixMap = new HashMap<String, String>();
	
	private PluginManager() {
		self = this;
		plugins = new HashSet<>();
		serverMap = new HashMap<>();
		prefixMap.put("net.aionstudios.proteus", "");
	}
	
	/**
	 * Initializes a singleton of this class if it does not already exist and returns it.
	 * @return A singleton, this class.
	 */
	public static PluginManager getInstance() {
		return self != null ? self : new PluginManager();
	}
	
	/**
	 * Searches Proteus's plugins folder and loads plugin configurations as statically located resource streams.
	 * This process registers plugins by name, adds their code to the classpath, and stores their entry point.
	 */
	public void discoverPlugins() {
		if(discoveredPlugins) {
			return;
		}
		discoveredPlugins = true;
		File folder = new File("./plugins");
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
		ucl = new URLClassLoader(jarUrls.toArray(new URL[0]), PluginManager.class.getClassLoader());
		List<String> pluginPaths = new ArrayList<String>();
		for(InputStream is : loadPluginConfigs()) {
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
						System.err.println("Failed registering a plugin path. Namespace contained a system duplicate.");
						System.exit(0);
					}
					String packagePrefix = pa.substring(0, pa.lastIndexOf("."));
					for(String p:prefixMap.keySet()) {
						if(p.startsWith(packagePrefix)||packagePrefix.startsWith(p)) {
							System.err.println("Failed registering a plugin path. Namespace contained a plugin duplicate.");
							System.exit(0);
						}
					}
					if(j.has("plugin")) {
						String ni = j.getString("plugin");
						for(String key:prefixMap.keySet()) {
							if(prefixMap.get(key).equals(ni)||ni.equals("")||ni.equals("Proteus")) {
								System.err.println("Failed registering a plugin path. Namespace assigned prefix is a duplicate.");
								System.exit(0);
							}
						}
						prefixMap.put(packagePrefix, ni);
					}
					pluginPaths.add(pa);
				}
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		for(String path : pluginPaths) {
			injectClasspath(path);
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
	
	public Set<ProteusPlugin> getPlugins() {
		return plugins;
	}
	
	/**
	 * Enables plugins by calling their entry methods if not already run.
	 * Does not currently implement the ability to disable plugins.
	 */
	public void enablePlugins() {
		if(!enabledPlugins) {
			for(ProteusPlugin h : plugins) {
				h.onEnable(null);
			}
			enabledPlugins = true;
		}
	}
	
	/**
	 * Disables plugins by calling their closing methods if running.
	 * This process does not need to ensure that plugins actually stop as it should only occur when the host software closes the server.
	 */
	public void disablePlugins() {
		if(enabledPlugins) {
			for(ProteusPlugin h : plugins) {
				h.onDisable();
				if (serverMap.containsKey(h)) {
					serverMap.get(h);
				}
			}
			enabledPlugins = false;
		}
	}
	
	/**
	 * Loads the plugin config files for each registered jar.
	 * @return A list of InputStreams, reading from each jar's config.
	 */
	private List<InputStream> loadPluginConfigs() {
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
	 * Loads a plugin that has already been registered in the classpath without instantiation.
	 * @param iPath
	 */
	private void injectClasspath(String pluginPath) {
		try {
			Class<?> classToLoad = Class.forName(pluginPath, true, ucl);
			//A neat trick to prevent disabled plugins from running their code.
			plugins.add((ProteusPlugin) ObjectInstantiator.getInstance().newInstance(classToLoad));
			System.out.println("Success loading plugin: " + pluginPath);
		} catch (ClassNotFoundException e) {
			System.err.println("Failed loading plugin at '" + pluginPath + "'!");
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * @return The names of all plugins from the prefix map, excluding tags for Proteus core components.
	 */
	public String[] getPluginNames() {
		String[] nis = new String[prefixMap.size()];
		for(int i = 2; i < prefixMap.size(); i++) {
			nis[i] = prefixMap.get(prefixMap.keySet().toArray()[i]);
		}
		return nis;
	}
	
}