package com.lyft.data.proxyserver;

import static org.testng.Assert.assertEquals;

import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import java.io.IOException;
import java.util.Random;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.testng.annotations.Test;

public class TestProxyServer {

  @Test
  public void testProxyServer() throws IOException {
    String mockResponseText = "Test1234";
    MockWebServer backend = newMockWebServer(mockResponseText);

    int serverPort = backend.getPort() + 1;
    ProxyServerConfiguration config = buildConfig(backend.getUrl("/").toString(), serverPort);

    try (ProxyServer proxyServer = new ProxyServer(config, new ProxyHandler())) {
      proxyServer.start();
      CloseableHttpClient httpclient = HttpClientBuilder.create().build();
      HttpUriRequest httpUriRequest = new HttpGet("http://localhost:" + serverPort);
      HttpResponse response = httpclient.execute(httpUriRequest);
      assertEquals(mockResponseText, EntityUtils.toString(response.getEntity()));
    } finally {
      backend.shutdown();
    }
  }

  @Test
  public void testCustomHeader() throws Exception {
    String mockResponseText = "CUSTOM HEADER TEST";
    MockWebServer backend = newMockWebServer(mockResponseText);

    int serverPort = backend.getPort() + 1;
    ProxyServerConfiguration config = buildConfig(backend.getUrl("/").toString(), serverPort);

    try (ProxyServer proxyServer = new ProxyServer(config, new ProxyHandler())) {
      proxyServer.start();
      CloseableHttpClient httpclient = HttpClientBuilder.create().build();
      HttpUriRequest httpUriRequest = new HttpGet("http://localhost:" + serverPort);
      httpUriRequest.setHeader("HEADER1", "FOO");
      httpUriRequest.setHeader("HEADER2", "BAR");

      HttpResponse response = httpclient.execute(httpUriRequest);
      assertEquals(mockResponseText, EntityUtils.toString(response.getEntity()));
      RecordedRequest recordedRequest = backend.takeRequest();
      assertEquals(recordedRequest.getHeader("HEADER1"), "FOO");
      assertEquals(recordedRequest.getHeader("HEADER2"), "BAR");
    } finally {
      backend.shutdown();
    }
  }

  @Test
  public void testHugeCustomHeadersBiggerThanDefaultBuffers() throws Exception {
    String mockResponseText = "HUGE CUSTOM HEADER TEST";
    MockWebServer backend = newMockWebServer(mockResponseText);

    int serverPort = backend.getPort() + 1;
    ProxyServerConfiguration config = buildConfig(backend.getUrl("/").toString(), serverPort);

    try (ProxyServer proxyServer = new ProxyServer(config, new ProxyHandler())) {
      proxyServer.start();
      CloseableHttpClient httpclient = HttpClientBuilder.create().build();
      HttpUriRequest httpUriRequest = new HttpGet("http://localhost:" + serverPort);

      int repetitions = 700;
      httpUriRequest.addHeader("HEADER1", repeat("FOO", repetitions));
      httpUriRequest.addHeader("HEADER2", repeat("BAR", repetitions));

      HttpResponse response = httpclient.execute(httpUriRequest);
      assertEquals(502, response.getStatusLine().getStatusCode());
      assertEquals("Bad Gateway", response.getStatusLine().getReasonPhrase());
      assertEquals(0, backend.getRequestCount());
    } finally {
      backend.shutdown();
    }
  }

  @Test
  public void testCustomConfigTunedForHugeCustomHeaders() throws Exception {
    String mockResponseText = "HUGE CUSTOM HEADER TEST";
    MockWebServer backend = newMockWebServer(mockResponseText);

    int serverPort = backend.getPort() + 1;
    ProxyServerConfiguration config = buildConfig(backend.getUrl("/").toString(), serverPort);
    // increase the buffer sizes to accommodate for the huge headers
    config.setRequestHeaderSize(32 * 1024);
    config.setResponseHeaderSize(32 * 1024);
    config.setRequestBufferSize(64 * 1024);
    config.setResponseBufferSize(64 * 1024);

    try (ProxyServer proxyServer = new ProxyServer(config, new ProxyHandler())) {
      proxyServer.start();
      CloseableHttpClient httpclient = HttpClientBuilder.create().build();
      HttpUriRequest httpUriRequest = new HttpGet("http://localhost:" + serverPort);

      int repetitions = 700;
      httpUriRequest.addHeader("HEADER1", repeat("FOO", repetitions));
      httpUriRequest.addHeader("HEADER2", repeat("BAR", repetitions));

      HttpResponse response = httpclient.execute(httpUriRequest);
      assertEquals(mockResponseText, EntityUtils.toString(response.getEntity()));
      RecordedRequest recordedRequest = backend.takeRequest();
      assertEquals(recordedRequest.getHeader("HEADER1"), repeat("FOO", repetitions));
      assertEquals(recordedRequest.getHeader("HEADER2"), repeat("BAR", repetitions));
    } finally {
      backend.shutdown();
    }
  }

  private static MockWebServer newMockWebServer(String mockResponseText) throws IOException {
    int backendPort = 30000 + new Random().nextInt(1000);

    MockWebServer backend = new MockWebServer();
    backend.enqueue(new MockResponse().setBody(mockResponseText));
    backend.play(backendPort);
    return backend;
  }

  private static String repeat(String str, int repetitions) {
    StringBuilder repeatedStr = new StringBuilder();
    for (int i = 0; i < repetitions; i++) {
      repeatedStr.append(str);
    }
    return repeatedStr.toString();
  }

  private ProxyServerConfiguration buildConfig(String backendUrl, int localPort) {
    ProxyServerConfiguration config = new ProxyServerConfiguration();
    config.setName("MockBackend");
    config.setPrefix("/");
    config.setPreserveHost("true");
    config.setProxyTo(backendUrl);
    config.setLocalPort(localPort);
    return config;
  }
}
