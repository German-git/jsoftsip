package com.jsoftsip.ui;

import atlantafx.base.theme.*;

public enum ThemeType {

    PRIMER_LIGHT("Primer Light", new PrimerLight()),

    PRIMER_DARK("Primer Dark", new PrimerDark()),

    NORD_LIGHT("Nord Light", new NordLight()),

    NORD_DARK("Nord Dark", new NordDark()),

    CUPERTINO_LIGHT("Cupertino Light", new CupertinoLight()),

    CUPERTINO_DARK("Cupertino Dark", new CupertinoDark()),

    DRACULA("Dracula", new Dracula());

    private final String displayName;

    private final Theme theme;

    ThemeType(String displayName, Theme theme) {
        this.displayName = displayName;
        this.theme = theme;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Theme getTheme() {
        return theme;
    }

    @Override
    public String toString() {
        return displayName;
    }
}