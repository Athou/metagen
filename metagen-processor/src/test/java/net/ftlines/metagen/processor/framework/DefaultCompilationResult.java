package net.ftlines.metagen.processor.framework;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;

public class DefaultCompilationResult implements CompilationResult {
	private final File outputDir;
	private final DiagnosticCollector collector;

	public DefaultCompilationResult(File outputDir,
			DiagnosticCollector collector) {
		this.outputDir = outputDir;
		this.collector = collector;
	}

	public List<Diagnostic<?>> getDiagnostics() {
		return Collections.unmodifiableList(collector.getDiagnostics());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see binder.framework.CompilationResult#isClean()
	 */
	@Override
	public boolean isClean() {
		for (Diagnostic<?> diagnostic : getDiagnostics()) {
			switch (diagnostic.getKind()) {
			case ERROR:
			case MANDATORY_WARNING:
			case WARNING:
				return false;
			}
		}
		return true;
	}

	private File getFile(File base, Class<?> clazz, String suffix) {
		String name = clazz.getName();
		name = name.replace('.', '/');
		return new File(base, name + suffix);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see binder.framework.CompilationResult#getFile(java.lang.Class,
	 * java.lang.String)
	 */
	@Override
	public File getFile(Class<?> clazz, String suffix)
			throws FileNotFoundException {
		File file = getFile(outputDir, clazz, suffix);
		if (!file.exists()) {
			throw new FileNotFoundException(file.getAbsolutePath());
		}
		return file;
	}

	public void dumpFile(Class<?> clazz, String suffix) throws IOException {
		File file = getFile(clazz, suffix);
		FileInputStream in = new FileInputStream(file);
		byte[] buff = new byte[1024];
		int c = 0;
		while ((c = in.read(buff)) > 0) {
			System.out.write(buff, 0, c);
		}
		in.close();
	}

}