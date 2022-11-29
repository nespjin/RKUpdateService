package android.rockchip.update.util;

/**
 * FTP操作类型
 */
public enum FTPOptType {
	UP("uploading"), 
	DOWN("downloading"),
	LIST("listing"), 
	DELFILE("delete file"), 
	DELFOD("delete folder"), 
	RENAME("rename file");

	private String optname;

	FTPOptType(String optname) {
		this.optname = optname;
	}

	public String getOptname() {
		return optname;
	}
}
