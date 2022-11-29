package android.rockchip.update.service;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.Locale;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;
import android.os.SystemProperties;

import android.os.UserHandle;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.os.storage.DiskInfo;
import java.util.List;
import java.io.FileNotFoundException;
import android.os.Build;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

public class RKUpdateService extends Service {
	private static final String TAG = "RKUpdateService";
	private static final boolean DEBUG = true;
	private static final boolean mIsNotifyDialog = true;
	private static final boolean mIsSupportUsbUpdate = true;

	private Context mContext;
	private volatile boolean mIsFirstStartUp = true;

	private static void LOG(String msg) {
		if (DEBUG) {
			Log.d(TAG, msg);
		}
	}

	static {
		/*
		 * Load the library. If it's already loaded, this does nothing.
		 */
		System.loadLibrary("rockchip_update_jni");
	}

	public static String OTA_PACKAGE_FILE = "update.zip";
	public static String RKIMAGE_FILE = "update.img";
	public static final int RKUPDATE_MODE = 1;
	public static final int OTAUPDATE_MODE = 2;
	private static volatile boolean mWorkHandleLocked = false;
	private static volatile boolean mIsNeedDeletePackage = false;

	public static final String EXTRA_IMAGE_PATH = "android.rockchip.update.extra.IMAGE_PATH";
	public static final String EXTRA_IMAGE_VERSION = "android.rockchip.update.extra.IMAGE_VERSION";
	public static final String EXTRA_CURRENT_VERSION = "android.rockchip.update.extra.CURRENT_VERSION";
	public static String DATA_ROOT = "/data/media/0";
	public static String FLASH_ROOT = Environment.getExternalStorageDirectory().getAbsolutePath();
	// public static String SDCARD_ROOT = "/mnt/external_sd";
	// public static String USB_ROOT = "/mnt/usb_storage";
	public static String SDCARD_ROOT = "/mnt/media_rw";
	public static String USB_ROOT = "/mnt/media_rw";
	public static String CACHE_ROOT = Environment.getDownloadCacheDirectory().getAbsolutePath();

	public static final int COMMAND_NULL = 0;
	public static final int COMMAND_CHECK_LOCAL_UPDATING = 1;
	public static final int COMMAND_CHECK_REMOTE_UPDATING = 2;
	public static final int COMMAND_CHECK_REMOTE_UPDATING_BY_HAND = 3;
	public static final int COMMAND_DELETE_UPDATEPACKAGE = 4;

	private static final String COMMAND_FLAG_SUCCESS = "success";
	private static final String COMMAND_FLAG_UPDATING = "updating";

	public static final int UPDATE_SUCCESS = 1;
	public static final int UPDATE_FAILED = 2;

	private static final String[] IMAGE_FILE_DIRS = { DATA_ROOT + "/", FLASH_ROOT + "/",
			// SDCARD_ROOT + "/",
			// USB_ROOT + "/",
	};

	private String mLastUpdatePath;
	private WorkHandler mWorkHandler;
	private Handler mMainHandler;
	private SharedPreferences mAutoCheckSet;

