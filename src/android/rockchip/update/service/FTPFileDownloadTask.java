package android.rockchip.update.service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.xmlpull.v1.XmlPullParserException;

import it.sauronsoftware.ftp4j.FTPClient;
import it.sauronsoftware.ftp4j.FTPDataTransferListener;
import it.sauronsoftware.ftp4j.FTPIllegalReplyException;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.rockchip.update.util.FTPOptType;
import android.rockchip.update.util.FTPRequestInfo;
import android.rockchip.update.util.FTPToolkit;
import android.rockchip.update.util.FileInfo;
import android.rockchip.update.util.FileInfo.Piece;
import android.rockchip.update.util.RegetInfoUtil;
import android.util.Log;


public class FTPFileDownloadTask extends Thread {
	private static final String TAG = "FTPFileDownloadTask";
	private MyFtpListener mDownloadListener;
	private FTPRequestInfo mFTPRequest;
	private String mLocalFilePath;
	private String mLocalTempFile;
	private FTPClient mFtpClient;
	private long mContentLength = 0;
	private volatile long mReceivedCount;
	private volatile long mLastReceivedCount;
	private volatile long last_times = 0;
	private FileInfo mFileInfo;
	private String mFileName;
	private volatile int err = HTTPFileDownloadTask.ERR_NOERR;

	
	public FTPFileDownloadTask(FTPRequestInfo ftpRequest, String localPath, String fileName) {
		mFTPRequest = ftpRequest;
		mFileName = fileName;
		mDownloadListener = new MyFtpListener(FTPOptType.DOWN);
		mLocalFilePath = (localPath.endsWith("/")? localPath : localPath + "/") + fileName;
		mLocalTempFile = (localPath.endsWith("/")? localPath : localPath + "/") + "."
				+ (fileName.lastIndexOf(".") > 0? (fileName.substring(0, fileName.lastIndexOf(".")) + "__tp.xml") : (fileName + "__tp.xml"));
		
		Log.d(TAG, "mLocalFilePath = " + mLocalFilePath + "  mLocalTempFile = " + mLocalTempFile);
	}

	public void setProgressHandler(Handler progressHandler) {
		mDownloadListener.setProgressHandler(progressHandler);
	}
	
	public void stopDownload() {
		try {
			err = HTTPFileDownloadTask.ERR_REQUEST_STOP;
			mFtpClient.abortCurrentDataTransfer(true);
		} catch (Exception e) {
			err = HTTPFileDownloadTask.ERR_UNKNOWN;
			e.printStackTrace();
		} 
	}
	
	private void prepareDownload() throws Exception {
		File targetFile = new File(mLocalFilePath);
		
		if(!targetFile.exists()) {
			targetFile.createNewFile();
		}else {
			File tmpFile = new File(mLocalTempFile);
			if(tmpFile.exists()) {
				mFileInfo = RegetInfoUtil.parseFileInfoXml(tmpFile);
				Log.d(TAG, "target file have not download complete, so we try to continue download!");
			}else {
				targetFile.delete(); 
				targetFile.createNewFile();
				Log.d(TAG, "find the same name target file, so delete and rewrite it!!!");
			}
		}
		
		if(mFileInfo == null) {
			mFileInfo = new FileInfo();
			mFileInfo.setFileLength(mContentLength);
			mFileInfo.setmURI(new URI("ftp://" + mFTPRequest.getHost() +  mFTPRequest.getRequestPath()));
			mFileInfo.setFileName(mFileName);
			mFileInfo.setReceivedLength(0);
			RegetInfoUtil.changeFileAccessRight(new File(mLocalTempFile));
		}
		
		mFtpClient = FTPToolkit.makeFtpConnection(mFTPRequest.getHost(), mFTPRequest.getPort(),
				mFTPRequest.getUsername(), mFTPRequest.getPassword());
		
		mContentLength = FTPToolkit.getFileLength(mFtpClient, mFTPRequest.getRequestPath());
		
		mFileInfo.setFileLength(mContentLength);
		RegetInfoUtil.changeFileAccessRight(targetFile);
	}
	
