package com.datanexus.datanexus.service.ai;

import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
@Slf4j
public class QuerySafetyValidator {

    private static final List<Pattern> FORBIDDEN_PATTERNS = List.of(
            Pattern.compile("\\bINSERT\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bUPDATE\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bDELETE\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bDROP\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bALTER\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bCREATE\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bTRUNCATE\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bGRANT\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bREVOKE\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bEXEC(UTE)?\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bCALL\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bMERGE\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bREPLACE\\b", Pattern.CASE_INSENSITIVE)
    );

    public ValidationResult validate(String sql) {
        if (sql == null || sql.isBlank()) {
            return ValidationResult.invalid("Query cannot be empty");
        }

        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }

        for (Pattern pattern : FORBIDDEN_PATTERNS) {
            if (pattern.matcher(trimmed).find()) {
                // Allow UPDATE/DELETE inside subqueries that are part of a SELECT, but
                // as a safety measure we reject any occurrence outside of string literals
                String keyword = pattern.pattern().replaceAll("\\\\b", "").replaceAll("\\(.*?\\)", "");
                return ValidationResult.invalid(
                        "Forbidden SQL operation detected: " + keyword +
                        ". Only SELECT queries are allowed for security reasons.");
            }
        }

        try {
            Statement statement = CCJSqlParserUtil.parse(trimmed);
            if (!(statement instanceof Select)) {
                return ValidationResult.invalid(
                        "Only SELECT statements are allowed. Received: " +
                        statement.getClass().getSimpleName());
            }
        } catch (Exception e) {
            log.warn("SQL parsing failed for validation, falling back to pattern check: {}", e.getMessage());
            if (!trimmed.toUpperCase().trim().startsWith("SELECT") &&
                !trimmed.toUpperCase().trim().startsWith("WITH")) {
                return ValidationResult.invalid("Query must start with SELECT or WITH (CTE)");
            }
        }

        return ValidationResult.valid();
    }

    public record ValidationResult(boolean valid, String reason) {
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason);
        }
    }
}
