package mo.cc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.PropertyFilter;

import mo.cc.flow.ErrorFlow;
import mo.cc.flow.Image;
import mo.cc.flow.ImageFlow;
import mo.cc.flow.ListFlow;
import mo.cc.flow.TextFlow;
import mo.cc.server.RestEasyNettyServer;

public class Utils {
	/**
	 * 参数为RestEasy服务类和端口，根据参数启动服务
	 */
	public static void runService(Set<Class<?>> clazzs, String serverPort) {
		ResteasyDeployment resteasyDeployment = new ResteasyDeployment();
		for(Class<?> clazz : clazzs) {
			resteasyDeployment.getActualResourceClasses().add(clazz);
		}
		RestEasyNettyServer server = new RestEasyNettyServer(
				resteasyDeployment, serverPort);
		try {
			server.start();
			System.out.println("Port : " + serverPort);
			System.out.println("Server Started ...");
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Server Starting Failure ...");
		}
	}
	/**
	 * 根据状态码和Mo流格式中的Message-Type生成ResponseBuilder
	 * 其中Message-Type目前包括"text"、"list"、"image"
	 */
	private static ResponseBuilder genResponseBuilder(int statusCode
			, String messageType) {
		ResponseBuilder rb = Response.status(statusCode);
		rb.header("Content-Type", "application/json; charset=UTF-8");
		// 未找到正确的返回值或处理过程中出错
		rb.header("Message-Type", messageType);
		return rb;
	}
	/**
	 * 生成错误Response，格式为ErrorFlow，内容为一般提示性信息
	 * 需要传入状态码和error字段作为不展示给用户的开发者错误信息
	 */
	public static Response genErrorResponse(int statusCode, String error) {
		// 未找到正确的返回值或处理过程中出错
		ResponseBuilder rb = genResponseBuilder(statusCode, "error");
		ErrorFlow errorFlow = new ErrorFlow();
		errorFlow.description = "抱歉流好像出错了。";
		errorFlow.text = "试试别的功能？";
		errorFlow.error = error;
		rb.entity(Utils.parseToJSONString(errorFlow));
		return rb.build();
	}
	/**
	 * 生成TextFlow格式的Response
	 */
	public static Response genTextFlowResponse(String text
			, String description) {
		ResponseBuilder rb = genResponseBuilder(200, "text");
		TextFlow textFlow = new TextFlow();
		textFlow.text = text;
		textFlow.description = description;
		rb.entity(Utils.parseToJSONString(textFlow));
		return rb.build();
	}
	/**
	 * 传入ListFLow、ImageFlow或TextFlow三种正常返回结果
	 * 将对象转换为符合Mo格式的Response输出
	 * 出错时返回TextFlow格式的带有提示信息的Response
	 */
	public static Response genResponse(Object flow) {
		if(flow != null) {
			if(flow instanceof ListFlow) {
				ResponseBuilder rb = genResponseBuilder(200, "list");
				rb.entity(Utils.parseToJSONString((ListFlow) flow));
				return rb.build();
			}
			else if(flow instanceof ImageFlow) {
				ResponseBuilder rb = genResponseBuilder(200, "image");
				rb.entity(Utils.parseToJSONString((ImageFlow) flow));
				return rb.build();
			}
			else if(flow instanceof TextFlow) {
				ResponseBuilder rb = genResponseBuilder(200, "text");
				rb.entity(Utils.parseToJSONString((TextFlow) flow));
				return rb.build();
			}
		}
		return Utils.genErrorResponse(500, "Generating Response Error");
	}
	/**
	 * 将对象以JSON格式的String输出，
	 * 强制转换image为Image对象进行检查，
	 * 避免image对象没有内容时还会存在的问题，
	 * 转换失败时返回null
	 */
	private static String parseToJSONString(Object object) {
		try {
			String jsonString = JSON.toJSONString(object, new PropertyFilter() {
				@Override
				public boolean apply(Object source, String name, Object value) {
					if("image".equals(name)) {
						if(((Image) value).isValidated()) {
							return true;
						}
						else {
							return false;
						}
					}
					else if("pages".equals(name)) {
						if(((Integer) value) > 0) {
							return true;
						}
						else {
							return false;
						}
					}
					return true;
				}
			});
			return jsonString;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	/**
	 * 发送GET请求并返回JSON类型结果
	 * 出现错误或状态码不是2开头时返回null
	 */
	public static JSONArray genGetJSONArray(String url) {
		Set<Header> headers = new HashSet<Header>();
		return genGetJSONArray(url, headers);
	}
	/**
	 * 发送GET请求并返回JSON类型结果
	 * 出现错误或状态码不是2开头时返回null
	 */
	public static JSONArray genGetJSONArray(String url, Set<Header> headers) {
		try {
			CloseableHttpResponse rawResponse = Utils.sendGet(url, headers);
			int code = rawResponse.getStatusLine().getStatusCode();
			if(code >= 200 && code < 300) {
				String entity = EntityUtils.toString(rawResponse.getEntity()
						, "UTF-8");
				return JSON.parseArray(entity);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	/**
	 * 发送GET请求并返回JSON类型结果
	 * 出现错误或状态码不是2开头时返回null
	 */
	public static JSONObject genGetJSONObject(String url) {
		Set<Header> headers = new HashSet<Header>();
		return genGetJSONObject(url, headers);
	}
	/**
	 * 发送GET请求并返回JSON类型结果
	 * 出现错误或状态码不是2开头时返回null
	 */
	public static JSONObject genGetJSONObject(String url
			, String proxyHost, int proxyPort) {
		Set<Header> headers = new HashSet<Header>();
		return genGetJSONObject(url, headers, proxyHost, proxyPort);
	}
	/**
	 * 发送GET请求并返回JSON类型结果
	 * 出现错误或状态码不是2开头时返回null
	 */
	public static JSONObject genGetJSONObject(String url, Set<Header> headers) {
		try {
			CloseableHttpResponse rawResponse = Utils.sendGet(url, headers);
			int code = rawResponse.getStatusLine().getStatusCode();
			if(code >= 200 && code < 300) {
				String entity = EntityUtils.toString(rawResponse.getEntity()
						, "UTF-8");
				return JSON.parseObject(entity);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	/**
	 * 发送GET请求并返回JSON类型结果
	 * 出现错误或状态码不是2开头时返回null
	 */
	public static JSONObject genGetJSONObject(String url, Set<Header> headers
			, String proxyHost, int proxyPort) {
		try {
			CloseableHttpResponse rawResponse = Utils.sendGet(url, headers
					, proxyHost, proxyPort);
			int code = rawResponse.getStatusLine().getStatusCode();
			if(code >= 200 && code < 300) {
				String entity = EntityUtils.toString(rawResponse.getEntity()
						, "UTF-8");
				return JSON.parseObject(entity);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	/**
	 * 发送POST请求并返回JSON类型结果
	 * 出现错误或状态码不是2开头时返回null
	 */
	public static JSONObject genPostJSONObject(String url
			, List<NameValuePair> pairList) {
		Set<Header> headers = new HashSet<Header>();
		return genPostJSONObject(url, pairList, headers);
	}
	/**
	 * 发送POST请求并返回JSON类型结果
	 * 出现错误或状态码不是2开头时返回null
	 */
	public static JSONObject genPostJSONObject(String url
			, List<NameValuePair> pairList, String proxyHost
			, int proxyPort) {
		Set<Header> headers = new HashSet<Header>();
		return genPostJSONObject(url, pairList, headers
				, proxyHost, proxyPort);
	}
	/**
	 * 发送POST请求并返回JSON类型结果
	 * 出现错误或状态码不是2开头时返回null
	 */
	public static JSONObject genPostJSONObject(String url
			, List<NameValuePair> pairList, Set<Header> headers) {
		try {
			CloseableHttpResponse rawResponse = Utils.sendPost(url
					, pairList, headers);
			int code = rawResponse.getStatusLine().getStatusCode();
			if(code >= 200 && code < 300) {
				String entity = EntityUtils.toString(rawResponse.getEntity()
						, "UTF-8");
				return JSON.parseObject(entity);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	/**
	 * 发送POST请求并返回JSON类型结果
	 * 出现错误或状态码不是2开头时返回null
	 */
	public static JSONObject genPostJSONObject(String url
			, List<NameValuePair> pairList, Set<Header> headers
			, String proxyHost, int proxyPort) {
		try {
			CloseableHttpResponse rawResponse = Utils.sendPost(url
					, pairList, headers, proxyHost, proxyPort);
			int code = rawResponse.getStatusLine().getStatusCode();
			if(code >= 200 && code < 300) {
				String entity = EntityUtils.toString(rawResponse.getEntity()
						, "UTF-8");
				return JSON.parseObject(entity);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	/**
	 * 根据URL发送GET请求并返回Document类型结果
	 * 出现错误或状态码不是2开头时返回null
	 */
	public static Document genGetDocument(String url) {
		Set<Header> headers = new HashSet<Header>();
		return genGetDocument(url, headers);
	}
	/**
	 * 发送GET请求并返回Document类型结果
	 * 出现错误或状态码不是2开头时返回null
	 */
	public static Document genGetDocument(String url, String proxyHost
			, int proxyPort) {
		Set<Header> headers = new HashSet<Header>();
		return genGetDocument(url, headers, proxyHost, proxyPort);
	}
	/**
	 * 发送GET请求并返回Document类型结果
	 * 出现错误或状态码不是2开头时返回null
	 */
	public static Document genGetDocument(String url, Set<Header> headers
			, String proxyHost, int proxyPort) {
		try {
			CloseableHttpResponse rawResponse = Utils.sendGet(url, headers
					, proxyHost, proxyPort);
			int code = rawResponse.getStatusLine().getStatusCode();
			if(code >= 200 && code < 300) {
				String entity = EntityUtils.toString(rawResponse.getEntity()
						, "UTF-8");
				return Jsoup.parse(entity);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	/**
	 * 发送GET请求并返回Document类型结果
	 * 出现错误或状态码不是2开头时返回null
	 */
	public static Document genGetDocument(String url, Set<Header> headers) {
		try {
			CloseableHttpResponse rawResponse = Utils.sendGet(url, headers);
			int code = rawResponse.getStatusLine().getStatusCode();
			if(code >= 200 && code < 300) {
				String entity = EntityUtils.toString(rawResponse.getEntity()
						, "UTF-8");
				return Jsoup.parse(entity);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	/**
	 * 发送HTTP的GET请求，失败时返回null
	 */
	public static CloseableHttpResponse sendGet(String url) {
		Set<Header> headers = new HashSet<Header>();
		return sendGet(url, headers);
	}
	/**
	 * 发送HTTP的GET请求，失败时返回null
	 */
	public static CloseableHttpResponse sendGet(String url, String proxyHost
			, int proxyPort) {
		Set<Header> headers = new HashSet<Header>();
		return sendGet(url, headers, proxyHost, proxyPort);
	}
	/**
	 * 发送HTTP的GET请求，失败时返回null
	 */
	public static CloseableHttpResponse sendGet(String url, Set<Header> headers) {
		CloseableHttpResponse response;
		try {
			CloseableHttpClient httpClient;
			if(url.startsWith("https")) {
				// https
				SSLContext sslContext = SSLContexts.custom()
						.loadTrustMaterial(null, new TrustSelfSignedStrategy())
						.useTLS()
						.build();
				SSLConnectionSocketFactory sslSocketFactory = 
						new SSLConnectionSocketFactory(sslContext
								, new AllowAllHostnameVerifier());
				httpClient = HttpClients.custom().setSSLSocketFactory(
						sslSocketFactory).build();
			}
			else {
				httpClient = HttpClients.createDefault();
			}
			HttpGet httpGet = new HttpGet(url);
			for(Header header : headers) {
				httpGet.addHeader(header);
			}
			response = httpClient.execute(httpGet);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return response;
	}
	/**
	 * 发送HTTP的GET请求，失败时返回null
	 */
	public static CloseableHttpResponse sendGet(String url, Set<Header> headers
			, String proxyHost, int proxyPort) {
		Registry<ConnectionSocketFactory> reg = RegistryBuilder
				.<ConnectionSocketFactory>create()
                .register("http", new MyConnectionSocketFactory())
                .build();
        PoolingHttpClientConnectionManager cm = 
        		new PoolingHttpClientConnectionManager(reg);
        CloseableHttpClient httpClient;
		if(url.startsWith("https")) {
			// https
			SSLContext sslContext;
			try {
				sslContext = SSLContexts.custom()
						.loadTrustMaterial(null, new TrustSelfSignedStrategy())
						.useTLS()
						.build();
				SSLConnectionSocketFactory sslSocketFactory = 
						new SSLConnectionSocketFactory(sslContext
								, new AllowAllHostnameVerifier());
				httpClient = HttpClients.custom().setSSLSocketFactory(
						sslSocketFactory).setConnectionManager(cm).build();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
		}
		else {
			httpClient = HttpClients.custom()
	                .setConnectionManager(cm)
	                .build();
		}
		CloseableHttpResponse response;
		try {
			InetSocketAddress socksaddr = new InetSocketAddress(proxyHost, proxyPort);
            HttpClientContext context = HttpClientContext.create();
            context.setAttribute("socks.address", socksaddr);
			HttpGet httpGet = new HttpGet(url);
			for(Header header : headers) {
				httpGet.addHeader(header);
			}
			response = httpClient.execute(httpGet, context);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return response;
	}
	/**
	 * 发送HTTP个POST请求，失败时返回null
	 */
	public static CloseableHttpResponse sendPost(String url
			, List<NameValuePair> pairList) {
		Set<Header> headers = new HashSet<Header>();
		return sendPost(url, pairList, headers);
	}
	/**
	 * 发送HTTP个POST请求，失败时返回null
	 */
	public static CloseableHttpResponse sendPost(String url
			, List<NameValuePair> pairList, String proxyHost
			, int proxyPort) {
		Set<Header> headers = new HashSet<Header>();
		return sendPost(url, pairList, headers, proxyHost, proxyPort);
	}
	/**
	 * 发送HTTP的POST请求，出错时返回null
	 */
	public static CloseableHttpResponse sendPost(String url
			, List<NameValuePair> pairList, Set<Header> headers) {
		CloseableHttpResponse response;
		try {
			// 实现将请求的参数封装到表单中，即请求体中
			UrlEncodedFormEntity entity = new UrlEncodedFormEntity(
					pairList, "UTF-8");
			// 使用post方式提交数据
			HttpPost httpPost = new HttpPost(url);
			for(Header header : headers) {
				httpPost.addHeader(header);
			}
			httpPost.setEntity(entity);
			// 执行post请求，并获取服务器端的响应HttpResponse
			CloseableHttpClient httpClient;
			if(url.startsWith("https")) {
				// https
				SSLContext sslContext = SSLContexts.custom()
						.loadTrustMaterial(null, new TrustSelfSignedStrategy())
						.useTLS()
						.build();
				SSLConnectionSocketFactory sslSocketFactory = 
						new SSLConnectionSocketFactory(sslContext
								, new AllowAllHostnameVerifier());
				httpClient = HttpClients.custom().setSSLSocketFactory(
						sslSocketFactory).build();
			}
			else {
				httpClient = HttpClients.createDefault();
			}
			response = httpClient.execute(httpPost);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return response;
	}
	/**
	 * 发送HTTP的POST请求，出错时返回null
	 */
	public static CloseableHttpResponse sendPost(String url
			, List<NameValuePair> pairList, Set<Header> headers
			, String proxyHost, int proxyPort) {
		Registry<ConnectionSocketFactory> reg = RegistryBuilder
				.<ConnectionSocketFactory>create()
                .register("http", new MyConnectionSocketFactory())
                .build();
        PoolingHttpClientConnectionManager cm = 
        		new PoolingHttpClientConnectionManager(reg);
        CloseableHttpClient httpClient;
		if(url.startsWith("https")) {
			// https
			SSLContext sslContext;
			try {
				sslContext = SSLContexts.custom()
						.loadTrustMaterial(null, new TrustSelfSignedStrategy())
						.useTLS()
						.build();
				SSLConnectionSocketFactory sslSocketFactory = 
						new SSLConnectionSocketFactory(sslContext
								, new AllowAllHostnameVerifier());
				httpClient = HttpClients.custom().setSSLSocketFactory(
						sslSocketFactory).setConnectionManager(cm).build();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
		}
		else {
			httpClient = HttpClients.custom()
	                .setConnectionManager(cm)
	                .build();
		}
		CloseableHttpResponse response;
		try {
			InetSocketAddress socksaddr = new InetSocketAddress(proxyHost, proxyPort);
            HttpClientContext context = HttpClientContext.create();
            context.setAttribute("socks.address", socksaddr);
			// 实现将请求的参数封装到表单中，即请求体中
			UrlEncodedFormEntity entity = new UrlEncodedFormEntity(
					pairList, "UTF-8");
			// 使用post方式提交数据
			HttpPost httpPost = new HttpPost(url);
			for(Header header : headers) {
				httpPost.addHeader(header);
			}
			httpPost.setEntity(entity);
			// 执行post请求，并获取服务器端的响应HttpResponse
			response = httpClient.execute(httpPost, context);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return response;
	}
}

class MyConnectionSocketFactory implements ConnectionSocketFactory {
	@Override
	public Socket createSocket(HttpContext context) 
			throws IOException {
		InetSocketAddress socksaddr = (InetSocketAddress) 
				context.getAttribute("socks.address");
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, socksaddr);
        return new Socket(proxy);
	}
	@Override
	public Socket connectSocket(int connectTimeout
			, Socket sock, HttpHost host
			, InetSocketAddress remoteAddress
			, InetSocketAddress localAddress
			, HttpContext context)
			throws IOException {
		Socket socket;
        if (sock != null) {
            socket = sock;
        }
        else {
        	socket = createSocket(context);
        }
        if (localAddress != null) {
        	socket.bind(localAddress);
        }
        try {
        	socket.connect(remoteAddress, connectTimeout);
        } catch (SocketTimeoutException ex) {
            throw new ConnectTimeoutException(
            		ex, host, remoteAddress.getAddress());
        }
        return socket;
	}
}