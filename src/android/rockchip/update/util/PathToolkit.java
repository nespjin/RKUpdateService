package android.rockchip.update.util;

import java.io.File;

/**
 * 路径处理工具，操作系统自适应
 * 
 */
public final class PathToolkit {
	private PathToolkit() {
	}

	/**
	 * 格式化文件路径，将其中不规范的分隔转换为标准的分隔符,并且去掉末尾的文件路径分隔符。 本方法操作系统自适应
	 * 
	 * @param path
	 *            文件路径
	 * @return 格式化后的文件路径
	 */
	public static String formatPath4File(String path) {
		String reg0 = "\\\\+";
		String reg = "\\\\+|/+";
		String temp = path.trim().replaceAll(reg0, "/");
		temp = temp.replaceAll(reg, "/");
		if (temp.length() > 1 && temp.endsWith("/")) {
			temp = temp.substring(0, temp.length() - 1);
		}
		temp = temp.replace('/', File.separatorChar);
		return temp;
	}

	/**
	 * 格式化文件路径，将其中不规范的分隔转换为标准的分隔符 并且去掉末尾的"/"符号(适用于FTP远程文件路径或者Web资源的相对路径)。
	 * 
	 * @param path
	 *            文件路径
	 * @return 格式化后的文件路径
	 */
	public static String formatPath4FTP(String path) {
		String reg0 = "\\\\+";
		String reg = "\\\\+|/+";
		String temp = path.trim().replaceAll(reg0, "/");
		temp = temp.replaceAll(reg, "/");
		if (temp.length() > 1 && temp.endsWith("/")) {
			temp = temp.substring(0, temp.length() - 1);
		}
		return temp;
	}

	/**
	 * 获取FTP路径的父路径，但不对路径有效性做检查
	 * 
	 * @param path
	 *            FTP路径
	 * @return 父路径，如果没有父路径，则返回null
	 */
	public static String genParentPath4FTP(String path) {
		String pp = new File(path).getParent();
		if (pp == null)
			return null;
		else
			return formatPath4FTP(pp);
	}
}
