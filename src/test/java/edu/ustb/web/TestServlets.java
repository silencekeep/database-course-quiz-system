package edu.ustb.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ustb.service.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Test-only servlet implementations mirroring Main's inner classes. */
public class TestServlets {

    static String readBody(HttpServletRequest req) throws IOException {
        try (BufferedReader r = req.getReader()) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    static Integer authUser(AuthService auth, HttpServletRequest req) {
        String t = req.getHeader("X-Auth-Token");
        return t == null ? null : auth.validateToken(t);
    }

    public static class Register extends HttpServlet {
        private final AuthService auth;
        private final ObjectMapper mapper = new ObjectMapper();

        public Register(AuthService auth) {
            this.auth = auth;
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            Map<String, String> body = mapper.readValue(readBody(req), new com.fasterxml.jackson.core.type.TypeReference<>() {});
            try {
                String token = auth.register(body.get("account"), body.get("name"), body.get("password"));
                resp.setContentType("application/json");
                resp.getWriter().write("{\"token\":\"" + token + "\"}");
            } catch (Exception e) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    public static class Login extends HttpServlet {
        private final AuthService auth;
        private final ObjectMapper mapper = new ObjectMapper();

        public Login(AuthService auth) {
            this.auth = auth;
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            Map<String, String> body = mapper.readValue(readBody(req), new com.fasterxml.jackson.core.type.TypeReference<>() {});
            try {
                String token = auth.login(body.get("account"), body.get("password"));
                resp.setContentType("application/json");
                resp.getWriter().write("{\"token\":\"" + token + "\"}");
            } catch (Exception e) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    public static class CreateSurvey extends HttpServlet {
        private final SurveyService survey;
        private final AuthService auth;
        private final ObjectMapper mapper = new ObjectMapper();

        public CreateSurvey(SurveyService survey, AuthService auth) {
            this.survey = survey;
            this.auth = auth;
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            Integer uid = authUser(auth, req);
            if (uid == null) {
                resp.setStatus(401);
                resp.getWriter().write("{\"error\":\"UNAUTHORIZED\"}");
                return;
            }
            Map<String, Object> payload = mapper.readValue(readBody(req), new com.fasterxml.jackson.core.type.TypeReference<>() {});
            payload.put("creator_id", uid);
            String json = mapper.writeValueAsString(payload);
            try {
                var surv = survey.createSurveyFromJson(json);
                resp.setContentType("application/json");
                resp.getWriter().write(mapper.writeValueAsString(surv));
            } catch (Exception e) {
                resp.setStatus(500);
                resp.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    public static class UnifiedSurvey extends HttpServlet {
        private final SurveyService survey;
        private final SubmissionService submissionService;
        private final AuthService auth;
        private final ShareService shareService;
        private final ScoreboardService scoreboardService;
        private final SurveyQueryService queryService;
        private final ObjectMapper mapper = new ObjectMapper();

        public UnifiedSurvey(SurveyService survey, SubmissionService submissionService, AuthService auth,
                             ShareService shareService, ScoreboardService scoreboardService, SurveyQueryService queryService) {
            this.survey = survey;
            this.submissionService = submissionService;
            this.auth = auth;
            this.shareService = shareService;
            this.scoreboardService = scoreboardService;
            this.queryService = queryService;
        }

        Integer extractId(String path) {
            if (path == null) return null;
            String[] seg = path.split("/");
            if (seg.length < 2) return null;
            try {
                return Integer.parseInt(seg[1]);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            String path = req.getPathInfo();
            if (path == null) {
                resp.setStatus(400);
                return;
            }
            if (path.matches("^/\\d+$")) {
                var sv = survey.loadSurvey(extractId(path));
                if (sv == null) {
                    resp.setStatus(404);
                    return;
                }
                resp.setContentType("application/json");
                resp.getWriter().write(mapper.writeValueAsString(sv));
                return;
            }
            if (path.endsWith("/scoreboard")) {
                var list = scoreboardService.buildScoreboard(extractId(path));
                resp.setContentType("application/json");
                resp.getWriter().write(mapper.writeValueAsString(list));
                return;
            }
            if (path.endsWith("/shares")) {
                Integer uid = authUser(auth, req);
                if (uid == null) {
                    resp.setStatus(401);
                    return;
                }
                try {
                    var result = queryService.pageSurveyShares(extractId(path), uid, 1, 50);
                    resp.setContentType("application/json");
                    resp.getWriter().write(mapper.writeValueAsString(result));
                } catch (RuntimeException ex) {
                    resp.setStatus("FORBIDDEN".equals(ex.getMessage()) ? 403 : 500);
                }
                return;
            }
            resp.setStatus(404);
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            String path = req.getPathInfo();
            if (path == null) {
                resp.setStatus(400);
                return;
            }
            if (path.endsWith("/submitList")) {
                double total = submissionService.submitBatch(extractId(path), readBody(req), req.getRemoteAddr());
                resp.setContentType("application/json");
                resp.getWriter().write("{\"total_score\":" + total + "}");
                return;
            }
            if (path.endsWith("/share")) {
                Integer uid = authUser(auth, req);
                if (uid == null) {
                    resp.setStatus(401);
                    return;
                }
                var body = mapper.readValue(readBody(req), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                boolean allowEdit = Boolean.TRUE.equals(body.get("allow_edit"));
                String link = shareService.createShare(extractId(path), uid, allowEdit);
                Map<String, Object> res = new LinkedHashMap<>();
                res.put("link", link);
                resp.setContentType("application/json");
                resp.getWriter().write(mapper.writeValueAsString(res));
                return;
            }
            resp.setStatus(404);
        }
    }
}
