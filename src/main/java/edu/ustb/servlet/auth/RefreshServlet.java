package edu.ustb.servlet.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ustb.Main;
import edu.ustb.service.AuthService;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RefreshServlet extends HttpServlet {
    private final AuthService authService;
    private final ObjectMapper mapper = new ObjectMapper();
    public RefreshServlet(AuthService authService){ this.authService = authService; }

    @SuppressWarnings("unchecked")
    private Map<String,Object> readJson(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try(BufferedReader br = req.getReader()){ String line; while((line=br.readLine())!=null){ sb.append(line); } }
        if(sb.length()==0) return new HashMap<>();
        return mapper.readValue(sb.toString(), Map.class);
    }

    @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        try {
            Map<String,Object> body = readJson(req);
            String token = (String) body.get("token");
            String newToken = authService.refresh(token);
            Map<String,Object> data = new HashMap<>(); data.put("token", newToken); Main.writeOk(resp, data);
        }catch (RuntimeException ex){ Main.writeError(resp, 401, ex.getMessage(), ex.getMessage());
        }catch (Exception e){ Main.writeError(resp, 500, "SERVER_ERROR", e.getMessage());
        }
    }
}
