package com.chefit.users.dto;

import com.chefit.users.model.User;

import java.util.List;

// Redacted public view of a User. Omits hashedPassword, ip, googleSub, and modifyDate.
// The omissions are part of the security contract — tests assert each is absent from the JSON.
public record PublicUser(
    String uuid,
    String identifier,
    boolean identifierIsEmail,
    List<String> providers,
    String realname,
    String createDate
) {
    public static PublicUser from(User user) {
        return new PublicUser(
                user.uuid(),
                user.identifier(),
                user.identifierIsEmail(),
                user.providers(),
                user.realname(),
                user.createDate());
    }
}
