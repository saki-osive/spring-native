package org.springframework.nativex.buildtools;

import java.io.IOException;
import java.nio.file.Path;

/**
 * File generated by the native build tools.
 * <p>This file should be later compiled and/or packaged with the resulting application.
 *
 * @author Brian Clozel
 */
public interface GeneratedFile {

	/**
	 * Write the generated file to the given root path.
	 * 
	 * <p>This method should write the file to the expected location,
	 * given the type of the generated file and relative to the given root path.
	 * @param rootPath the root path for writing generated files
	 */
	void writeTo(Path rootPath) throws IOException;

}
