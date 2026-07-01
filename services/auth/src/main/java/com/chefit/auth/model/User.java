package com.chefit.auth.model;

import java.util.List;

// A user account. `identifier` is the single string the user signs in with — either a
// username they chose or an email. `identifierIsEmail` is a shape hint, set at registration,
// that downstream code (Google merge, future email-verification flow) consults instead of
// re-parsing the string each time. hashedPassword and googleSub are mutually optional:
// every account has at least one auth method, possibly both after a merge.
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
