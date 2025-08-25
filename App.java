import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

public class App {

    static final int PORT = 8080;
    static final String CSV = "data_tasks.csv";

    static TaskRepository repo = new TaskRepository(CSV);

    public static void main(String[] args) throws Exception {
        repo.load();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new RootHandler());
        server.createContext("/api/tasks", new ApiTasksHandler());
        server.setExecutor(null);
        System.out.println("Servindo em http://localhost:" + PORT);
        server.start();
    }

    static class Task {
        private String id;
        private String titulo;
        private String descricao;
        private int status; // 0 TODO, 1 DOING, 2 DONE
        private long criadoEm;

        public Task(String titulo, String descricao) {
            this.id = UUID.randomUUID().toString().substring(0, 8);
            this.titulo = titulo;
            this.descricao = descricao;
            this.status = 0;
            this.criadoEm = System.currentTimeMillis();
        }

        public Task(String id, String titulo, String descricao, int status, long criadoEm) {
            this.id = id;
            this.titulo = titulo;
            this.descricao = descricao;
            this.status = status;
            this.criadoEm = criadoEm;
        }

        // getters e setters
        public String getId() { return id; }
        public String getTitulo() { return titulo; }
        public String getDescricao() { return descricao; }
        public int getStatus() { return status; }
        public long getCriadoEm() { return criadoEm; }

        public void setStatus(int status) {
            this.status = Math.max(0, Math.min(2, status));
        }

        public String toJson() {
            return String.format(
                "{\"id\":\"%s\",\"titulo\":\"%s\",\"descricao\":\"%s\",\"status\":%d,\"criadoEm\":%d}",
                esc(titulo), esc(descricao), esc(id), status, criadoEm
            ).replace("desc","desc"); 
        }

        public String toCsv() {
            return String.join(";",
                esc(id), esc(titulo), esc(descricao),
                String.valueOf(status), String.valueOf(criadoEm)
            );
        }

        private static String esc(String s) {
            if (s == null) return "";
            if (s.contains(";") || s.contains("\"") || s.contains("\n"))
                return "\"" + s.replace("\"", "\"\"") + "\"";
            return s;
        }
    }

    static class TaskRepository {
        private List<Task> tasks = new ArrayList<>();
        private final String file;

        public TaskRepository(String file) { this.file = file; }

        public List<Task> listar() { return tasks; }
        public Task criar(String titulo, String descricao) {
            Task t = new Task(titulo, descricao);
            tasks.add(t);
            salvar();
            return t;
        }
        public Task buscar(String id) {
            return tasks.stream().filter(t -> t.getId().equals(id)).findFirst().orElse(null);
        }
        public void remover(String id) {
            tasks.removeIf(t -> t.getId().equals(id));
            salvar();
        }
        public void salvar() {
            Path p = Paths.get(file);
            try {
                if (p.getParent() != null) Files.createDirectories(p.getParent());
                try (BufferedWriter bw = Files.newBufferedWriter(p, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    bw.write("id;titulo;descricao;status;criadoEm\n");
                    for (Task t : tasks) {
                        bw.write(t.toCsv() + "\n");
                    }
                }
            } catch (IOException e) {
                System.out.println("Erro ao salvar CSV: " + e.getMessage());
            }
        }
        public void load() {
            tasks.clear();
            Path p = Paths.get(file);
            if (!Files.exists(p)) return;
            try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("id;")) continue;
                    String[] a = line.split(";");
                    if (a.length < 5) continue;
                    tasks.add(new Task(a[0], a[1], a[2],
                            Integer.parseInt(a[3]), Long.parseLong(a[4])));
                }
            } catch (IOException e) {
                System.out.println("Erro ao carregar CSV: " + e.getMessage());
            }
        }
    }

    static class RootHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, ""); return; }
            byte[] body = INDEX_HTML.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        }
    }

    static class ApiTasksHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            String method = ex.getRequestMethod();
            URI uri = ex.getRequestURI();
            String path = uri.getPath();

            try {
                if ("GET".equals(method) && "/api/tasks".equals(path)) {
                    sendJson(ex, 200, listarJson());
                    return;
                }
                if ("POST".equals(method) && "/api/tasks".equals(path)) {
                    String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    String titulo = jsonGet(body, "titulo");
                    String descricao = jsonGet(body, "descricao");
                    if (titulo == null || titulo.isBlank()) {
                        sendJson(ex, 400, "{\"error\":\"titulo obrigatório\"}");
                        return;
                    }
                    Task t = repo.criar(titulo, descricao == null ? "" : descricao);
                    sendJson(ex, 200, t.toJson());
                    return;
                }
                if ("PATCH".equals(method) && path.startsWith("/api/tasks/") && path.endsWith("/status")) {
                    String id = path.substring("/api/tasks/".length(), path.length() - "/status".length());
                    Task t = repo.buscar(id);
                    if (t == null) { sendJson(ex, 404, "{\"error\":\"not found\"}"); return; }
                    String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    String stStr = jsonGet(body, "status");
                    int st = Integer.parseInt(stStr);
                    t.setStatus(st);
                    repo.salvar();
                    sendJson(ex, 200, t.toJson());
                    return;
                }
                if ("DELETE".equals(method) && path.startsWith("/api/tasks/")) {
                    String id = path.substring("/api/tasks/".length());
                    repo.remover(id);
                    sendJson(ex, 204, "");
                    return;
                }
                send(ex, 404, "");
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(ex, 500, "{\"error\":\"server\"}");
            }
        }
    }

    static void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
    static void sendJson(HttpExchange ex, int code, String body) throws IOException {
        ex.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        send(ex, code, body==null?"":body);
    }

    static String listarJson() {
        StringBuilder sb = new StringBuilder("[");
        List<Task> ts = repo.listar();
        for (int i=0;i<ts.size();i++){
            if (i>0) sb.append(",");
            sb.append(ts.get(i).toJson());
        }
        sb.append("]");
        return sb.toString();
    }

    static String jsonGet(String body, String key){
        if (body == null) return null;
        body = body.replaceAll("[{}\"]", "");
        for (String kv : body.split(",")) {
            String[] p = kv.split(":");
            if (p.length==2 && p[0].trim().equals(key)) return p[1].trim();
        }
        return null;
    }

    static final String INDEX_HTML = "<html><body><h1>Kanban</h1></body></html>";
}