	/*----------------------------------------------------------------------------------------------------*/
	public static URI mRemoteURI = null;
	public static URI mRemoteURIBackup = null;
	private String mTargetURI = null;
	private boolean mUseBackupHost = false;
	private String mOtaPackageVersion = null;
	private String mSystemVersion = null;
	private String mOtaPackageName = null;
	private String mOtaPackageLength = null;
	private String mDescription = null;
	private volatile boolean mIsOtaCheckByHand = false;

	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return mBinder;
	}

	private final LocalBinder mBinder = new LocalBinder();

	public class LocalBinder extends Binder {
		public void updateFirmware(String imagePath, int mode) {
			LOG("updateFirmware(): imagePath = " + imagePath);
			try {
				mWorkHandleLocked = true;
				if (mode == OTAUPDATE_MODE) {
					RKRecoverySystem.installPackage(mContext, new File(imagePath));
				} else if (mode == RKUPDATE_MODE) {
					RKRecoverySystem.installPackage(mContext, new File(imagePath));
				}
			} catch (IOException e) {
				Log.e(TAG, "updateFirmware() : Reboot for updateFirmware() failed", e);
			}
		}

		public boolean doesOtaPackageMatchProduct(String imagePath) {
			LOG("doesImageMatchProduct(): start verify package , imagePath = " + imagePath);

			try {
				RKRecoverySystem.verifyPackage(new File(imagePath), null, null);
			} catch (GeneralSecurityException e) {
				LOG("doesImageMatchProduct(): verifaPackage faild!\n" + e.toString());
				return false;
			} catch (Exception e) {
				LOG("doesImageMatchProduct(): verifaPackage faild!\n" + e.toString());
				return false;
			}
			return true;
		}

		public void deletePackage(String path) {
			LOG("try to deletePackage... path=" + path);
			String fileName = path;// = "/data/media/0/update.zip";
			if (path.startsWith("@")) {
				if (Build.VERSION.SDK_INT >= 30) {
					fileName = "/storage/emulated/0/update.zip";

				} else {
					fileName = "/data/media/0/update.zip";
				}
				LOG("ota was maped, path = " + path + ",delete " + fileName);

				File f_ota = new File(fileName);
				if (f_ota.exists()) {
					f_ota.delete();
					LOG("delete complete! path=" + fileName);
				} else {
					fileName = "/data/media/0/update.img";
					f_ota = new File(fileName);
					if (f_ota.exists()) {
						f_ota.delete();
						LOG("delete complete! path=" + fileName);
					} else {
						LOG("path = " + fileName + ", file not exists!");
					}
				}
			}

			if (Build.VERSION.SDK_INT >= 30) {
				if (path.startsWith("/data/media")) {
					fileName = path.replace("/data/media", "/storage/emulated");
				} else if (path.startsWith("/mnt/media_rw")) {
					fileName = path.replace("/mnt/media_rw", "/storage");
				}

			} else {
				fileName = path;
			}
			Log.d(TAG, " deletePackage fileName =" + fileName);

			File f = new File(fileName);
			if (f.exists()) {
				f.delete();
				LOG("delete complete! path=" + fileName);
			} else {
				LOG("fileName=" + fileName + ",file not exists!");
			}
		}

		public void unLockWorkHandler() {
			LOG("unLockWorkHandler...");
			mWorkHandleLocked = false;
		}

		public void LockWorkHandler() {
			mWorkHandleLocked = true;
			LOG("LockWorkHandler...!");
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();

		mContext = this;
		/*-----------------------------------*/
		LOG("Starting RKUpdateService, version is " + getAppVersionName(mContext));

		// whether is UMS or m-user
		if (getMultiUserState()) {
			FLASH_ROOT = DATA_ROOT;
		}

		String ota_packagename = getOtaPackageFileName();
		if (ota_packagename != null) {
			OTA_PACKAGE_FILE = ota_packagename;
			LOG("get ota package name private is " + OTA_PACKAGE_FILE);
		}

		String rk_imagename = getRKimageFileName();
		if (rk_imagename != null) {
			RKIMAGE_FILE = rk_imagename;
			LOG("get rkimage name private is " + RKIMAGE_FILE);
		}

		try {
			mRemoteURI = new URI(getRemoteUri());
			mRemoteURIBackup = new URI(getRemoteUriBackup());
			LOG("remote uri is " + mRemoteURI.toString());
			LOG("remote uri backup is " + mRemoteURIBackup.toString());
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}

		mAutoCheckSet = getSharedPreferences("auto_check", MODE_PRIVATE);

		mMainHandler = new Handler(Looper.getMainLooper());
		HandlerThread workThread = new HandlerThread("UpdateService : work thread");
		workThread.start();
		mWorkHandler = new WorkHandler(workThread.getLooper());

		if (mIsFirstStartUp) {
			LOG("first startup!!!");
			mIsFirstStartUp = false;
			String command = RKRecoverySystem.readFlagCommand();
			String path;
			if (command != null) {
				LOG("command = " + command);
				if (command.contains("$path")) {
					path = command.substring(command.indexOf('=') + 1);
					LOG("last_flag: path = " + path);

					if (command.startsWith(COMMAND_FLAG_SUCCESS)) {
						if (!mIsNotifyDialog) {
							mIsNeedDeletePackage = true;
							mLastUpdatePath = path;
							return;
						}

						LOG("now try to start notifydialog activity!");
						Intent intent = new Intent(mContext, NotifyDeleteActivity.class);
						intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						intent.putExtra("flag", UPDATE_SUCCESS);
						intent.putExtra("path", path);
						startActivity(intent);
						mWorkHandleLocked = true;
						return;
					}
					if (command.startsWith(COMMAND_FLAG_UPDATING)) {
						Intent intent = new Intent(mContext, NotifyDeleteActivity.class);
						intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						intent.putExtra("flag", UPDATE_FAILED);
						intent.putExtra("path", path);
						startActivity(intent);
						mWorkHandleLocked = true;
						return;
					}
				}
			}
		}
	}

	@Override
	public void onDestroy() {
		LOG("onDestroy.......");
		super.onDestroy();
	}

	@Override
	public void onStart(Intent intent, int startId) {
		LOG("onStart.......");

		super.onStart(intent, startId);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		LOG("onStartCommand.......");

		if (intent == null) {
			return Service.START_NOT_STICKY;
		}

		int command = intent.getIntExtra("command", COMMAND_NULL);
		int delayTime = intent.getIntExtra("delay", 1000);

		LOG("command = " + command + " delaytime = " + delayTime);
		if (command == COMMAND_NULL) {
			return Service.START_NOT_STICKY;
		}

		if (command == COMMAND_CHECK_REMOTE_UPDATING) {
			mIsOtaCheckByHand = false;
			if (!mAutoCheckSet.getBoolean("auto_check", true)) {
				LOG("user set not auto check!");
				return Service.START_NOT_STICKY;
			}
		}

		if (command == COMMAND_CHECK_REMOTE_UPDATING_BY_HAND) {
			mIsOtaCheckByHand = true;
			command = COMMAND_CHECK_REMOTE_UPDATING;
		}

		if (mIsNeedDeletePackage) {
			command = COMMAND_DELETE_UPDATEPACKAGE;
			delayTime = 20000;
			mWorkHandleLocked = true;
		}

		Message msg = new Message();
		msg.what = command;
		msg.arg1 = WorkHandler.NOT_NOTIFY_IF_NO_IMG;
		mWorkHandler.sendMessageDelayed(msg, delayTime);
		return Service.START_REDELIVER_INTENT;
	}

	/** @see mWorkHandler. */
	private class WorkHandler extends Handler {
		private static final int NOTIFY_IF_NO_IMG = 1;
		private static final int NOT_NOTIFY_IF_NO_IMG = 0;

		/*-----------------------------------*/

		public WorkHandler(Looper looper) {
			super(looper);
		}

		public void handleMessage(Message msg) {

			String[] searchResult = null;

			switch (msg.what) {

				case COMMAND_CHECK_LOCAL_UPDATING:
					LOG("WorkHandler::handleMessage() : To perform 'COMMAND_CHECK_LOCAL_UPDATING'.");
					if (mWorkHandleLocked) {
						LOG("WorkHandler::handleMessage() : locked !!!");
						return;
					}

					if (null != (searchResult = getValidFirmwareImageFile(IMAGE_FILE_DIRS))) {
						if (1 == searchResult.length) {
							String path = searchResult[0];
							String imageFileVersion = null;
							String currentVersion = null;

							// if it is rkimage, check the image
							if (path.endsWith("img")) {
								if (!checkRKimage(path)) {
									LOG("WorkHandler::handleMessage() : not a valid rkimage !!");
									return;
								}

								imageFileVersion = getImageVersion(path);

								LOG("WorkHandler::handleMessage() : Find a VALID image file : '" + path
										+ "'. imageFileVersion is '" + imageFileVersion);

								currentVersion = getCurrentFirmwareVersion();
								LOG("WorkHandler::handleMessage() : Current system firmware version : '"
										+ currentVersion + "'.");
							}
							startProposingActivity(path, imageFileVersion, currentVersion);
							return;
						} else {
							LOG("find more than two package files, so it is invalid!");
							return;
						}
					}

					break;
				case COMMAND_CHECK_REMOTE_UPDATING:
					if (mWorkHandleLocked) {
						LOG("WorkHandler::handleMessage() : locked !!!");
						return;
					}

					for (int i = 0; i < 2; i++) {
						try {
							boolean result;

							if (i == 0) {
								mUseBackupHost = false;
								result = requestRemoteServerForUpdate(mRemoteURI);
							} else {
								mUseBackupHost = true;
								result = requestRemoteServerForUpdate(mRemoteURIBackup);
							}

							if (result) {
								LOG("find a remote update package, now start PackageDownloadActivity...");
								startNotifyActivity();
							} else {
								LOG("no find remote update package...");
								myMakeToast(mContext.getString(R.string.current_new));
							}
							break;
						} catch (Exception e) {
							// e.printStackTrace();
							LOG("request remote server error...");
							myMakeToast(mContext.getString(R.string.current_new));
						}

						try {
							Thread.sleep(5000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
					break;
				case COMMAND_DELETE_UPDATEPACKAGE:
					// if mIsNeedDeletePackage == true delete the package
					if (mIsNeedDeletePackage) {
						LOG("execute COMMAND_DELETE_UPDATEPACKAGE...");
						File f = new File(mLastUpdatePath);
						if (f.exists()) {
							f.delete();
							LOG("delete complete! path=" + mLastUpdatePath);
						} else {
							LOG("path=" + mLastUpdatePath + " ,file not exists!");
						}

						mIsNeedDeletePackage = false;
						mWorkHandleLocked = false;
					}

					break;
				default:
					break;
			}
		}

	}

	private String[] findFromSdOrUsb() {
		final int userId = UserHandle.myUserId();
		StorageManager mStorageManager = (StorageManager) mContext.getSystemService(Context.STORAGE_SERVICE);
		final List<VolumeInfo> volumes = mStorageManager.getVolumes();
		for (VolumeInfo volume : volumes) {
			if (!volume.isMountedReadable())
				continue;
			if (volume == null)
				continue;
			final DiskInfo disk = volume.getDisk();
			if (disk == null)
				continue;
			Log.d(TAG, "callen.cai --- volume.internalPath = " + volume.internalPath + ",volume.path=" + volume.path);
			String OtaPath = null;
			String ImgPath = null;

			if (Build.VERSION.SDK_INT >= 30) {
				OtaPath = volume.path + "/" + OTA_PACKAGE_FILE;
				ImgPath = volume.path + "/" + RKIMAGE_FILE;
				Log.d(TAG, "Build.VERSION.SDK_INT >= 30 OtaPath =" + OtaPath);
			} else {
				OtaPath = volume.internalPath + "/" + OTA_PACKAGE_FILE;
				ImgPath = volume.internalPath + "/" + RKIMAGE_FILE;
			}

			int flag = -1;
			if (new File(OtaPath).exists()) {
				flag = 0;
			} else if (new File(ImgPath).exists()) {
				flag = 1;
			} else {
				continue;
			}
			if (disk.isSd()) {
				if (volume.fsType.equals("ntfs") || volume.fsType.equals("exfat")) {
					Log.e(TAG, "sd fstype is " + volume.fsType);
					Toast.makeText(mContext, mContext.getString(R.string.sd_head) + volume.fsType
							+ mContext.getString(R.string.fat_warn), Toast.LENGTH_LONG).show();
					continue;
				}
			} else if (disk.isUsb()) {
				if (volume.fsType.equals("ntfs") || volume.fsType.equals("exfat")) {
					Log.e(TAG, "usb fstype is " + volume.fsType);
					Toast.makeText(mContext, mContext.getString(R.string.usb_head) + volume.fsType
							+ mContext.getString(R.string.fat_warn), Toast.LENGTH_LONG).show();
					continue;
				}
			}
			if (flag == 0) {
				Log.e(TAG, "find package from " + OtaPath);
				return (new String[] { OtaPath });
			} else if (flag == 1) {
				Log.e(TAG, "find package from " + ImgPath);
				return (new String[] { ImgPath });
			}
		}
		return null;
	}

	private String[] getValidFirmwareImageFile(String searchPaths[]) {
		for (String dir_path : searchPaths) {
			String filePath = dir_path + OTA_PACKAGE_FILE;
			LOG("try to search update file : " + filePath);

			if ((new File(filePath)).exists()) {
				LOG("getValidFirmwareImageFile: Target update file: " + filePath);
				return (new String[] { filePath });
			}
		}

		// find rkimage
		for (String dir_path : searchPaths) {
			String filePath = dir_path + RKIMAGE_FILE;
			// LOG("getValidFirmwareImageFile() : Target image file path : " + filePath);

			if ((new File(filePath)).exists()) {
				return (new String[] { filePath });
			}
		}
		if (mIsSupportUsbUpdate) {
			// find usb device update package
			return findFromSdOrUsb();
		}

		return null;
	}

	native private static String getImageVersion(String path);

	native private static String getImageProductName(String path);

	private void startProposingActivity(String path, String imageVersion, String currentVersion) {
		Intent intent = new Intent();

		intent.setComponent(new ComponentName("android.rockchip.update.service",
				"android.rockchip.update.service.FirmwareUpdatingActivity"));
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra(EXTRA_IMAGE_PATH, path);
		intent.putExtra(EXTRA_IMAGE_VERSION, imageVersion);
		intent.putExtra(EXTRA_CURRENT_VERSION, currentVersion);

		mContext.startActivity(intent);
	}

	private boolean checkRKimage(String path) {
		String imageProductName = getImageProductName(path);
		LOG("checkRKimage() : imageProductName = " + imageProductName);
		if (imageProductName == null) {
			return false;
		}

		if (imageProductName.trim().equals(getProductName())) {
			return true;
		} else {
			return false;
		}
	}

	private String getOtaPackageFileName() {
		String str = SystemProperties.get("ro.ota.packagename");
		if (str == null || str.length() == 0) {
			return null;
		}
		if (!str.endsWith(".zip")) {
			return str + ".zip";
		}

		return str;
	}

	private String getRKimageFileName() {
		String str = SystemProperties.get("ro.rkimage.name");
		if (str == null || str.length() == 0) {
			return null;
		}
		if (!str.endsWith(".img")) {
			return str + ".img";
		}

		return str;
	}

	private String getCurrentFirmwareVersion() {
		return SystemProperties.get("ro.firmware.version");
	}

	private static String getProductName() {
		return SystemProperties.get("ro.product.model");
	}

	private void notifyInvalidImage(String path) {
		Intent intent = new Intent();

		intent.setComponent(new ComponentName("android.rockchip.update.service",
				"android.rockchip.update.service.InvalidFirmwareImageActivity"));
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra(EXTRA_IMAGE_PATH, path);

		mContext.startActivity(intent);
	}

	private void makeToast(final CharSequence msg) {
		mMainHandler.post(new Runnable() {
			public void run() {
				Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
			}
		});
	}

	/**********************************************************************************************************************
	 * ota update
	 ***********************************************************************************************************************/
	public static String getRemoteUri() {
		return "http://" + getRemoteHost() + "/OtaUpdater/android?product=" + getOtaProductName() + "&version="
				+ getSystemVersion() + "&sn=" + getProductSN() + "&country=" + getCountry() + "&language="
				+ getLanguage();
	}

	public static String getRemoteUriBackup() {
		return "http://" + getRemoteHostBackup() + "/OtaUpdater/android?product=" + getOtaProductName() + "&version="
				+ getSystemVersion() + "&sn=" + getProductSN() + "&country=" + getCountry() + "&language="
				+ getLanguage();
	}

	public static String getRemoteHost() {
		String remoteHost = SystemProperties.get("ro.product.ota.host");
		if (remoteHost == null || remoteHost.length() == 0) {
			remoteHost = "172.16.14.202:2300";
		}
		return remoteHost;
	}

	public static String getRemoteHostBackup() {
		String remoteHost = SystemProperties.get("ro.product.ota.host2");
		if (remoteHost == null || remoteHost.length() == 0) {
			remoteHost = "172.16.14.202:2300";
		}
		return remoteHost;
	}

	public static String getOtaProductName() {
		String productName = SystemProperties.get("ro.product.model");
		if (productName.contains(" ")) {
			productName = productName.replaceAll(" ", "");
		}

		return productName;
	}

	public static boolean getMultiUserState() {
		String multiUser = SystemProperties.get("ro.factory.hasUMS");
		if (multiUser != null && multiUser.length() > 0) {
			return !multiUser.equals("true");
		}

		multiUser = SystemProperties.get("ro.factory.storage_policy");
		if (multiUser != null && multiUser.length() > 0) {
			return multiUser.equals("1");
		}

		return false;
	}

	private void startNotifyActivity() {
		Intent intent = new Intent(mContext, OtaUpdateNotifyActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra("uri", mTargetURI);
		intent.putExtra("OtaPackageLength", mOtaPackageLength);
		intent.putExtra("OtaPackageName", mOtaPackageName);
		intent.putExtra("OtaPackageVersion", mOtaPackageVersion);
		intent.putExtra("SystemVersion", mSystemVersion);
		intent.putExtra("description", mDescription);
		mContext.startActivity(intent);
		mWorkHandleLocked = true;
	}

	private void myMakeToast(CharSequence msg) {
		if (mIsOtaCheckByHand) {
			makeToast(msg);
		}
	}

	private boolean requestRemoteServerForUpdate(URI remote) throws IOException, ClientProtocolException {
		if (remote == null) {
			return false;
		}

		HttpClient httpClient = CustomerHttpClient.getHttpClient();
		HttpHead httpHead = new HttpHead(remote);

		HttpResponse response = httpClient.execute(httpHead);
		int statusCode = response.getStatusLine().getStatusCode();

		if (statusCode != 200) {
			return false;
		}
		if (DEBUG) {
			for (Header header : response.getAllHeaders()) {
				LOG(header.getName() + ":" + header.getValue());
			}
		}

		Header[] headLength = response.getHeaders("OtaPackageLength");
		if (headLength != null && headLength.length > 0) {
			mOtaPackageLength = headLength[0].getValue();
		}

		Header[] headName = response.getHeaders("OtaPackageName");
		if (headName == null) {
			return false;
		}
		if (headName.length > 0) {
			mOtaPackageName = headName[0].getValue();
		}

		Header[] headVersion = response.getHeaders("OtaPackageVersion");
		if (headVersion != null && headVersion.length > 0) {
			mOtaPackageVersion = headVersion[0].getValue();
		}

		Header[] headTargetURI = response.getHeaders("OtaPackageUri");
		if (headTargetURI == null) {
			return false;
		}
		if (headTargetURI.length > 0) {
			mTargetURI = headTargetURI[0].getValue();
		}

		if (mOtaPackageName == null || mTargetURI == null) {
			LOG("server response format error!");
			return false;
		}

		// get description from server response.
		Header[] headDescription = response.getHeaders("description");
		if (headDescription != null && headDescription.length > 0) {
			mDescription = new String(headDescription[0].getValue().getBytes("ISO8859_1"), "UTF-8");
		}

		if (!mTargetURI.startsWith("http://") && !mTargetURI.startsWith("https://")
				&& !mTargetURI.startsWith("ftp://")) {
			mTargetURI = "http://" + (mUseBackupHost ? getRemoteHostBackup() : getRemoteHost())
					+ (mTargetURI.startsWith("/") ? mTargetURI : ("/" + mTargetURI));
		}

		mSystemVersion = getSystemVersion();

		LOG("OtaPackageName = " + mOtaPackageName + " OtaPackageVersion = " + mOtaPackageVersion
				+ " OtaPackageLength = " + mOtaPackageLength + " SystemVersion = " + mSystemVersion + "OtaPackageUri = "
				+ mTargetURI);
		return true;
	}

	public static String getSystemVersion() {
		String version = SystemProperties.get("ro.product.version");
		if (version == null || version.length() == 0) {
			version = "1.0.0";
		}

		return version;
	}

	public static String getProductSN() {
		String sn = SystemProperties.get("ro.serialno");
		if (sn == null || sn.length() == 0) {
			sn = "unknown";
		}

		return sn;
	}

	public static String getCountry() {
		return Locale.getDefault().getCountry();
	}

	public static String getLanguage() {
		return Locale.getDefault().getLanguage();
	}

	public static String getAppVersionName(Context context) {
		String versionName = "";
		try {
			// ---get the package info---
			PackageManager pm = context.getPackageManager();
			PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
			versionName = pi.versionName;
			if (versionName == null || versionName.length() <= 0) {
				return "";
			}
		} catch (Exception e) {
			Log.e("VersionInfo", "Exception", e);
		}
		return versionName;
	}
}
