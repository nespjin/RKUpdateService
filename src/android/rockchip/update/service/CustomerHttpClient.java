package android.rockchip.update.service;

import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;

public class CustomerHttpClient {
	private static final String CHARSET = HTTP.UTF_8;
    private static HttpClient customerHttpClient;
 
    private CustomerHttpClient() {
    }
 
    public static synchronized HttpClient getHttpClient() {
    	if (null == customerHttpClient) {
    		HttpParams params = new BasicHttpParams();
            // 设置一些基本参数
            HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
            HttpProtocolParams.setContentCharset(params, CHARSET);
            HttpProtocolParams.setUseExpectContinue(params, true);
            HttpProtocolParams.setUserAgent(params, "rk29sdk/4.0");
            
            // 增加最大连接到200
            ConnManagerParams.setMaxTotalConnections(params, 100);
            // 增加每个路由的默认最大连接到20
            ConnPerRouteBean connPerRoute = new ConnPerRouteBean(20);
            // 对localhost:80增加最大连接到50
            HttpHost localhost = new HttpHost("locahost", 80);
            connPerRoute.setMaxForRoute(new HttpRoute(localhost), 50);
            ConnManagerParams.setMaxConnectionsPerRoute(params, connPerRoute);
  
            // 超时设置
            /* 从连接池中取连接的超时时间 */
            ConnManagerParams.setTimeout(params, 1000);
            /* 连接超时 */
            HttpConnectionParams.setConnectionTimeout(params, 2000);
            /* 请求超时 */
            HttpConnectionParams.setSoTimeout(params, 4000);
            
            //重定向设置
            HttpClientParams.setRedirecting(params, true);
 
            // 设置我们的HttpClient支持HTTP和HTTPS两种模式
            SchemeRegistry schReg = new SchemeRegistry();
            schReg.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            schReg.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
 
            
            // 使用线程安全的连接管理来创建HttpClient
            ClientConnectionManager conMgr = new ThreadSafeClientConnManager(params, schReg);
            customerHttpClient = new DefaultHttpClient(conMgr, params);
        }
    	
        return customerHttpClient;
    }
    
    public static synchronized void closeHttpClient() {
    	if(customerHttpClient != null) {
    		customerHttpClient.getConnectionManager().shutdown();
    		customerHttpClient = null;
    	}
    }

}
