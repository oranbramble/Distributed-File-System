package Loggers;

import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;

public abstract class Logger {

	public enum LoggingType {
		NO_LOG, // no log at all
		ON_TERMINAL_ONLY, // log to System.out only 
		ON_FILE_ONLY, // log to file only
		ON_FILE_AND_TERMINAL // log to both System.out and file
	}
	
	protected final LoggingType loggingType;
	protected PrintStream ps;
	
	protected Logger(LoggingType loggingType) {
		this.loggingType = loggingType;
	}
	
	protected abstract String getLogFileSuffix();
	
	protected synchronized PrintStream getPrintStream() throws IOException {
		if (ps == null)
			ps = new PrintStream(getLogFileSuffix() + "_" + System.currentTimeMillis() + ".log");
		return ps;
	}
	
	protected boolean logToFile() {
		return loggingType == LoggingType.ON_FILE_ONLY || loggingType == LoggingType.ON_FILE_AND_TERMINAL;
	}
	
	protected boolean logToTerminal() {
		return loggingType == LoggingType.ON_TERMINAL_ONLY || loggingType == LoggingType.ON_FILE_AND_TERMINAL;
	}
	
	protected void log(String message) {
		if (logToFile())
			try { getPrintStream().println(message); } catch(Exception e) { e.printStackTrace(); }
		if (logToTerminal())
			System.out.println(message);
	}
	
	public void messageSent(Socket socket, String message) {
		log("[" + socket.getLocalPort() + "->" + socket.getPort() + "] " + message);
	}
	
	public void messageReceived(Socket socket, String message) {
		log("[" + socket.getLocalPort() + "<-" + socket.getPort() + "] " + message);
	}
	
}
