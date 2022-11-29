package android.rockchip.update.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import android.rockchip.update.util.FileInfo.Piece;

import android.util.Log;
import android.util.Xml;

public class RegetInfoUtil {
	private static final String TAG = "RegetInfoUtil";
	public static void writeFileInfoXml(File target, FileInfo fileInfo) 
			throws IllegalArgumentException, IllegalStateException, IOException {
		
		FileOutputStream fout;	
		if(!target.exists()) {
			target.createNewFile();
		}
		fout = new FileOutputStream(target);
		XmlSerializer xmlSerializer = Xml.newSerializer();    
		xmlSerializer.setOutput(fout, "UTF-8");    
		xmlSerializer.startDocument("UTF-8", true);
		
		xmlSerializer.startTag(null, "FileInfo");
		xmlSerializer.attribute(null, "Name", fileInfo.getFileName());
		xmlSerializer.attribute(null, "Length", String.valueOf(fileInfo.getFileLength()));
		xmlSerializer.attribute(null, "ReceiveLength", String.valueOf(fileInfo.getReceivedLength()));
		xmlSerializer.attribute(null, "URI", fileInfo.getURI().toString());
		
		xmlSerializer.startTag(null, "Pieces");
		for(int id = 0; id < fileInfo.getPieceNum(); id++) {
			Piece p = fileInfo.getPieceById(id);
			xmlSerializer.startTag(null, "Piece");
			xmlSerializer.attribute(null, "Start", String.valueOf(p.getStart()));
			xmlSerializer.attribute(null, "End", String.valueOf(p.getEnd()));
			xmlSerializer.attribute(null, "PosNow", String.valueOf(p.getPosNow()));
			xmlSerializer.endTag(null, "Piece");
		}
		xmlSerializer.endTag(null, "Pieces");
		xmlSerializer.endTag(null, "FileInfo");
		xmlSerializer.endDocument();    
		fout.flush();    
		fout.close(); 
	}
	
	public static FileInfo parseFileInfoXml(File target) 
				throws XmlPullParserException, URISyntaxException, IOException {
		FileInfo fileInfo = new FileInfo();
		FileInputStream fin = new FileInputStream(target);
		XmlPullParser xmlPullParser = Xml.newPullParser();    
		xmlPullParser.setInput(fin, "UTF-8"); 
		int eventType = xmlPullParser.getEventType(); 
		
		while (eventType != XmlPullParser.END_DOCUMENT) {   
			String tag = xmlPullParser.getName();
        	Log.i(TAG, tag+"====>");
            switch (eventType) {    
            case XmlPullParser.START_DOCUMENT:    
                break;    
            case XmlPullParser.START_TAG:   
                if ("FileInfo".equals(tag)) {    
                	fileInfo.setFileName(xmlPullParser.getAttributeValue(0)); 
                	fileInfo.setFileLength(Integer.valueOf(xmlPullParser.getAttributeValue(1)));
                	fileInfo.setReceivedLength(Integer.valueOf(xmlPullParser.getAttributeValue(2)));
                	fileInfo.setmURI(new URI(xmlPullParser.getAttributeValue(3)));
                }else if("Piece".equals(tag)) {
                	fileInfo.addPiece((long)Integer.valueOf(xmlPullParser.getAttributeValue(0)), 
                						(long)Integer.valueOf(xmlPullParser.getAttributeValue(1)), 
                						(long)Integer.valueOf(xmlPullParser.getAttributeValue(2)));
                	Log.d(TAG, "add a Piece");
                }
                break;    
            case XmlPullParser.END_TAG:    
                break;    
            }    
            eventType = xmlPullParser.next();
		}
		
		fileInfo.printDebug();
		return fileInfo;
	}
	public static void changeFileAccessRight(File file)
	{
		Process p;
		int status;
        try {
            p = Runtime.getRuntime().exec("chmod 766 " +  file );
            status = p.waitFor();
            if (status == 0) {
                //chmod succeed
                  Log.d(TAG, "File chmod succeed");
            } else {
                //chmod failed
                 Log.w(TAG, "File chmod failed status="+status);
            }
        }catch (Exception e) {
				   e.printStackTrace();
			}
	}

	
}
