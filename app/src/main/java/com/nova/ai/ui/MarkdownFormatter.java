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
                codeBuffer.append(expandTabs(line)).append("\n");
                continue;
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

    private static String expandTabs(String line) {
        if (line.indexOf('\t') < 0) return line;
        StringBuilder sb = new StringBuilder(line.length() + 8);
        int col = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\t') {
                int spaces = 4 - (col % 4);
                for (int s = 0; s < spaces; s++) sb.append(' ');
                col += spaces;
            } else {
                sb.append(c);
                col++;
            }
        }
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
