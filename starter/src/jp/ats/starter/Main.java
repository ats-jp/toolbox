package jp.ats.starter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

public class Main {

	private static final String relativeLocation = "/jp/ats/starter/Main.class";

	private static final int schemaLength = "jar:file:/".length();

	private static final String jarName = "starter.jar";

	private static final String propertiesFileName = "starter.properties";

	private static final String classesDirKeyName = "classes-dir";

	private static final String defaultClassesDirName = "classes";

	private static final String libDirKeyName = "lib-dir";

	private static final String defaultLibDirName = "lib";

	private static final String profilerOutputDirKeyName = "profiler-output-dir";

	private static final String defaultProfilerOutputDir = "profiler";

	private static final String mainClassKeyName = "main-class";

	private static final String profilerClassName = "jp.ats.profiler.Profiler";

	private static final String useProfilerKeyName = "use-profiler";

	private static final String profilerTargetClassPrefixesKeyName = "profiler-target-class-prefixes";

	public static final String homePath;

	private static final Properties properties;

	static {
		String selfPath = Main.class.getResource(relativeLocation).toString();
		String filePath = selfPath.substring(schemaLength);
		try {
			String myHomePath = URLDecoder
					.decode(filePath.substring(0,
							filePath.indexOf(jarName + "!" + relativeLocation)),
							"UTF-8");
			//Windows UNC ƒpƒX–¼
			String uncPath = '/' + myHomePath;
			if (new File(myHomePath, propertiesFileName).exists()) {
				homePath = myHomePath;
			} else if (new File(uncPath, propertiesFileName).exists()) {
				homePath = uncPath;
			} else {
				throw new IllegalStateException();
			}

			properties = new Properties();
			properties.load(new FileInputStream(new File(homePath,
					propertiesFileName)));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	public static String getHomePath() {
		return homePath;
	}

	public static String getProperty(String key) {
		return properties.getProperty(key);
	}

	public static void main(String[] args) throws Exception {
		boolean useProfiler = Boolean.valueOf(
				properties.getProperty(useProfilerKeyName, "false"))
				.booleanValue();

		List<File> files = new LinkedList<File>();
		List<URL> urls = new LinkedList<URL>();
		{
			StringTokenizer tokenizer = new StringTokenizer(
					properties.getProperty(classesDirKeyName,
							defaultClassesDirName), ",");

			while (tokenizer.hasMoreTokens()) {
				File classes = new File(homePath, tokenizer.nextToken());
				if (classes.exists()) {
					files.add(classes);
					urls.add(classes.toURI().toURL());
				}
			}
		}

		{
			StringTokenizer tokenizer = new StringTokenizer(
					properties.getProperty(libDirKeyName, defaultLibDirName),
					",");

			while (tokenizer.hasMoreTokens()) {
				File lib = new File(homePath, tokenizer.nextToken());

				if (lib.exists()) {
					String[] fileNames = lib.list();
					for (int i = 0; i < fileNames.length; i++) {
						File file = new File(lib, fileNames[i]);
						if (file.isDirectory())
							continue;
						urls.add(createJarURL(file));
						files.add(file);
					}
				}
			}
		}

		URLClassLoader baseLoader = URLClassLoader.newInstance(urls
				.toArray(new URL[urls.size()]));

		ClassLoader loader;
		if (useProfiler) {
			loader = (ClassLoader) Class.forName(profilerClassName, false,
					baseLoader).newInstance();
			initializeProfiler(loader, files);
		} else {
			loader = baseLoader;
		}
		Thread.currentThread().setContextClassLoader(loader);

		String mainClassName = properties.getProperty(mainClassKeyName);
		Method main = Class.forName(mainClassName, false, loader)
				.getDeclaredMethod("main", new Class[] { String[].class });
		main.invoke(null, new Object[] { args });
	}

	private static URL createJarURL(File jar) {
		try {
			return new URL("jar:" + jar.toURI().toURL() + "!/");
		} catch (MalformedURLException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void initializeProfiler(ClassLoader profiler,
			List<File> files) throws Exception {
		Class<?> profilerClass = profiler.getClass();
		profilerClass.getDeclaredMethod("setClasspath", File[].class).invoke(
				profiler,
				new Object[] { files.toArray(new File[files.size()]) });

		StringTokenizer tokenizer = new StringTokenizer(properties.getProperty(
				profilerTargetClassPrefixesKeyName, ""), ",");
		List<String> prefixList = new LinkedList<String>();
		while (tokenizer.hasMoreTokens()) {
			prefixList.add(tokenizer.nextToken());
		}

		profilerClass.getDeclaredMethod("setTargetClassNamePrefixes",
				String[].class)
				.invoke(profiler,
						new Object[] { prefixList.toArray(new String[prefixList
								.size()]) });

		String outputDirName = properties.getProperty(profilerOutputDirKeyName,
				defaultProfilerOutputDir);

		profilerClass.getDeclaredMethod("setOutputDirectory", File.class)
				.invoke(profiler,
						new Object[] { new File(homePath, outputDirName) });
	}
}