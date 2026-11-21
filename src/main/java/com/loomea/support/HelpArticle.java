package com.loomea.support;

import java.util.Locale;

/** A help article the support assistant may answer from. */
public record HelpArticle(String id, Audience audience, String title, String body) {

    public enum Audience {
        CLIENT, OWNER, ALL;

        static Audience parse(String value) {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        }

        boolean includes(boolean owner) {
            return this == ALL || (owner ? this == OWNER : this == CLIENT);
        }
    }
}
