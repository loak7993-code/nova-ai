package com.nova.ai.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Tools {

    public static class ToolResult {
        public final String content;
        public final List<String> sources;

        public ToolResult(String content, List<String> sources) {
            this.content = content;
            this.sources = sources != null ? sources : new ArrayList<>();
        }
    }

    public static JsonArray toolDefinitions() {
        JsonArray tools = new JsonArray();

        JsonObject timeTool = new JsonObject();
        timeTool.addProperty("type", "function");
        JsonObject timeFn = new JsonObject();
        timeFn.addProperty("name", "get_current_time");
        timeFn.addProperty("description", "Get the current date and time. Use when the user asks about the current time, date, day of week, or any time-relative question.");
        JsonObject timeParams = new JsonObject();
        timeParams.addProperty("type", "object");
        timeParams.add("properties", new JsonObject());
        timeFn.add("parameters", timeParams);
        timeTool.add("function", timeFn);
        tools.add(timeTool);

        JsonObject calcTool = new JsonObject();
        calcTool.addProperty("type", "function");
        JsonObject calcFn = new JsonObject();
        calcFn.addProperty("name", "calculate");
        calcFn.addProperty("description", "Evaluate a mathematical expression. Supports +, -, *, /, ^, parentheses, sqrt(), sin(), cos(), tan(), log(), ln(), pi, e. Use for any arithmetic or math computation.");
        JsonObject calcParams = new JsonObject();
        calcParams.addProperty("type", "object");
        JsonObject exprProp = new JsonObject();
        exprProp.addProperty("type", "string");
        exprProp.addProperty("description", "The mathematical expression to evaluate, e.g. '17*23' or 'sqrt(144) + 2'");
        JsonObject props = new JsonObject();
        props.add("expression", exprProp);
        calcParams.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("expression");
        calcParams.add("required", required);
        calcFn.add("parameters", calcParams);
        calcTool.add("function", calcFn);
        tools.add(calcTool);

        JsonObject searchTool = new JsonObject();
        searchTool.addProperty("type", "function");
        JsonObject searchFn = new JsonObject();
        searchFn.addProperty("name", "web_search");
        searchFn.addProperty("description", "Search the web for current information. Use when the user asks about recent events, news, current facts, or anything that requires up-to-date information beyond your training data.");
        JsonObject searchParams = new JsonObject();
        searchParams.addProperty("type", "object");
        JsonObject queryProp = new JsonObject();
        queryProp.addProperty("type", "string");
        queryProp.addProperty("description", "The search query");
        JsonObject searchProps = new JsonObject();
        searchProps.add("query", queryProp);
        searchParams.add("properties", searchProps);
        JsonArray searchRequired = new JsonArray();
        searchRequired.add("query");
        searchParams.add("required", searchRequired);
        searchFn.add("parameters", searchParams);
        searchTool.add("function", searchFn);
        tools.add(searchTool);

        return tools;
    }

    public static ToolResult execute(String toolName, String arguments) {
        try {
            JsonObject args = arguments == null || arguments.isEmpty()
                    ? new JsonObject()
                    : JsonParser.parseString(arguments).getAsJsonObject();

            switch (toolName) {
                case "get_current_time":
                    return getCurrentTime(args);
                case "calculate":
                    return calculate(args);
                case "web_search":
                    return webSearch(args);
                default:
                    return new ToolResult("Unknown tool: " + toolName, new ArrayList<>());
            }
        } catch (Exception e) {
            return new ToolResult("Tool error: " + e.getMessage(), new ArrayList<>());
        }
    }

    private static ToolResult getCurrentTime(JsonObject args) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a z", Locale.getDefault());
        return new ToolResult("Current date and time: " + sdf.format(new Date()), new ArrayList<>());
    }

    private static ToolResult calculate(JsonObject args) {
        String expr = args.has("expression") ? args.get("expression").getAsString() : "";
        double result = evalMath(expr);
        String out;
        if (result == Math.floor(result) && !Double.isInfinite(result)) {
            out = String.format(Locale.US, "%.0f", result);
        } else {
            out = String.format(Locale.US, "%.6f", result).replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return new ToolResult(out, new ArrayList<>());
    }

    private static ToolResult webSearch(JsonObject args) {
        String query = args.has("query") ? args.get("query").getAsString() : "";
        if (query.isEmpty()) return new ToolResult("No search query provided.", new ArrayList<>());
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();

            String baseUrl = com.nova.ai.data.Settings.get().searchUrl;
            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                return new ToolResult("Search not configured. Set a SearXNG URL in Settings → Search Engine.", new ArrayList<>());
            }
            baseUrl = baseUrl.replaceAll("/+$", "");

            String searchUrl = baseUrl + "/search?q="
                    + java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")
                    + "&format=json";
            Request searchReq = new Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", "NovaAI/1.7")
                    .header("Accept", "application/json")
                    .build();

            String responseBody;
            try (Response searchResp = client.newCall(searchReq).execute()) {
                if (!searchResp.isSuccessful() || searchResp.body() == null) {
                    return new ToolResult("Search failed (HTTP " + searchResp.code() + ")", new ArrayList<>());
                }
                responseBody = searchResp.body().string();
            }

            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray resultsArr = root.has("results") ? root.getAsJsonArray("results") : new JsonArray();

            if (resultsArr.size() == 0) {
                return new ToolResult("No results found for: " + query, new ArrayList<>());
            }

            List<String> sources = new ArrayList<>();
            StringBuilder combined = new StringBuilder();
            int fetched = 0;
            for (int i = 0; i < resultsArr.size() && fetched < 5; i++) {
                JsonObject res = resultsArr.get(i).getAsJsonObject();
                String title = res.has("title") && !res.get("title").isJsonNull() ? res.get("title").getAsString() : "";
                String content = res.has("content") && !res.get("content").isJsonNull() ? res.get("content").getAsString() : "";
                String url = res.has("url") && !res.get("url").isJsonNull() ? res.get("url").getAsString() : "";

                if (content.isEmpty() && title.isEmpty()) continue;

                String domain = extractDomain(url);
                if (!domain.isEmpty()) sources.add(domain);

                if (combined.length() > 0) combined.append("\n\n");
                combined.append(title);
                if (!content.isEmpty()) combined.append(": ").append(content);

                fetched++;
            }

            if (sources.isEmpty()) {
                return new ToolResult(combined.length() > 0 ? combined.toString() : "No content found.", new ArrayList<>());
            }
            return new ToolResult(combined.toString(), sources);
        } catch (Exception e) {
            return new ToolResult("Search failed: " + e.getMessage(), new ArrayList<>());
        }
    }

    private static String extractDomain(String url) {
        if (url == null || url.isEmpty()) return "";
        try {
            String noProto = url.replaceAll("^https?://", "");
            int slash = noProto.indexOf('/');
            String domain = slash > 0 ? noProto.substring(0, slash) : noProto;
            if (domain.startsWith("www.")) domain = domain.substring(4);
            return domain;
        } catch (Exception e) {
            return "";
        }
    }

    private static double evalMath(String expr) {
        expr = expr.trim()
                .replaceAll("\\^", "**")
                .replaceAll("\\bln\\(", "_log(")
                .replaceAll("\\blog\\(", "_log10(")
                .replaceAll("\\bsqrt\\(", "_sqrt(")
                .replaceAll("\\bsin\\(", "_sin(")
                .replaceAll("\\bcos\\(", "_cos(")
                .replaceAll("\\btan\\(", "_tan(")
                .replaceAll("\\bpi\\b", String.valueOf(Math.PI))
                .replaceAll("\\be\\b", String.valueOf(Math.E));
        return new ExprParser(expr).parse();
    }

    private static class ExprParser {
        private final String str;
        private int pos = -1;
        private int ch;

        ExprParser(String str) { this.str = str; nextChar(); }

        private void nextChar() { ch = (++pos < str.length()) ? str.charAt(pos) : -1; }

        private boolean eat(int charToEat) {
            while (ch == ' ') nextChar();
            if (ch == charToEat) { nextChar(); return true; }
            return false;
        }

        double parse() {
            nextChar();
            double x = parseExpression();
            if (pos < str.length()) throw new RuntimeException("Unexpected: " + (char) ch);
            return x;
        }

        private double parseExpression() {
            double x = parseTerm();
            for (;;) {
                if (eat('+')) x += parseTerm();
                else if (eat('-')) x -= parseTerm();
                else return x;
            }
        }

        private double parseTerm() {
            double x = parseFactor();
            for (;;) {
                if (eat('*')) x *= parseFactor();
                else if (eat('/')) x /= parseFactor();
                else return x;
            }
        }

        private double parseFactor() {
            if (eat('+')) return parseFactor();
            if (eat('-')) return -parseFactor();

            double x;
            int startPos = this.pos;
            if (eat('(')) { x = parseExpression(); eat(')'); }
            else if ((ch >= '0' && ch <= '9') || ch == '.') {
                while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                x = Double.parseDouble(str.substring(startPos, this.pos));
            } else if (ch >= 'a' && ch <= 'z' || ch == '_') {
                while (ch >= 'a' && ch <= 'z' || ch == '_') nextChar();
                String func = str.substring(startPos, this.pos);
                if (func.equals("**")) { x = Math.pow(parseFactor(), parseFactor()); return x; }
                x = parseFactor();
                switch (func) {
                    case "_sqrt": x = Math.sqrt(x); break;
                    case "_sin": x = Math.sin(x); break;
                    case "_cos": x = Math.cos(x); break;
                    case "_tan": x = Math.tan(x); break;
                    case "_log": x = Math.log(x); break;
                    case "_log10": x = Math.log10(x); break;
                    default: throw new RuntimeException("Unknown function: " + func);
                }
            } else {
                throw new RuntimeException("Unexpected: " + (char) ch);
            }

            if (eat('^')) x = Math.pow(x, parseFactor());
            return x;
        }
    }
}
