package co.paralleluniverse.fibers.instrument;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A wrapper around {@link Pattern} that defines equality and comparability
 * based on that {@link Pattern}'s underlying regular expression.
 */
final class QuasarPattern implements Comparable<QuasarPattern> {
    private final Pattern pattern;

    static QuasarPattern compile(String regex) {
        return new QuasarPattern(Pattern.compile(regex));
    }

    private QuasarPattern(Pattern p) {
        pattern = p;
    }

    Matcher matcher(String input) {
        return pattern.matcher(input);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof QuasarPattern)) {
            return false;
        }
        final QuasarPattern other = (QuasarPattern) obj;
        return pattern.pattern().equals(other.pattern.pattern());
    }

    @Override
    public int hashCode() {
        return pattern.pattern().hashCode();
    }

    @Override
    public String toString() {
        return pattern.toString();
    }

    @Override
    public int compareTo(QuasarPattern other) {
        return pattern.pattern().compareTo(other.pattern.pattern());
    }
}
