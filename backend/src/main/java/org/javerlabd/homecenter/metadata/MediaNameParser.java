package org.javerlabd.homecenter.metadata;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.javerlabd.homecenter.media.MediaClassifier;
import org.springframework.stereotype.Component;

/** Recognizes common S01E02, 1x02, and CD2/Part 2 patterns and release years. */
@Component
public class MediaNameParser {

    private static final Pattern SEASON_EPISODE = Pattern.compile(
            "(?i)^(.*?)(?:[ ._\\-]+)?s(\\d{1,2})e(\\d{1,3})(?:e\\d{1,3})?.*$");
    private static final Pattern X_EPISODE = Pattern.compile(
            "(?i)^(.*?)(?:[ ._\\-]+)(\\d{1,2})x(\\d{1,3}).*$");
    private static final Pattern PART = Pattern.compile(
            "(?i)^(.*?)(?:[ ._\\-]+)(?:cd|disc|disk|part|pt)[ ._\\-]*(\\d{1,2})(?:\\D.*)?$");
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})(?!\\d)");
    private static final Pattern SEASON_DIRECTORY = Pattern.compile(
            "(?i)^(?:season|series|s[eé]ria|s)\\s*\\d{1,2}$");
    private static final Pattern NOISE_FROM = Pattern.compile(
            "(?i)\\b(?:2160p|1080p|720p|576p|480p|uhd|bluray|brrip|webrip|web[ ._-]?dl|"
                    + "hdtv|dvdrip|x26[45]|h[ ._-]?26[45]|hevc|av1|aac|dts|remux)\\b.*$");

    public ParsedVideoName parse(String relativePath, String fileName) {
        String base = withoutExtension(fileName);
        Integer year = yearOf(base);
        Integer season = null;
        Integer episode = null;
        String titleCandidate = base;

        Matcher episodeMatcher = SEASON_EPISODE.matcher(base);
        if (!episodeMatcher.matches()) {
            episodeMatcher = X_EPISODE.matcher(base);
        }
        if (episodeMatcher.matches()) {
            titleCandidate = episodeMatcher.group(1);
            season = Integer.valueOf(episodeMatcher.group(2));
            episode = Integer.valueOf(episodeMatcher.group(3));
            if (clean(titleCandidate).isBlank()) {
                titleCandidate = seriesDirectory(relativePath);
            }
        }

        Integer part = null;
        Matcher partMatcher = PART.matcher(base);
        if (partMatcher.matches()) {
            part = Integer.valueOf(partMatcher.group(2));
            if (season == null) {
                titleCandidate = partMatcher.group(1);
            }
        }

        Matcher yearMatcher = YEAR.matcher(titleCandidate);
        if (yearMatcher.find()) {
            String beforeYear = titleCandidate.substring(0, yearMatcher.start());
            if (!clean(beforeYear).isBlank()) {
                titleCandidate = beforeYear;
            }
        }

        String queryTitle = clean(titleCandidate);
        if (queryTitle.isBlank()) {
            queryTitle = MediaClassifier.titleOf(fileName);
        }
        return new ParsedVideoName(queryTitle, year, season, episode, part);
    }

    private static Integer yearOf(String value) {
        Matcher matcher = YEAR.matcher(value);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private static String seriesDirectory(String relativePath) {
        String normalized = relativePath.replace('\\', '/');
        String[] parts = normalized.split("/");
        for (int i = parts.length - 2; i >= 0; i--) {
            String candidate = clean(parts[i]);
            if (!candidate.isBlank() && !SEASON_DIRECTORY.matcher(candidate).matches()) {
                return candidate;
            }
        }
        return "";
    }

    private static String withoutExtension(String fileName) {
        String extension = MediaClassifier.extensionOf(fileName);
        return extension.isEmpty() ? fileName : fileName.substring(0, fileName.length() - extension.length() - 1);
    }

    private static String clean(String value) {
        String withoutNoise = NOISE_FROM.matcher(value).replaceFirst("");
        return withoutNoise
                .replaceAll("[\\[\\](){}]", " ")
                .replace('_', ' ')
                .replace('.', ' ')
                .replaceAll("\\s*-\\s*", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
