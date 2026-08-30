package com.jsoftsip.nativebridge.baresip;

/**
 * One config change to apply to a baresip config: set key to
 * value. The comment is written as a "# ..." line directly above
 * the config line when the key has to be appended, and is ignored
 * when an existing line is replaced. A null comment appends the
 * config line without any comment.
 */
public record ConfigPatch(String key, String value, String comment) {

    public ConfigPatch {

        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("patch key must not be blank");
        }

        if (value == null) {
            throw new IllegalArgumentException("patch value must not be null");
        }
    }
}
