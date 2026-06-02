package com.lawai.auth.model;

import java.util.List;

public record AuthStorePayload(
    List<UserRecord> users,
    List<SessionRecord> sessions,
    List<PasswordResetRecord> passwordResets
) {
}
