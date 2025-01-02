package io.github.springstudent.dekstop.client.utils;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public final class ImageUtilities {
    private ImageUtilities() {
    }

    private static final Map<String, ImageIcon> ICON_CACHE = new HashMap<>();

    public static ImageIcon getOrCreateIcon(String name) {
        final String rname = "/images/" + name;
        ImageIcon icon = ICON_CACHE.get(name);
        if (icon == null) {
            try {
                icon = new ImageIcon(ImageUtilities.class.getResource(rname));
            } catch (NullPointerException ex) {
                throw new IllegalStateException(String.format("Missing icon [%s].", rname));
            }
            ICON_CACHE.put(name, icon);
        }
        return icon;
    }

}
