package jp.ats.profiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Enumeration;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import javassist.NotFoundException;

public class Profiler extends ClassLoader {

	private static final ClassLoader parent = Profiler.class.getClassLoader();

	private static final String excludePrefix = Profiler.class.getPackage()
		.getName();

	private final ClassPool pool = ClassPool.getDefault();

	private int id = 0;

	private PrintWriter methodWriter;

	private String[] prefixes = new String[0];

	public Profiler() {
		pool.appendClassPath(new LoaderClassPath(getClass().getClassLoader()));
	}

	public void setOutputDirectory(File output) throws IOException {
		synchronized (getClass()) {
			if (output.exists()) {
				String[] files = output.list();
				for (int i = 0; i < files.length; i++) {
					File target = new File(output, files[i]);
					if (target.isDirectory()) continue;
					target.delete();
				}
			}
			output.mkdir();
			Logger.setOutput(output);
		}
		synchronized (this) {
			methodWriter = Logger.createWriter(new File(output, "methods.txt"));
		}
	}

	public void setClasspath(File[] classpaths) {
		try {
			for (int i = 0; i < classpaths.length; i++) {
				pool.appendClassPath(classpaths[i].getAbsolutePath());
			}
		} catch (NotFoundException e) {
			throw new IllegalStateException(e.toString());
		}
	}

	public synchronized void setTargetClassNamePrefixes(String[] prefixes) {
		this.prefixes = prefixes.clone();
	}

	protected synchronized Class<?> findClass(String name)
		throws ClassNotFoundException {

		if (name.startsWith(excludePrefix)) return parent.loadClass(name);

		boolean classNameMatched = false;
		for (int i = 0; i < prefixes.length; i++) {
			if (name.startsWith(prefixes[i])) {
				classNameMatched = true;
				break;
			}
		}

		try {
			if (!classNameMatched) {
				return defineClassOf(name);
			}

			CtClass cc = pool.get(name);

			{
				CtConstructor ci = cc.getClassInitializer();
				if (ci != null) {
					ci.insertBefore(getBefore());
					ci.insertAfter(getAfter());
					ci.addCatch(
						getException(),
						pool.get(Exception.class.getName()));
					writeMethodLine(ci, "<clinit>", id);
					id++;
				}
			}

			if (cc.isInterface()) {
				return defineClassOf(name);
			}

			{
				CtConstructor[] co = cc.getDeclaredConstructors();
				for (int i = 0; i < co.length; i++) {
					co[i].insertBeforeBody(getBefore());
					co[i].insertAfter(getAfter());
					co[i].addCatch(
						getException(),
						pool.get(Exception.class.getName()));
					writeMethodLine(co[i], "<init>", id);
					id++;
				}
			}

			{
				CtMethod[] ms = cc.getDeclaredMethods();
				for (int i = 0; i < ms.length; i++) {
					if (ms[i].isEmpty()) continue;
					ms[i].insertBefore(getBefore());
					ms[i].insertAfter(getAfter());
					ms[i].addCatch(
						getException(),
						pool.get(Exception.class.getName()));
					writeMethodLine(ms[i], ms[i].getName(), id);
					id++;
				}
			}

			methodWriter.flush();

			return defineClassOf(name);
		} catch (NotFoundException e) {
			throw new ClassNotFoundException();
		} catch (IOException e) {
			throw new IllegalStateException();
		} catch (CannotCompileException e) {
			throw new IllegalStateException();
		}
	}

	protected Enumeration<URL> findResources(String name) throws IOException {
		return parent.getResources(name);
	}

	protected URL findResource(String name) {
		return parent.getResource(name);
	}

	private Class<?> defineClassOf(String name)
		throws CannotCompileException, NotFoundException, IOException {
		byte[] b = pool.get(name).toBytecode();
		Class<?> clazz = defineClass(name, b, 0, b.length);
		resolveClass(clazz);
		return clazz;
	}

	private String getBefore() {
		return Logger.class.getName() + ".before(" + id + ");";
	}

	private String getAfter() {
		return Logger.class.getName() + ".after();";
	}

	private String getException() {
		return "{"
			+ Logger.class.getName()
			+ ".exception("
			+ id
			+ ");throw $e;}";
	}

	private static String getClassName(CtClass clazz, StringBuffer buffer) {
		if (!clazz.isArray()) return clazz.getName() + buffer;
		try {
			return getClassName(clazz.getComponentType(), buffer.append("[]"));
		} catch (NotFoundException e) {
			throw new IllegalStateException(e.getMessage());
		}
	}

	private void writeMethodLine(CtBehavior behavior, String name, int id) {
		String className = behavior.getDeclaringClass().getName();
		CtClass[] parameterClasses;
		try {
			parameterClasses = behavior.getParameterTypes();
		} catch (NotFoundException e) {
			throw new IllegalStateException(e.getMessage());
		}
		StringBuffer parameterBuffer = new StringBuffer();
		String separator = ", ";
		for (int i = 0; i < parameterClasses.length; i++) {
			parameterBuffer.append(getClassName(
				parameterClasses[i],
				new StringBuffer()));
			parameterBuffer.append(separator);
		}
		String parameters = parameterBuffer.toString();
		if (parameters.length() > 0) {
			parameters = parameters.substring(0, parameters.length()
				- separator.length());
		}

		int lineNumber = behavior.getMethodInfo().getLineNumber(0);

		String line = id
			+ "\t"
			+ className
			+ "\t"
			+ name
			+ "("
			+ parameters
			+ ")\t"
			+ lineNumber;
		methodWriter.println(line);
	}
}
