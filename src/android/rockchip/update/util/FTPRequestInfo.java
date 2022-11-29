package android.rockchip.update.util;

import android.util.Log;

public class FTPRequestInfo {
	private String host;
	private String username;
	private String password;
	private int port;
	private String requestPath;

	public FTPRequestInfo() {
		port = 21;
		host = null;
		username = null;
		password = null;
		requestPath = null;
	}
	
	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getRequestPath() {
		return requestPath;
	}

	public void setRequestPath(String requestPath) {
		this.requestPath = requestPath;
	}

	public void dump() {
		Log.d("FTPRequestInfo", "host: " + host);
		Log.d("FTPRequestInfo", "port: " + port);
		Log.d("FTPRequestInfo", "username: " + username);
		Log.d("FTPRequestInfo", "password: " + password);
		Log.d("FTPRequestInfo", "requestPath: " + requestPath);
	}
}
