package com.datanexus.datanexus.service.ai;

import com.datanexus.datanexus.dto.websocket.AnalyzeResponse;
import com.datanexus.datanexus.service.ai.provider.AIPromptBuilder;
import com.datanexus.datanexus.service.ai.provider.AIProvider;
import com.datanexus.datanexus.service.ai.provider.AIRequest;
import com.datanexus.datanexus.service.ai.provider.AIResponse;
import com.datanexus.datanexus.service.ai.provider.StreamChunkHandler;

import static com.datanexus.datanexus.service.ai.DataSummarizer.serializeDataForHtml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Service responsible for post-query AI analysis and dashboard HTML generation.
 * <p>
 * Flow:
 * 1. analyzeResults()    — sends structural data summary to AI → gets analysis + keyMetrics + chartSuggestions (JSON)
 * 2. generateDashboard() — sends analysis to AI → gets a small chart-config JSON (columns + metric values only)
 *                          → server builds the full, guaranteed-valid HTML from a fixed skeleton
 * <p>
 * The server owns all HTML/CSS/JS.  The AI only decides WHAT to show, not HOW to render it.
 * The generated HTML is stored in the persisted Message record (content JSON field)
 * and served from the DB by DashboardController — no in-memory cache needed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataAnalysisService {

    private final ObjectMapper objectMapper;

    // ─── Phase 1: Analyze the query results ──────────────────────────────

    /**
     * Ask the AI to analyze the raw query result data.
     * Returns a structured AnalysisResult with analysis text, metrics, and chart suggestions.
     */
    public AnalysisResult analyzeResults(AIProvider aiProvider,
                                         String userQuestion,
                                         List<AnalyzeResponse.QueryResult> queryResults,
                                         StreamChunkHandler chunkHandler) {
        // Only analyze results that actually have data
        List<AnalyzeResponse.QueryResult> successfulResults = queryResults.stream()
                .filter(qr -> qr.getData() != null && !qr.getData().isEmpty())
                .toList();

        if (successfulResults.isEmpty()) {
            log.info("No successful query results to analyze");
            return AnalysisResult.builder()
                    .analysis("No data was returned from the queries to analyze.")
                    .title("No Data")
                    .keyMetricsJson("[]")
                    .chartSuggestionsJson("[]")
                    .build();
        }

        String analysisPrompt = AIPromptBuilder.buildAnalysisPrompt(userQuestion, successfulResults);

        try {
            // Use a simple AIRequest with the analysis prompt as the user message
            AIRequest analysisRequest = AIRequest.builder()
                    .userMessage("Give me a AI analysis")
                    .prompt(analysisPrompt)
                    .availableSchemas(Collections.emptyList())
                    .conversationHistory(Collections.emptyList())
                    .firstMessage(true)
                    .rawPrompt(true)
                    .build();

            // Call AI — use streamChat for real-time feedback
            AIResponse response = aiProvider.streamChat(analysisRequest, chunk -> {
                if (chunkHandler != null) {
                    chunkHandler.onChunk(chunk);
                }
            });

            // Parse the AI's analysis JSON response
            return parseAnalysisResponse(response.getContent());

        } catch (Exception e) {
            log.error("AI analysis failed: {}", e.getMessage(), e);
            return AnalysisResult.builder()
                    .analysis("Analysis could not be completed: " + e.getMessage())
                    .title("Analysis Error")
                    .keyMetricsJson("[]")
                    .chartSuggestionsJson("[]")
                    .build();
        }
    }

    // ─── Phase 2: Generate the HTML dashboard ────────────────────────────

    /**
     * Ask the AI for a small JSON chart-config, then build the full HTML
     * dashboard server-side from a fixed skeleton.
     * <p>
     * The AI never writes HTML — it only tells us which columns to plot and
     * what the computed metric values are.  The server injects the real data
     * rows and renders everything using a stable, tested template.
     */
    public DashboardResult generateDashboard(AIProvider aiProvider,
                                             AnalysisResult analysisResult,
                                             List<AnalyzeResponse.QueryResult> queryResults,
                                             StreamChunkHandler chunkHandler) {
        List<AnalyzeResponse.QueryResult> successfulResults = queryResults.stream()
                .filter(qr -> qr.getData() != null && !qr.getData().isEmpty())
                .toList();

        if (successfulResults.isEmpty()) {
            log.info("No data for dashboard generation");
            return DashboardResult.builder()
                    .dashboardId(null)
                    .html(null)
                    .build();
        }

        // ── Step 1: ask AI only for chart-config JSON ─────────────────────
        String dashboardPrompt = AIPromptBuilder.buildDashboardPrompt(
                analysisResult.getTitle(),
                analysisResult.getAnalysis(),
                analysisResult.getKeyMetricsJson(),
                analysisResult.getChartSuggestionsJson(),
                successfulResults);

        DashboardConfig dashboardConfig;
        try {
            AIRequest dashboardRequest = AIRequest.builder()
                    .userMessage("Generate dashboard chart configuration")
                    .prompt(dashboardPrompt)
                    .availableSchemas(Collections.emptyList())
                    .conversationHistory(Collections.emptyList())
                    .firstMessage(true)
                    .rawPrompt(true)
                    .build();

            AIResponse response = aiProvider.streamChat(dashboardRequest, chunk -> {
                if (chunkHandler != null) {
                    chunkHandler.onChunk(chunk);
                }
            });

            dashboardConfig = parseDashboardConfig(response.getContent(), analysisResult);

        } catch (Exception e) {
            log.error("AI dashboard-config failed, using fallback: {}", e.getMessage(), e);
            // Fallback: build a minimal config from the analysis result
            dashboardConfig = buildFallbackConfig(analysisResult);
        }

        // ── Step 2: build full HTML server-side ───────────────────────────
        String realDataJson = serializeDataForHtml(successfulResults);
        String html = buildDashboardHtml(analysisResult.getTitle(),
                analysisResult.getAnalysis(),
                dashboardConfig,
                realDataJson);

        // ── Step 3: assign a unique dashboard ID ──────────────────────────
        // The HTML is stored in the Message.content JSON by the caller (orchestrator).
        // DashboardController will look it up from the DB using this ID.
        String dashboardId = UUID.randomUUID().toString();

        return DashboardResult.builder()
                .dashboardId(dashboardId)
                .html(html)
                .build();
    }

    // ─── Internal helpers ────────────────────────────────────────────────

    private AnalysisResult parseAnalysisResponse(String aiContent) {
        if (aiContent == null || aiContent.isBlank()) {
            return AnalysisResult.builder()
                    .analysis("No analysis content received.")
                    .title("Analysis")
                    .keyMetricsJson("[]")
                    .chartSuggestionsJson("[]")
                    .build();
        }

        try {
            String cleaned = extractJson(aiContent);
            JsonNode root = objectMapper.readTree(cleaned);

            String analysis = root.path("analysis").asText("No analysis available.");
            String title = root.path("title").asText("Data Analysis");
            String keyMetrics = root.has("keyMetrics")
                    ? objectMapper.writeValueAsString(root.get("keyMetrics"))
                    : "[]";
            String chartSuggestions = root.has("chartSuggestions")
                    ? objectMapper.writeValueAsString(root.get("chartSuggestions"))
                    : "[]";

            return AnalysisResult.builder()
                    .analysis(analysis)
                    .title(title)
                    .keyMetricsJson(keyMetrics)
                    .chartSuggestionsJson(chartSuggestions)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse AI analysis as JSON, using raw content: {}", e.getMessage());
            return AnalysisResult.builder()
                    .analysis(aiContent)
                    .title("Data Analysis")
                    .keyMetricsJson("[]")
                    .chartSuggestionsJson("[]")
                    .build();
        }
    }

    /**
     * Parse the AI's chart-config JSON response.
     * Falls back to a minimal config derived from the analysis result if parsing fails.
     */
    private DashboardConfig parseDashboardConfig(String aiContent, AnalysisResult analysisResult) {
        if (aiContent == null || aiContent.isBlank()) {
            return buildFallbackConfig(analysisResult);
        }
        try {
            String cleaned = extractJson(aiContent);
            JsonNode root = objectMapper.readTree(cleaned);

            String metricsJson = root.has("metrics")
                    ? objectMapper.writeValueAsString(root.get("metrics"))
                    : analysisResult.getKeyMetricsJson();
            String chartsJson = root.has("charts")
                    ? objectMapper.writeValueAsString(root.get("charts"))
                    : "[]";
            String theme = root.path("theme").asText("blue");

            return new DashboardConfig(metricsJson, chartsJson, theme);
        } catch (Exception e) {
            log.warn("Failed to parse dashboard config JSON: {}", e.getMessage());
            return buildFallbackConfig(analysisResult);
        }
    }

    private DashboardConfig buildFallbackConfig(AnalysisResult analysisResult) {
        return new DashboardConfig(analysisResult.getKeyMetricsJson(), "[]", "blue");
    }

    /**
     * Strip markdown fences and extract the first complete JSON object from AI output.
     */
    private String extractJson(String raw) {
        String s = raw.trim();
        // Strip ```json … ``` or ``` … ```
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline > 0) s = s.substring(firstNewline + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
            s = s.trim();
        }
        // Find the first { … } block in case there is leading text
        if (!s.startsWith("{")) {
            int start = s.indexOf('{');
            int end   = s.lastIndexOf('}');
            if (start >= 0 && end > start) s = s.substring(start, end + 1);
        }
        return s;
    }

    // ─── Server-side HTML skeleton builder ──────────────────────────────

    /**
     * Build a complete, self-contained dashboard HTML page.
     *
     * <p>Layout:
     * <ol>
     *   <li>Header — title, timestamp, Download CSV button</li>
     *   <li>Metric cards — "Total Records" always computed from real data first,
     *       then AI-supplied metrics (so AI can never show a wrong total)</li>
     *   <li>Charts — one card per AI chart config; labels auto-truncated,
     *       dates formatted, top-15 limit so axes are always readable</li>
     *   <li>Analysis — AI markdown rendered to HTML</li>
     *   <li>Data table — every column, every row, sticky header, search filter</li>
     * </ol>
     */
    private String buildDashboardHtml(String title, String analysis,
                                      DashboardConfig config, String realDataJson) {
        String safeTitle = escapeHtml(title != null ? title : "Dashboard");
        String analysisHtml = markdownToHtml(analysis);

        // theme → [primary, primaryLight, headerBg, bodyBg, primaryMid]
        String theme = config.theme() != null ? config.theme().toLowerCase() : "blue";
        String[] colors = switch (theme) {
            case "green"  -> new String[]{"#16a34a","#bbf7d0","#14532d","#f0fdf4","#86efac"};
            case "purple" -> new String[]{"#7c3aed","#ede9fe","#4c1d95","#faf5ff","#c4b5fd"};
            case "orange" -> new String[]{"#ea580c","#ffedd5","#7c2d12","#fff7ed","#fdba74"};
            default       -> new String[]{"#2563eb","#dbeafe","#1e3a8a","#f1f5f9","#93c5fd"};
        };
        String primary     = colors[0];
        String primaryLight = colors[1];
        String headerBg    = colors[2];
        String bodyBg      = colors[3];

        // 12-colour palette for charts
        String chartPalette = "'#2563eb','#16a34a','#f59e0b','#ef4444','#8b5cf6',"
                            + "'#06b6d4','#ec4899','#14b8a6','#f97316','#6366f1',"
                            + "'#84cc16','#a855f7'";

        return "<!DOCTYPE html>\n"
            + "<html lang=\"en\">\n"
            + "<head>\n"
            + "<meta charset=\"UTF-8\">\n"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n"
            + "<title>" + safeTitle + "</title>\n"
            + "<script src=\"https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js\"></script>\n"
            + "<style>\n"
            + "*, *::before, *::after { box-sizing:border-box; margin:0; padding:0; }\n"
            + "body { background:" + bodyBg + "; font-family:system-ui,-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif; color:#1e293b; min-height:100vh; }\n"

            // ── header ────────────────────────────────────────────────────
            + ".hdr { background:" + headerBg + "; color:#fff; padding:16px 32px; display:flex; align-items:center; gap:14px; flex-wrap:wrap; box-shadow:0 2px 8px rgba(0,0,0,.18); }\n"
            + ".hdr-left { flex:1; min-width:0; }\n"
            + ".hdr h1 { font-size:1.35rem; font-weight:700; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }\n"
            + ".hdr-meta { font-size:.75rem; opacity:.72; margin-top:3px; }\n"
            + ".btn-csv { display:inline-flex; align-items:center; gap:6px; padding:9px 18px; background:#fff; color:" + headerBg + "; border:none; border-radius:8px; cursor:pointer; font-size:.82rem; font-weight:700; letter-spacing:.01em; transition:box-shadow .15s,opacity .15s; white-space:nowrap; }\n"
            + ".btn-csv:hover { opacity:.9; box-shadow:0 2px 8px rgba(0,0,0,.2); }\n"
            + ".btn-csv svg { width:15px; height:15px; pointer-events:none; }\n"

            // ── page layout ───────────────────────────────────────────────
            + ".page { padding:28px 32px; max-width:1440px; margin:0 auto; }\n"
            + ".section-label { font-size:.7rem; font-weight:700; letter-spacing:.08em; text-transform:uppercase; color:#94a3b8; margin-bottom:10px; }\n"

            // ── metric cards ──────────────────────────────────────────────
            + ".metrics { display:grid; grid-template-columns:repeat(auto-fill,minmax(160px,1fr)); gap:14px; margin-bottom:32px; }\n"
            + ".mc { background:#fff; border-radius:12px; padding:20px 16px 16px; text-align:center; box-shadow:0 1px 3px rgba(0,0,0,.07),0 1px 8px rgba(0,0,0,.04); border-top:3px solid " + primary + "; }\n"
            + ".mc .ic { font-size:1.6rem; line-height:1; margin-bottom:8px; }\n"
            // metric value: auto-shrink font for long values, no word-break
            + ".mc .val { font-size:1.55rem; font-weight:800; color:" + primary + "; line-height:1.15; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; max-width:100%; display:block; }\n"
            + ".mc .lbl { font-size:.72rem; color:#64748b; font-weight:600; margin-top:5px; text-transform:uppercase; letter-spacing:.04em; }\n"

            // ── charts grid ───────────────────────────────────────────────
            // IMPORTANT: canvas must NOT be position:absolute — Chart.js needs a fixed pixel height
            + ".charts-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(440px,1fr)); gap:20px; margin-bottom:32px; }\n"
            + ".cc { background:#fff; border-radius:12px; padding:20px; box-shadow:0 1px 3px rgba(0,0,0,.07),0 1px 8px rgba(0,0,0,.04); }\n"
            + ".cc h3 { font-size:.88rem; font-weight:700; color:#334155; margin-bottom:14px; padding-bottom:10px; border-bottom:2px solid #f1f5f9; text-transform:uppercase; letter-spacing:.04em; }\n"
            + ".cc canvas { display:block; width:100%!important; height:260px!important; }\n"

            // ── analysis card ─────────────────────────────────────────────
            + ".ac { background:#fff; border-radius:12px; padding:24px 28px; margin-bottom:32px; box-shadow:0 1px 3px rgba(0,0,0,.07),0 1px 8px rgba(0,0,0,.04); line-height:1.8; }\n"
            + ".ac h2 { font-size:1rem; font-weight:700; color:" + primary + "; margin-bottom:14px; padding-bottom:10px; border-bottom:2px solid " + primaryLight + "; display:flex; align-items:center; gap:8px; }\n"
            + ".ac h3 { font-size:.9rem; font-weight:700; color:#334155; margin:14px 0 6px; }\n"
            + ".ac ul { padding-left:22px; }\n"
            + ".ac li { margin-bottom:5px; color:#475569; }\n"
            + ".ac p  { margin-bottom:10px; color:#475569; }\n"
            + ".ac strong { color:#1e293b; }\n"

            // ── data table ────────────────────────────────────────────────
            + ".tc { background:#fff; border-radius:12px; padding:20px; box-shadow:0 1px 3px rgba(0,0,0,.07),0 1px 8px rgba(0,0,0,.04); }\n"
            + ".tc-hdr { display:flex; align-items:center; gap:12px; margin-bottom:12px; flex-wrap:wrap; }\n"
            + ".tc-hdr h2 { font-size:1rem; font-weight:700; color:" + primary + "; flex:1; }\n"
            + ".tc-meta { font-size:.78rem; color:#64748b; }\n"
            + ".tbl-search { padding:7px 12px; border:1px solid #e2e8f0; border-radius:7px; font-size:.82rem; width:220px; outline:none; }\n"
            + ".tbl-search:focus { border-color:" + primary + "; box-shadow:0 0 0 3px " + primaryLight + "; }\n"
            + ".tbl-wrap { overflow-x:auto; max-height:460px; overflow-y:auto; border-radius:8px; border:1px solid #f1f5f9; }\n"
            + "table { width:100%; border-collapse:collapse; font-size:.82rem; }\n"
            + "thead tr { background:" + primary + "; }\n"
            + "th { color:#fff; padding:10px 14px; text-align:left; font-weight:600; position:sticky; top:0; z-index:1; white-space:nowrap; }\n"
            + "td { padding:8px 14px; border-bottom:1px solid #f1f5f9; color:#334155; white-space:nowrap; max-width:260px; overflow:hidden; text-overflow:ellipsis; }\n"
            + "tbody tr:hover td { background:" + primaryLight + "; }\n"
            + "tbody tr:nth-child(even) td { background:#f8fafc; }\n"
            + "tbody tr:nth-child(even):hover td { background:" + primaryLight + "; }\n"
            + ".no-rows { text-align:center; padding:24px; color:#94a3b8; font-size:.85rem; }\n"

            // ── footer ────────────────────────────────────────────────────
            + ".footer { text-align:center; padding:20px; font-size:.72rem; color:#94a3b8; }\n"
            + ".footer a { color:" + primary + "; text-decoration:none; font-weight:600; }\n"
            + "</style>\n"
            + "</head>\n"
            + "<body>\n"

            // ── Embedded data (never goes to AI) ──────────────────────────
            + "<script>\n"
            + "const datasets      = " + realDataJson + ";\n"
            + "const metricsConfig = " + config.metricsJson() + ";\n"
            + "const chartsConfig  = " + config.chartsJson() + ";\n"
            + "const PALETTE       = [" + chartPalette + "];\n"
            + "</script>\n"

            // ── Header ────────────────────────────────────────────────────
            + "<div class=\"hdr\">\n"
            + "  <div class=\"hdr-left\">\n"
            + "    <h1>" + safeTitle + "</h1>\n"
            + "    <div class=\"hdr-meta\" id=\"ts\"></div>\n"
            + "  </div>\n"
            + "  <button class=\"btn-csv\" onclick=\"downloadCSV(0)\">\n"
            + "    <svg viewBox=\"0 0 20 20\" fill=\"currentColor\"><path d=\"M13 8V2H7v6H2l8 8 8-8h-5zM0 18h20v2H0v-2z\"/></svg>\n"
            + "    Download CSV\n"
            + "  </button>\n"
            + "</div>\n"

            // ── Page ──────────────────────────────────────────────────────
            + "<div class=\"page\">\n"

            // ── Metrics ───────────────────────────────────────────────────
            + "  <div class=\"section-label\">Key Metrics</div>\n"
            + "  <div class=\"metrics\" id=\"metrics\"></div>\n"

            // ── Charts ────────────────────────────────────────────────────
            + "  <div class=\"section-label\" id=\"chartsLabel\" style=\"display:none\">Charts</div>\n"
            + "  <div class=\"charts-grid\" id=\"chartsGrid\"></div>\n"

            // ── Analysis ──────────────────────────────────────────────────
            + "  <div class=\"ac\"><h2>📋 Analysis</h2>" + analysisHtml + "</div>\n"

            // ── Data table ────────────────────────────────────────────────
            + "  <div class=\"tc\">\n"
            + "    <div class=\"tc-hdr\">\n"
            + "      <h2>📄 Data</h2>\n"
            + "      <span class=\"tc-meta\" id=\"tableMeta\"></span>\n"
            + "      <input class=\"tbl-search\" id=\"tblSearch\" placeholder=\"🔍 Filter rows…\" oninput=\"filterTable(this.value)\">\n"
            + "    </div>\n"
            + "    <div class=\"tbl-wrap\">\n"
            + "      <table id=\"dataTable\"><thead id=\"tHead\"></thead><tbody id=\"tBody\"></tbody></table>\n"
            + "    </div>\n"
            + "  </div>\n"
            + "</div>\n"
            + "<div class=\"footer\">Powered by <a href=\"#\">DataNexus</a></div>\n"

            // ── Runtime JS ────────────────────────────────────────────────
            + "<script>\n"

            // timestamp
            + "document.getElementById('ts').textContent = 'Generated: ' + new Date().toLocaleString();\n\n"

            // ── helpers ─────────────────────────────────────────────────
            // truncate long label strings for chart display
            + "function trunc(s, n) { n = n || 18; s = String(s ?? ''); return s.length > n ? s.slice(0, n) + '…' : s; }\n\n"

            // format ISO date strings → short readable form
            + "function fmtLabel(s) {\n"
            + "  if (!s) return s;\n"
            + "  const str = String(s);\n"
            + "  // ISO datetime: 2026-02-17T15:04:29.566… → 17 Feb\n"
            + "  const iso = str.match(/^(\\d{4})-(\\d{2})-(\\d{2})T/);\n"
            + "  if (iso) { const d = new Date(str); return isNaN(d) ? trunc(str) : d.toLocaleDateString(undefined,{day:'2-digit',month:'short'}); }\n"
            + "  // plain date: 2026-02-17 → 17 Feb\n"
            + "  const dt = str.match(/^(\\d{4})-(\\d{2})-(\\d{2})$/);\n"
            + "  if (dt) { const d = new Date(str); return isNaN(d) ? str : d.toLocaleDateString(undefined,{day:'2-digit',month:'short'}); }\n"
            + "  return trunc(str);\n"
            + "}\n\n"

            // ── "Total Records" metric always from real data ──────────────
            + "(function renderMetrics() {\n"
            + "  const el = document.getElementById('metrics');\n"
            // always first: real row count from datasets
            + "  const totalRows = datasets.reduce((s, ds) => s + (ds.rows ? ds.rows.length : 0), 0);\n"
            + "  function card(icon, val, lbl) {\n"
            + "    const d = document.createElement('div'); d.className = 'mc';\n"
            + "    d.innerHTML = '<div class=\"ic\">' + icon + '</div><div class=\"val\">' + val + '</div><div class=\"lbl\">' + lbl + '</div>';\n"
            + "    el.appendChild(d);\n"
            + "  }\n"
            + "  card('📦', totalRows.toLocaleString(), 'Total Records');\n"
            // then AI-supplied metrics (skip any that look like a total-records duplicate)
            + "  const list = Array.isArray(metricsConfig) ? metricsConfig : [];\n"
            + "  list.forEach(m => {\n"
            + "    const lbl = (m.label || '').toLowerCase();\n"
            + "    if (lbl.includes('total record') || lbl.includes('row count') || lbl.includes('total row')) return;\n"
            + "    card(m.icon || '📊', m.value != null ? m.value : '-', m.label || '');\n"
            + "  });\n"
            // after rendering, shrink font for cards whose value text is long
            + "  document.querySelectorAll('.mc .val').forEach(el => {\n"
            + "    const len = el.textContent.length;\n"
            + "    if (len > 10) el.style.fontSize = '1.1rem';\n"
            + "    else if (len > 7) el.style.fontSize = '1.3rem';\n"
            + "    el.title = el.textContent;\n"
            + "  });\n"
            + "})();\n\n"

            // ── aggregate helper ─────────────────────────────────────────
            + "function aggregate(rows, groupField, valueField, aggType) {\n"
            + "  const map = new Map(), cnt = new Map();\n"
            + "  rows.forEach(r => {\n"
            + "    const key = String(r[groupField] ?? '(blank)');\n"
            + "    const num = parseFloat(r[valueField]); const v = isNaN(num) ? 0 : num;\n"
            + "    if (!map.has(key)) { map.set(key, v); cnt.set(key, 1); }\n"
            + "    else {\n"
            + "      if (aggType === 'sum' || aggType === 'count') map.set(key, map.get(key) + v);\n"
            + "      else if (aggType === 'avg') { map.set(key, map.get(key) + v); cnt.set(key, cnt.get(key) + 1); }\n"
            + "      else if (aggType === 'max') map.set(key, Math.max(map.get(key), v));\n"
            + "      else if (aggType === 'min') map.set(key, Math.min(map.get(key), v));\n"
            + "    }\n"
            + "    if (aggType === 'count') cnt.set(key, (cnt.get(key) || 0) + 1);\n"
            + "  });\n"
            + "  const entries = [...map.entries()].map(([k,v]) => {\n"
            + "    let val = aggType === 'avg' ? v / (cnt.get(k)||1) : aggType === 'count' ? cnt.get(k) : v;\n"
            + "    return [k, Math.round(val * 100) / 100];\n"
            + "  });\n"
            + "  entries.sort((a,b) => b[1]-a[1]);\n"
            + "  const top = entries.slice(0, 15);\n"
            + "  return { labels: top.map(e => e[0]), values: top.map(e => e[1]) };\n"
            + "}\n\n"

            // ── chart rendering ──────────────────────────────────────────
            + "(function renderCharts() {\n"
            + "  const grid = document.getElementById('chartsGrid');\n"
            + "  const label = document.getElementById('chartsLabel');\n"
            + "  const list = Array.isArray(chartsConfig) ? chartsConfig : [];\n"
            + "  let rendered = 0;\n"
            + "  list.forEach((cfg, idx) => {\n"
            + "    const ds = datasets[cfg.datasetIndex ?? 0];\n"
            + "    if (!ds || !ds.rows || ds.rows.length === 0) return;\n"
            + "    const rows = ds.rows;\n"
            + "    const type = (cfg.type || 'bar').toLowerCase();\n"
            + "    const isPie = type === 'pie' || type === 'doughnut';\n"
            + "    const isScatter = type === 'scatter';\n"
            + "    const agg = (cfg.aggregation || 'sum').toLowerCase();\n"
            + "\n"
            + "    const card = document.createElement('div'); card.className = 'cc';\n"
            + "    const hdr = document.createElement('h3'); hdr.textContent = cfg.title || ('Chart ' + (idx+1)); card.appendChild(hdr);\n"
            // canvas goes directly in .cc — fixed 260px height set in CSS, no wrapper needed
            + "    const canvas = document.createElement('canvas'); canvas.id = 'c'+idx; card.appendChild(canvas);\n"
            + "    grid.appendChild(card);\n"
            + "\n"
            + "    let chartData;\n"
            + "    if (isScatter) {\n"
            + "      const xF = cfg.xField, yF = cfg.yField; if (!xF||!yF) return;\n"
            + "      const pts = rows.map(r => ({ x: parseFloat(r[xF])||0, y: parseFloat(r[yF])||0 }));\n"
            + "      chartData = { datasets: [{ label: yF+' vs '+xF, data: pts, backgroundColor: PALETTE[idx%PALETTE.length]+'99' }] };\n"
            + "    } else if (isPie) {\n"
            + "      const lF = cfg.labelField || cfg.xField, vF = cfg.valueField || cfg.yField; if (!lF||!vF) return;\n"
            + "      const agged = aggregate(rows, lF, vF, agg);\n"
            + "      const labels = agged.labels.map(fmtLabel);\n"
            + "      chartData = { labels, datasets: [{ data: agged.values, backgroundColor: PALETTE.slice(0, labels.length), borderWidth: 2, borderColor: '#fff' }] };\n"
            + "    } else {\n"
            + "      const xF = cfg.xField, yF = cfg.yField; if (!xF||!yF) return;\n"
            + "      const agged = aggregate(rows, xF, yF, agg);\n"
            + "      const labels = agged.labels.map(fmtLabel);\n"
            + "      const colour = PALETTE[idx%PALETTE.length];\n"
            + "      chartData = { labels, datasets: [{ label: (agg==='count'?'Count by ':agg+' of ')+yF, data: agged.values,\n"
            + "        backgroundColor: type==='line' ? colour+'33' : colour+'cc',\n"
            + "        borderColor: colour, borderWidth: type==='line' ? 2 : 0,\n"
            + "        fill: type==='line', tension: 0.35, pointRadius: type==='line' ? 3 : 0,\n"
            + "        borderRadius: type==='bar' ? 4 : 0 }] };\n"
            + "    }\n"
            + "\n"
            + "    new Chart(canvas, { type,  data: chartData, options: {\n"
            + "      responsive: true, maintainAspectRatio: false,\n"
            + "      plugins: {\n"
            + "        legend: { display: isPie, position: 'right',\n"
            + "          labels: { boxWidth: 12, font: { size: 11 }, generateLabels: function(chart) {\n"
            + "            const orig = Chart.defaults.plugins.legend.labels.generateLabels(chart);\n"
            + "            orig.forEach(l => { if (l.text && l.text.length > 20) l.text = l.text.slice(0,20)+'…'; }); return orig;\n"
            + "          }}\n"
            + "        },\n"
            + "        tooltip: { mode: isPie ? 'point' : 'index', intersect: false }\n"
            + "      },\n"
            + "      scales: (isPie || isScatter) ? {} : {\n"
            + "        x: { ticks: { maxRotation: 40, maxTicksLimit: 12, font: { size: 11 },\n"
            + "          callback: function(val, i) { const lbl = this.getLabelForValue(val); return lbl && lbl.length > 14 ? lbl.slice(0,14)+'…' : lbl; }\n"
            + "        }, grid: { display: false } },\n"
            + "        y: { beginAtZero: true, ticks: { font: { size: 11 } }, grid: { color: '#f1f5f9' } }\n"
            + "      }\n"
            + "    }});\n"
            + "    rendered++;\n"
            + "  });\n"
            + "  if (rendered > 0) label.style.display = 'block';\n"
            + "})();\n\n"

            // ── all rows cache for search ─────────────────────────────────
            + "let _allRows = [];\n"
            + "let _cols = [];\n\n"

            // ── data table ───────────────────────────────────────────────
            + "(function renderTable() {\n"
            + "  if (!datasets.length || !datasets[0].columns) return;\n"
            + "  _cols = datasets[0].columns;\n"
            + "  _allRows = datasets[0].rows || [];\n"
            + "  document.getElementById('tableMeta').textContent =\n"
            + "    _allRows.length.toLocaleString() + ' row' + (_allRows.length !== 1 ? 's' : '') +\n"
            + "    ' \\u00b7 ' + _cols.length + ' col' + (_cols.length !== 1 ? 's' : '');\n"
            + "  // header\n"
            + "  const hr = document.createElement('tr');\n"
            + "  _cols.forEach(c => { const th = document.createElement('th'); th.textContent = c; hr.appendChild(th); });\n"
            + "  document.getElementById('tHead').appendChild(hr);\n"
            + "  renderRows(_allRows);\n"
            + "})();\n\n"

            + "function renderRows(rows) {\n"
            + "  const tbody = document.getElementById('tBody');\n"
            + "  tbody.innerHTML = '';\n"
            + "  if (!rows.length) { const tr = document.createElement('tr'); const td = document.createElement('td'); td.colSpan = _cols.length; td.className = 'no-rows'; td.textContent = 'No matching rows'; tr.appendChild(td); tbody.appendChild(tr); return; }\n"
            + "  rows.forEach(row => {\n"
            + "    const tr = document.createElement('tr');\n"
            + "    _cols.forEach(c => {\n"
            + "      const td = document.createElement('td');\n"
            + "      const v = row[c]; td.textContent = v == null ? '' : String(v);\n"
            + "      td.title = v == null ? '' : String(v);\n"
            + "      tr.appendChild(td);\n"
            + "    });\n"
            + "    tbody.appendChild(tr);\n"
            + "  });\n"
            + "}\n\n"

            // search/filter
            + "function filterTable(q) {\n"
            + "  if (!q) { renderRows(_allRows); return; }\n"
            + "  const lq = q.toLowerCase();\n"
            + "  renderRows(_allRows.filter(r => _cols.some(c => r[c] != null && String(r[c]).toLowerCase().includes(lq))));\n"
            + "}\n\n"

            // ── CSV export ───────────────────────────────────────────────
            + "function downloadCSV(dsIndex) {\n"
            + "  const ds = datasets[dsIndex || 0];\n"
            + "  if (!ds || !ds.columns) { alert('No data available'); return; }\n"
            + "  const cols = ds.columns;\n"
            + "  const rows = ds.rows || [];\n"
            + "  function q(v) {\n"
            + "    const s = v == null ? '' : String(v);\n"
            + "    return (s.includes(',') || s.includes('\"') || s.includes('\\n') || s.includes('\\r'))\n"
            + "      ? '\"' + s.replace(/\"/g, '\"\"') + '\"' : s;\n"
            + "  }\n"
            + "  const lines = [cols.map(q).join(',')];\n"
            + "  rows.forEach(r => lines.push(cols.map(c => q(r[c])).join(',')));\n"
            + "  const blob = new Blob(['\\uFEFF' + lines.join('\\r\\n')], { type: 'text/csv;charset=utf-8;' });\n"
            + "  const a = document.createElement('a');\n"
            + "  a.href = URL.createObjectURL(blob);\n"
            + "  a.download = '" + safeTitle.replace("'", "\\'") + ".csv';\n"
            + "  document.body.appendChild(a); a.click(); a.remove();\n"
            + "  URL.revokeObjectURL(a.href);\n"
            + "}\n"
            + "</script>\n"
            + "</body>\n"
            + "</html>";
    }

    /**
     * Markdown → HTML conversion for analysis text.
     * Handles: # ## ### headings, **bold**, *italic*, - * bullet lists, numbered lists, blank lines.
     * All branches go through inlineMd so bold/italic always renders.
     */
    private String markdownToHtml(String md) {
        if (md == null || md.isBlank()) return "<p>No analysis available.</p>";
        StringBuilder html = new StringBuilder();
        boolean inUl = false;
        boolean inOl = false;
        for (String line : md.split("\n")) {
            String t = line.trim();

            // close open lists when a non-list line is encountered
            boolean isBullet   = t.startsWith("- ") || t.startsWith("* ") || t.startsWith("• ");
            boolean isNumbered = t.matches("^\\d+\\.\\s.*");

            if (!isBullet && inUl)  { html.append("</ul>\n"); inUl = false; }
            if (!isNumbered && inOl){ html.append("</ol>\n"); inOl = false; }

            if (t.startsWith("### ")) {
                html.append("<h3>").append(inlineMd(t.substring(4))).append("</h3>\n");
            } else if (t.startsWith("## ")) {
                html.append("<h3>").append(inlineMd(t.substring(3))).append("</h3>\n");
            } else if (t.startsWith("# ")) {
                html.append("<h3>").append(inlineMd(t.substring(2))).append("</h3>\n");
            } else if (isBullet) {
                if (!inUl) { html.append("<ul>\n"); inUl = true; }
                // strip leading bullet char + space
                String content = t.replaceFirst("^[-*•]\\s+", "");
                html.append("<li>").append(inlineMd(content)).append("</li>\n");
            } else if (isNumbered) {
                if (!inOl) { html.append("<ol>\n"); inOl = true; }
                String content = t.replaceFirst("^\\d+\\.\\s+", "");
                html.append("<li>").append(inlineMd(content)).append("</li>\n");
            } else if (t.isEmpty()) {
                // blank line = paragraph break (skip if already just added one)
            } else {
                html.append("<p>").append(inlineMd(t)).append("</p>\n");
            }
        }
        if (inUl) html.append("</ul>\n");
        if (inOl) html.append("</ol>\n");
        return html.toString();
    }

    /**
     * Handle inline markdown: **bold**, *italic*, `code`.
     * Strips any trailing stray asterisks the AI leaves behind.
     * Escapes HTML before applying patterns so we don't double-escape.
     */
    private String inlineMd(String text) {
        if (text == null) return "";
        // Remove stray trailing/leading asterisks not part of a pair
        String s = escapeHtml(text.trim());
        // **bold** — greedy first pass handles nested cases like **foo **bar**
        s = s.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        // *italic* — only single asterisk remaining after bold pass
        s = s.replaceAll("(?<!\\*|<strong>)\\*(?!\\*)(.+?)(?<!\\*|</strong>)\\*(?!\\*)", "<em>$1</em>");
        // `code`
        s = s.replaceAll("`(.+?)`", "<code style=\"background:#f1f5f9;padding:1px 5px;border-radius:4px;font-size:.85em\">$1</code>");
        // Strip any remaining lone asterisks (AI hallucination artifacts)
        s = s.replaceAll("(?<![*])\\*(?![*])", "");
        return s;
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ─── Inner DTOs ──────────────────────────────────────────────────────

    /** Holds the AI-supplied chart and metric configuration for dashboard generation. */
    private record DashboardConfig(String metricsJson, String chartsJson, String theme) {}

    // ─── Result DTOs ─────────────────────────────────────────────────────

    @Getter
    @Builder
    public static class AnalysisResult {
        private String analysis;
        private String title;
        private String keyMetricsJson;
        private String chartSuggestionsJson;
    }

    @Getter
    @Builder
    public static class DashboardResult {
        private String dashboardId;
        private String html;
    }
}
