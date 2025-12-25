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

    public static String BASE_URL = "http://10.0.2.2:8080";

    private static String readAll(InputStream in) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private static String get(String path) throws Exception {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(4000);
        c.setReadTimeout(4000);

        int code = c.getResponseCode();
        InputStream in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
        String body = readAll(in);
        if (code < 200 || code >= 300) throw new RuntimeException(body);
        return body;
    }

    private static String post(String path, String jsonBody) throws Exception {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(4000);
        c.setReadTimeout(4000);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        byte[] out = jsonBody.getBytes("utf-8");
        try (OutputStream os = c.getOutputStream()) {
            os.write(out);
        }

        int code = c.getResponseCode();
        InputStream in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
        String body = readAll(in);
        if (code < 200 || code >= 300) throw new RuntimeException(body);
        return body;
    }

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

        StatusResponse r = new StatusResponse();
        r.ticket = o.getInt("ticket");
        r.status = o.getString("status");
        r.position = o.getInt("position");
        r.current_ticket = o.getInt("current_ticket");
        return r;
    }
}
