package jp.ats.profiler;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedList;

public class Logger {

	private static final ThreadLocal<Holder> holder = new ThreadLocal<Holder>() {

		protected Holder initialValue() {
			try {
				return new Holder();
			} catch (IOException e) {
				throw new ProfilerError(e);
			}
		}
	};

	private static File output;

	public static void before(int id) {
		try {
			holder.get().printBefore(id);
		} catch (Exception e) {
			throw new ProfilerError(e);
		}
	}

	public static void after() {
		try {
			holder.get().printAfter();
		} catch (Exception e) {
			throw new ProfilerError(e);
		}
	}

	public static void exception(int id) {
		try {
			holder.get().printException(id);
		} catch (Exception e) {
			throw new ProfilerError(e);
		}
	}

	static PrintWriter createWriter(File file) throws IOException {
		if (!file.createNewFile()) throw new IOException(
			"createNewFile() failed. " + file);
		return new PrintWriter(new BufferedOutputStream(new FileOutputStream(
			file.getAbsolutePath(),
			true)));
	}

	static synchronized void setOutput(File outputDirectory) {
		output = outputDirectory;
	}

	private static synchronized File getOutput() {
		return output;
	}

	private static class Holder {

		private static int threadId = 0;

		private final LinkedList<StackUnit> stack = new LinkedList<StackUnit>();

		private final PrintWriter stackTraceWriter;

		private final PrintWriter elapsedWriter;

		Holder() throws IOException {
			int threadId = getThreadId();
			stackTraceWriter = createWriter(new File(getOutput(), "stacktrace-"
				+ threadId
				+ ".txt"));

			elapsedWriter = createWriter(new File(getOutput(), "elapsedtime-"
				+ threadId
				+ ".txt"));

			String threadName = Thread.currentThread().toString();
			synchronized (Holder.class) {
				PrintWriter writer = createWriter(new File(
					getOutput(),
					"threads.txt"));
				writer.println("thread id:[" + threadId + "] = " + threadName);
				writer.flush();
				writer.close();
			}
		}

		protected void finalize() {
			stackTraceWriter.close();
			elapsedWriter.close();
		}

		void printBefore(int id) {
			stackTraceWriter.println("B "
				+ createUnit(id)
				+ " {\t"
				+ System.currentTimeMillis());
		}

		void printAfter() {
			StackUnit unit = stack.removeLast();
			stackTraceWriter.println("A "
				+ unit
				+ " }\t"
				+ System.currentTimeMillis());
			elapsedWriter.println(unit + "\t" + unit.getElapsedTime());
			flush();
		}

		void printException(int id) {
			StackUnit unit = stack.removeLast();
			if (unit.getId() != id) {
				stack.add(unit);
				return;
			}
			stackTraceWriter.println("E "
				+ unit
				+ " }\t"
				+ System.currentTimeMillis());
			elapsedWriter.println(unit + "\t" + unit.getElapsedTime());
			flush();
		}

		private StackUnit createUnit(int id) {
			StackUnit unit;
			if (stack.size() == 0) {
				unit = new StackUnit(id);
			} else {
				unit = new StackUnit(stack.getLast(), id);
			}
			stack.add(unit);
			return unit;
		}

		private void flush() {
			stackTraceWriter.flush();
			elapsedWriter.flush();
		}

		private static synchronized int getThreadId() {
			return threadId++;
		}
	}

	private static class StackUnit {

		private final int id;

		private final String stack;

		private final long start;

		StackUnit(StackUnit parent, int id) {
			this.id = id;
			this.stack = parent + "-" + id;
			start = System.currentTimeMillis();
		}

		StackUnit(int id) {
			this.id = id;
			this.stack = String.valueOf(id);
			start = System.currentTimeMillis();
		}

		int getId() {
			return id;
		}

		long getElapsedTime() {
			return System.currentTimeMillis() - start;
		}

		public String toString() {
			return stack;
		}
	}
}