	@Override
	public void run() {
		
		try {
			prepareDownload();
			
			if(mFileInfo.getPieceNum() == 0) {
				mFileInfo.addPiece(0, mContentLength - 1, 0);
				FTPToolkit.download(mFtpClient, mFTPRequest.getRequestPath(), mLocalFilePath, 0, mDownloadListener);
			}else {
				Log.d(TAG, "try to continue download ====>");
				mReceivedCount = mFileInfo.getReceivedLength();
				Piece p = mFileInfo.getPieceById(0);
				FTPToolkit.download(mFtpClient, mFTPRequest.getRequestPath(), mLocalFilePath, p.getPosNow(), mDownloadListener);
			}
		}catch (Exception e) {
			Log.e(TAG, "catch a unknown error!");
			err = HTTPFileDownloadTask.ERR_UNKNOWN;
			mDownloadListener.aborted();
		}
	}
	
	/**
	 * FTP监听器
	 */
	public class MyFtpListener implements FTPDataTransferListener {
		private Handler mProgressHandler;
		private FTPOptType optType;
		
		public void setProgressHandler(Handler progressHandler) {
			mProgressHandler = progressHandler;
		}

		public MyFtpListener(FTPOptType optType) {
			this.optType = optType;
		}

		public void started() {
			if(mProgressHandler != null) {
				Message m = new Message();
				m.what = HTTPFileDownloadTask.PROGRESS_START_COMPLETE;
				
				mProgressHandler.sendMessage(m);
				Log.d(TAG, "send ProgressStartComplete");
			}
		}

		public void transferred(int length) {
			mReceivedCount += length;
			Piece p = mFileInfo.getPieceById(0);
			long posNew = p.getPosNow() + length; 
			mFileInfo.modifyPieceState(0, posNew);
			mFileInfo.setReceivedLength(mReceivedCount);
			long cur_times = System.currentTimeMillis();
			
			if(mProgressHandler != null && (cur_times - last_times >= 1000)) {
				int percent = (int)(mReceivedCount * 100 / mContentLength);
				
				long receivedCount = mReceivedCount;
				long contentLength = mContentLength;
				long receivedPerSecond = (mReceivedCount - mLastReceivedCount);
				mLastReceivedCount = mReceivedCount;
				
				Message m = new Message();
				m.what = HTTPFileDownloadTask.PROGRESS_UPDATE;
				Bundle b = new Bundle();
				b.putLong("ContentLength", contentLength);
				b.putLong("ReceivedCount", receivedCount);
				b.putLong("ReceivedPerSecond", receivedPerSecond);
				m.setData(b);
				
				mProgressHandler.sendMessage(m);
				Log.d(TAG, "send ProgressUpdate");
				last_times = cur_times;
			}
		}

		public void completed() {
			File f = new File(mLocalTempFile);
			if(f.exists()) {
				f.delete();
				Log.d(TAG, "finish(): delete the temp file!");
			}
			
			if(mProgressHandler != null) {
				Message m = new Message();
				m.what = HTTPFileDownloadTask.PROGRESS_DOWNLOAD_COMPLETE;
				
				mProgressHandler.sendMessage(m);
				Log.d(TAG, "send ProgressDownloadComplete");
			}
		}

		public void aborted() {
			Log.d(TAG, "download aborted!!!");
			
			File f = new File(mLocalTempFile);
			try {
				mFileInfo.printDebug();
				RegetInfoUtil.writeFileInfoXml(f, mFileInfo);
				RegetInfoUtil.changeFileAccessRight(f);
				Log.d(TAG, "download task not complete, save the progress !!!");
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			if(mProgressHandler != null) {
				Message m = new Message();
				m.what = HTTPFileDownloadTask.PROGRESS_STOP_COMPLETE;	
				Bundle b = new Bundle();
				b.putInt("err", err);
				m.setData(b);
				mProgressHandler.sendMessage(m);
				Log.d(TAG, "send ProgressStopComplete");
			}
			
			FTPToolkit.closeConnection(mFtpClient);
		}

		public void failed() {
		}
	}
}
