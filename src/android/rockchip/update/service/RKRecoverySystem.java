/*************************************************************************
	> File Name: RKRecoverySystem.java
	> Author: jkand.huang
	> Mail: jkand.huang@rock-chips.com
	> Created Time: Wed 02 Nov 2016 03:10:47 PM CST
 ************************************************************************/
package android.rockchip.update.service;

import android.os.RecoverySystem;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.ConditionVariable;
import android.os.PowerManager;
import android.os.FileUtils;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.lang.reflect.*;
import android.os.Build;

public class RKRecoverySystem extends RecoverySystem {
	private static final String TAG = "RKRecoverySystem";
	private static File RECOVERY_DIR = new File("/cache/recovery");
	private static File UPDATE_FLAG_FILE = new File(RECOVERY_DIR, "last_flag");

	public static void installPackage(Context context, File packageFile) throws IOException {
		String filename = packageFile.getCanonicalPath();
		writeFlagCommand(filename);
		String oriPath = packageFile.getCanonicalPath();
		String convertPath = oriPath;
		if (Build.VERSION.SDK_INT >= 30) { // Build.VERSION_CODES.R
			Log.d(TAG, "installPackage SDK version >= Build.VERSION_CODES.R,should convert update file path.");
			if (oriPath.startsWith("/storage/emulated/0")) {
				convertPath = oriPath.replace("/storage/emulated/0", "/data/media/0");
			} else if (oriPath.startsWith("/storage")) {
				convertPath = oriPath.replace("/storage", "/mnt/media_rw");
			}
			Log.d(TAG, "installPackage update " + oriPath + " to " + convertPath);
			File convertFile = new File(convertPath);
			RecoverySystem.installPackage(context, convertFile);

		} else
			RecoverySystem.installPackage(context, packageFile);
	}

	public static String readFlagCommand() {
		if (UPDATE_FLAG_FILE.exists()) {
			Log.d(TAG, "UPDATE_FLAG_FILE is exists");
			char[] buf = new char[128];
			int readCount = 0;
			;
			try {
				FileReader reader = new FileReader(UPDATE_FLAG_FILE);
				readCount = reader.read(buf, 0, buf.length);
				Log.d(TAG, "readCount = " + readCount + " buf.length = " + buf.length);
			} catch (IOException e) {
				Log.e(TAG, "can not read /cache/recovery/last_flag!");
			} finally {
				UPDATE_FLAG_FILE.delete();

			}

			StringBuilder sBuilder = new StringBuilder();
			for (int i = 0; i < readCount; i++) {
				if (buf[i] == 0) {
					break;
				}
				sBuilder.append(buf[i]);
			}
			return sBuilder.toString();
		} else {
			return null;
		}
	}

	public static void writeFlagCommand(String path) throws IOException {
		RECOVERY_DIR.mkdirs();
		UPDATE_FLAG_FILE.delete();
		FileWriter writer = new FileWriter(UPDATE_FLAG_FILE);
		try {
			writer.write("updating$path=" + path);
		} finally {
			writer.close();
		}
	}
}
