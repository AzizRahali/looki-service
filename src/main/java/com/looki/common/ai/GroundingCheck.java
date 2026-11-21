package com.looki.common.ai;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks that AI-written text only mentions numbers, email addresses and links
 * that appear in the source texts it was given. Catches the most harmful kind of
 * made-up detail (wrong prices, deadlines, percentages, contacts).
 */
public final class GroundingCheck {

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");
    private static final Pattern LIST_NUMBER = Pattern.compile("(?m)^\\s*\\d{1,2}[.)](?=\\s)");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");
    private static final Pattern LINK = Pattern.compile("(?:https?://|www\\.)\\S+", Pattern.CASE_INSENSITIVE);

    private GroundingCheck() {}

    public static boolean isGrounded(String text, List<String> sources) {
        String normalized = normalizeDigits(text);
        String sourceText = normalizeDigits(String.join("\n", sources));
        String sourceLower = sourceText.toLowerCase(Locale.ROOT);

        Matcher emails = EMAIL.matcher(normalized);
        while (emails.find()) {
            if (!sourceLower.contains(emails.group().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        Matcher links = LINK.matcher(normalized);
        while (links.find()) {
            if (!sourceLower.contains(links.group().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        Set<Double> allowed = numbersIn(sourceText);
        // Digits inside email addresses were checked above; "1." list numbering is not a fact.
        String withoutEmails = EMAIL.matcher(normalized).replaceAll(" ");
        Matcher numbers = NUMBER.matcher(LIST_NUMBER.matcher(withoutEmails).replaceAll(" "));
        while (numbers.find()) {
            if (candidates(numbers.group()).stream().noneMatch(allowed::contains)) {
                return false;
            }
        }
        return true;
    }

    private static Set<Double> numbersIn(String text) {
        Set<Double> numbers = new HashSet<>();
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            numbers.addAll(candidates(matcher.group()));
        }
        return numbers;
    }

    /** A token like "1,5" may be a decimal or use a thousands separator; accept either reading. */
    private static List<Double> candidates(String token) {
        String digitsOnly = token.replaceAll("[.,]", "");
        String decimal = token.replace(',', '.');
        return List.of(Double.parseDouble(decimal), Double.parseDouble(digitsOnly));
    }

    /** Converts Arabic-Indic and Persian digits to Western digits. */
    static String normalizeDigits(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c >= '\u0660' && c <= '\u0669') {
                sb.append((char) ('0' + c - '\u0660'));
            } else if (c >= '\u06F0' && c <= '\u06F9') {
                sb.append((char) ('0' + c - '\u06F0'));
            } else if (c == '\u066B') {
                sb.append('.');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
