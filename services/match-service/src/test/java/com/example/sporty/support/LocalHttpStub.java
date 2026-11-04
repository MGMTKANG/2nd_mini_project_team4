package com.example.sporty.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** 외부 서비스 없이 실제 Feign HTTP 요청과 JSON 응답을 검증하는 루프백 서버. */
public final class LocalHttpStub implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService executor;
    private final Map<String, Response> responses = new ConcurrentHashMap<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final List<String> requestBodies = new CopyOnWriteArrayList<>();

    public LocalHttpStub() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        executor = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "local-http-stub");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void stub(String path, int status, String json) {
        stub(path, status, json, Duration.ZERO);
    }

    public void stub(String path, int status, String json, Duration delay) {
        responses.put(path, new Response(status, json, delay.toMillis()));
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    public List<String> requestBodies() { return List.copyOf(requestBodies); }

    public void reset() {
        responses.clear();
        requests.clear();
        requestBodies.clear();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            requests.add(new Request(exchange.getRequestMethod(), path,
                    decodeQuery(exchange.getRequestURI().getRawQuery())));
            Response response = responses.getOrDefault(path, new Response(404,
                    "{\"code\":\"STUB_NOT_CONFIGURED\",\"message\":\"No response configured\"}", 0));
            if (response.delayMillis() > 0) {
                try {
                    Thread.sleep(response.delayMillis());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            byte[] body = response.json().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(response.status(), body.length);
            exchange.getResponseBody().write(body);
        } finally {
            // 응답 시간 초과 테스트에서는 클라이언트가 먼저 연결을 닫을 수도 있다.
            exchange.close();
        }
    }

    private static Map<String, List<String>> decodeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> query = new LinkedHashMap<>();
        for (String parameter : rawQuery.split("&")) {
            String[] parts = parameter.split("=", 2);
            String name = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(parts.length == 2 ? parts[1] : "", StandardCharsets.UTF_8);
            query.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
        }
        query.replaceAll((name, values) -> List.copyOf(values));
        return Map.copyOf(query);
    }

    public record Request(String method, String path, Map<String, List<String>> query) {
    }

    private record Response(int status, String json, long delayMillis) {
    }
}
