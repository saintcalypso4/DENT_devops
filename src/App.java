import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Executors;

public class App {

    static final String DB_HOST = env("POSTGRES_HOST", "db");
    static final String DB_PORT = env("POSTGRES_PORT", "5432");
    static final String DB_NAME = env("POSTGRES_DB", "app");
    static final String DB_USER = env("POSTGRES_USER", "postgres");
    static final String DB_PASS = env("POSTGRES_PASSWORD", "1234");

    public static void main(String[] args) throws Exception {
        waitForDatabase();
        initDatabase();

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/health",
                exchange -> send(exchange, 200, "{\"status\":\"ok\",\"service\":\"dentistry-clinic\"}"));
        server.createContext("/tasks", App::handleTasks);
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();

        System.out.println("Dentistry server started on port 8080...");
    }

    // ---------- настройки и подключение ----------

    static String env(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    static Connection connect() throws SQLException {
        String url = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
        return DriverManager.getConnection(url, DB_USER, DB_PASS);
    }

    static void waitForDatabase() throws InterruptedException {
        for (int i = 1; i <= 30; i++) {
            try (Connection c = connect()) {
                System.out.println("Database is ready.");
                return;
            } catch (SQLException e) {
                System.out.println("Waiting for database... attempt " + i);
                Thread.sleep(2000);
            }
        }
        throw new RuntimeException("Database is not available");
    }

    static void initDatabase() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS tasks (
                    id    SERIAL PRIMARY KEY,
                    title TEXT NOT NULL,
                    done  BOOLEAN NOT NULL DEFAULT FALSE
                )
                """;
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }


    static void handleTasks(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {
            if (path.equals("/tasks") || path.equals("/tasks/")) {
                if ("GET".equals(method)) {
                    send(exchange, 200, listTasks().toString());
                } else if ("POST".equals(method)) {
                    JSONObject body = readJson(exchange);
                    JSONObject created = createTask(body.getString("title"), body.optBoolean("done", false));
                    send(exchange, 201, created.toString());
                } else {
                    send(exchange, 405, error("Method not allowed"));
                }
            } else {
                int id = parseId(path);
                if (id < 0) {
                    send(exchange, 404, error("Not found"));
                    return;
                }

                if ("GET".equals(method)) {
                    JSONObject task = getTask(id);
                    if (task == null) {
                        send(exchange, 404, error("Task not found"));
                    } else {
                        send(exchange, 200, task.toString());
                    }
                } else if ("PUT".equals(method)) {
                    JSONObject body = readJson(exchange);
                    JSONObject updated = updateTask(id, body.getString("title"), body.optBoolean("done", false));
                    if (updated == null) {
                        send(exchange, 404, error("Task not found"));
                    } else {
                        send(exchange, 200, updated.toString());
                    }
                } else if ("DELETE".equals(method)) {
                    if (deleteTask(id)) {
                        send(exchange, 204, null);
                    } else {
                        send(exchange, 404, error("Task not found"));
                    }
                } else {
                    send(exchange, 405, error("Method not allowed"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            send(exchange, 500, error(e.getMessage() == null ? "Internal error" : e.getMessage()));
        }
    }


    static JSONArray listTasks() throws SQLException {
        JSONArray result = new JSONArray();
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT id, title, done FROM tasks ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.put(toJson(rs));
            }
        }
        return result;
    }

    static JSONObject getTask(int id) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT id, title, done FROM tasks WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? toJson(rs) : null;
            }
        }
    }

    static JSONObject createTask(String title, boolean done) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO tasks (title, done) VALUES (?, ?) RETURNING id, title, done")) {
            ps.setString(1, title);
            ps.setBoolean(2, done);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return toJson(rs);
            }
        }
    }

    static JSONObject updateTask(int id, String title, boolean done) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE tasks SET title = ?, done = ? WHERE id = ? RETURNING id, title, done")) {
            ps.setString(1, title);
            ps.setBoolean(2, done);
            ps.setInt(3, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? toJson(rs) : null;
            }
        }
    }

    static boolean deleteTask(int id) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM tasks WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }


    static JSONObject toJson(ResultSet rs) throws SQLException {
        return new JSONObject()
                .put("id", rs.getInt("id"))
                .put("title", rs.getString("title"))
                .put("done", rs.getBoolean("done"));
    }

    static int parseId(String path) {
        try {
            String last = path.substring(path.lastIndexOf('/') + 1);
            return Integer.parseInt(last);
        } catch (Exception e) {
            return -1;
        }
    }

    static JSONObject readJson(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return new JSONObject(body);
    }

    static String error(String message) {
        return new JSONObject().put("message", message).toString();
    }

    static void send(HttpExchange exchange, int code, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        if (body == null || code == 204) {
            exchange.sendResponseHeaders(code, -1);
        } else {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
        exchange.close();
    }
}