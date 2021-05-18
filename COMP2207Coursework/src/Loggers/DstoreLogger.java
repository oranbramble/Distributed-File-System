package Loggers;

import java.io.IOException;

public class DstoreLogger extends Logger {

private static final String LOG_FILE_SUFFIX = "dstore";
	
	private static DstoreLogger instance = null;
	
	private final String logFileSuffix;
	
	public static void init(LoggingType loggingType, int port) throws IOException {
		if (instance == null)
			instance = new DstoreLogger(loggingType, port);
		else
			throw new IOException("DstoreLogger already initialised");
	}
	
	public static DstoreLogger getInstance() {
		if (instance == null)
			throw new RuntimeException("DstoreLogger has not been initialised yet");
		return instance;
	}

	protected DstoreLogger(LoggingType loggingType, int port) throws IOException {
		super(loggingType);
		logFileSuffix = LOG_FILE_SUFFIX + "_" + port;			
	}

	@Override
	protected String getLogFileSuffix() {
		return logFileSuffix;
	}

}
