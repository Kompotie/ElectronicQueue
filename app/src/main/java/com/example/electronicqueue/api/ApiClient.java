package com.example.electronicqueue.api;

import com.example.electronicqueue.model.JoinResponse;
import com.example.electronicqueue.model.StatusResponse;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ApiClient {

    // Android Emulator -> host machine.
    // Host port 8080 should be forwarded into Raspberry Pi Desktop VM (guest 8080).
    private static final String BASE_URL = "http://10.0.2.2:8080";

    public static JoinResponse join(String name) throws Exception {
        JSONObject body = new JSONObject();
        body.put("name", name);

        String resp = post("/queue/join", body.toString());
        JSONObject o = new JSONObject(resp);

        JoinResponse r = new JoinResponse();
        r.ticket = o.getInt("ticket");
        r.position = o.getInt("position");
        r.current_ticket = o.getInt("current_ticket");
        return r;
    }

    public static StatusResponse status(int ticket) throws Exception {
        String resp = get("/queue/status?ticket=" + ticket);
        JSONObject o = new JSONObject(resp);

        StatusResponse s = new StatusResponse();
        s.ticket = o.getInt("ticket");
        s.status = o.getString("status");
        s.position = o.getInt("position");
        s.current_ticket = o.getInt("current_ticket");
        return s;
    }

    private static String get(String path) throws Exception {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(4000);

        int code = conn.getResponseCode();
        String body = readAll(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
        if (code < 200 || code >= 300) {
            throw new Exception(extractError(body));
        }
        return body;
    }

    private static String post(String path, String jsonBody) throws Exception {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(4000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes("utf-8"));
        }

        int code = conn.getResponseCode();
        String body = readAll(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
        if (code < 200 || code >= 300) {
            throw new Exception(extractError(body));
        }
        return body;
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private static String extractError(String body) {
        try {
            JSONObject o = new JSONObject(body);
            if (o.has("error")) return o.getString("error");
        } catch (Exception ignored) {}
        return body;
    }
}
