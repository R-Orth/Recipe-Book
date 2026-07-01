package com.chefit.users.model;

import java.util.List;

public record User(
    String uuid,
    String identifier,
    boolean identifierIsEmail,
    String hashedPassword,
    String googleSub,
    List<String> providers,
    String realname,
    String ip,
    String createDate,
    String modifyDate
) {
    public static boolean looksLikeEmail(String identifier) {
        if (identifier == null) return false;
        int at = identifier.indexOf('@');
        return at > 0 && at < identifier.length() - 1 && identifier.indexOf('@', at + 1) < 0;
    }
}
