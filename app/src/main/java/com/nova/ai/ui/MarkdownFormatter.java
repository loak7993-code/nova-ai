package com.nova.ai.ui;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.BulletSpan;
import android.text.style.StyleSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.TypefaceSpan;
import android.text.style.ForegroundColorSpan;

public class MarkdownFormatter {

    public static CharSequence format(String src) {
        if (src == null) return "";
        SpannableStringBuilder out = new SpannableStringBuilder();
        String[] lines = src.split("\n", -1);
        boolean inCodeBlock = false;
        StringBuilder codeBuffer = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (line.trim().startsWith("```")) {
                if (inCodeBlock) {
                    int start = out.length();
                    out.append(codeBuffer.toString().replaceAll("\n$", ""));
                    out.setSpan(new TypefaceSpan("monospace"), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new BackgroundColorSpan(0xFF131316), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new ForegroundColorSpan(0xFFECECEC), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    codeBuffer.setLength(0);
                    inCodeBlock = false;
                    out.append("\n");
                } else {
                    inCodeBlock = true;
                }
                continue;
            }

            if (inCodeBlock) {
                codeBuffer.append(line).append("\n");
                continue;
            }

            if (isTableSeparatorLine(lines, i)) {
                java.util.List<String[]> tableRows = new java.util.ArrayList<>();
                int headerIdx = i - 1;
                int bodyIdx = i + 1;
                while (headerIdx >= 0 && lines[headerIdx].trim().isEmpty()) headerIdx--;
                if (headerIdx >= 0 && isTableRow(lines[headerIdx])) {
                    tableRows.add(parseRow(lines[headerIdx]));
                    while (bodyIdx < lines.length && isTableRow(lines[bodyIdx])) {
                        tableRows.add(parseRow(lines[bodyIdx]));
                        bodyIdx++;
                    }
                    if (tableRows.size() >= 2) {
                        appendTable(out, tableRows);
                        i = bodyIdx - 1;
                        continue;
                    }
                }
            }

            if (line.startsWith("# ")) { appendHeading(out, line.substring(2), 1.6f, true); continue; }
            if (line.startsWith("## ")) { appendHeading(out, line.substring(3), 1.35f, true); continue; }
            if (line.startsWith("### ")) { appendHeading(out, line.substring(4), 1.15f, true); continue; }

            if (line.matches("^\\s*[-*]\\s.*")) {
                String content = line.replaceFirst("^\\s*[-*]\\s", "");
                int start = out.length();
                out.append("• ").append(content).append("\n");
                out.setSpan(new BulletSpan(24, 0xFF9AA4B2, 8), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                applyInline(out, start);
                continue;
            }

            if (line.matches("^\\s*\\d+\\.\\s.*")) {
                String content = line.replaceFirst("^\\s*(\\d+\\.)\\s", "");
                int start = out.length();
                out.append(content).append("\n");
                applyInline(out, start);
                continue;
            }

            int start = out.length();
            out.append(line);
            if (i < lines.length - 1) out.append("\n");
            applyInline(out, start);
        }

        if (inCodeBlock && codeBuffer.length() > 0) {
            int start = out.length();
            out.append(codeBuffer.toString().replaceAll("\n$", ""));
            out.setSpan(new TypefaceSpan("monospace"), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new BackgroundColorSpan(0xFF131316), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return out;
    }

    private static boolean isTableSeparatorLine(String[] lines, int i) {
        if (i <= 0 || i >= lines.length) return false;
        String line = lines[i].trim();
        if (!line.contains("|") || !line.contains("-")) return false;
        String stripped = line.replaceAll("[|: \\-]", "");
        return stripped.isEmpty();
    }

    private static boolean isTableRow(String line) {
        return line != null && line.trim().startsWith("|");
    }

    private static String[] parseRow(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("|")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("|")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        String[] parts = trimmed.split("\\|", -1);
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        return parts;
    }

    private static void appendTable(SpannableStringBuilder out, java.util.List<String[]> rows) {
        int cols = 0;
        for (String[] r : rows) cols = Math.max(cols, r.length);

        int[] widths = new int[cols];
        for (String[] r : rows) {
            for (int c = 0; c < r.length && c < cols; c++) {
                widths[c] = Math.max(widths[c], r[c].length());
            }
        }

        int start = out.length();
        if (start > 0 && out.charAt(start - 1) != '\n') out.append("\n");

        start = out.length();
        String[] header = rows.get(0);
        out.append(formatRow(header, widths)).append("\n");
        out.append(formatSeparator(widths)).append("\n");
        for (int r = 1; r < rows.size(); r++) {
            out.append(formatRow(rows.get(r), widths));
            if (r < rows.size() - 1) out.append("\n");
        }

        out.setSpan(new TypefaceSpan("monospace"), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new BackgroundColorSpan(0xFF1A1715), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new ForegroundColorSpan(0xFFECECEC), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append("\n");
    }

    private static String formatRow(String[] cells, int[] widths) {
        StringBuilder sb = new StringBuilder();
        sb.append(" ");
        for (int c = 0; c < widths.length; c++) {
            String cell = c < cells.length ? cells[c] : "";
            sb.append(padRight(cell, widths[c]));
            if (c < widths.length - 1) sb.append(" │ ");
        }
        sb.append(" ");
        return sb.toString();
    }

    private static String formatSeparator(int[] widths) {
        StringBuilder sb = new StringBuilder();
        sb.append(" ");
        for (int c = 0; c < widths.length; c++) {
            for (int w = 0; w < widths[c]; w++) sb.append("─");
            if (c < widths.length - 1) sb.append("─┼─");
        }
        sb.append(" ");
        return sb.toString();
    }

    private static String padRight(String s, int width) {
        if (s.length() >= width) return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) sb.append(" ");
        return sb.toString();
    }

    private static void appendHeading(SpannableStringBuilder out, String text, float size, boolean bold) {
        int start = out.length();
        out.append(text).append("\n");
        out.setSpan(new RelativeSizeSpan(size), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (bold) out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        applyInline(out, start);
    }

    private static void applyInline(SpannableStringBuilder out, int base) {
        applyMarker(out, base, "**", new StyleSpan(Typeface.BOLD));
        applyMarker(out, base, "__", new StyleSpan(Typeface.BOLD));
        applyMarker(out, base, "*", new StyleSpan(Typeface.ITALIC));
        applyInlineCode(out, base);
    }

    private static void applyMarker(SpannableStringBuilder out, int base, String marker, Object style) {
        int mLen = marker.length();
        int search = base;
        while (true) {
            int s = out.toString().indexOf(marker, search);
            if (s < 0) break;
            int e = out.toString().indexOf(marker, s + mLen);
            if (e < 0) break;
            out.delete(e, e + mLen);
            out.setSpan(style, s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.delete(s, s + mLen);
            search = s;
        }
    }

    private static void applyInlineCode(SpannableStringBuilder out, int base) {
        int search = base;
        while (true) {
            int s = out.toString().indexOf("`", search);
            if (s < 0) break;
            int e = out.toString().indexOf("`", s + 1);
            if (e < 0) break;
            out.delete(e, e + 1);
            out.setSpan(new TypefaceSpan("monospace"), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new BackgroundColorSpan(0xFF131316), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new ForegroundColorSpan(0xFF9DB8FF), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.delete(s, s + 1);
            search = s;
        }
    }
}
