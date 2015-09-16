/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.ui;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class JBUI {
  /**
   * List of supported scale factors for hidpi displays. The array must be sorted.
   */
  private static final float[] supportedScaleFactors = {1.0f, 1.25f, 1.33f, 1.4f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f};
  /**
   * Default DPI value in case we can't retrieve it from various system settings.
   */
  private static final int DEFAULT_DPI = 96;
  /**
   * Scale factor to apply to the size of UI elements.
   */
  private static float SCALE_FACTOR = calculateScaleFactor();

  private static float calculateScaleFactor() {
    if (SystemInfo.isMac) {
      return 1.0f;
    }

    if (SystemProperties.has("hidpi.system.dpi.override")) {
      int dpi = SystemProperties.getIntProperty("hidpi.system.dpi.override", DEFAULT_DPI);
      return dpiToSupportedScaleFactor(dpi);
    }

    if (SystemProperties.is("hidpi")) {
      return 2.0f;
    }

    if (SystemProperties.has("hidpi") && !SystemProperties.is("hidpi")) {
      return 1.0f;
    }

    // rpaquay: We use getSystemDPI for Windows too, as using the desktop property below
    // does not seem to work on Windows: For 192dpi, we get 16, which leads to a scaling
    // factor of 1.25
    if (SystemInfo.isLinux || SystemInfo.isWindows) {
      final int dpi = getSystemDPI();
      return dpiToSupportedScaleFactor(dpi);
    }

    int size = -1;
    try {
      if (SystemInfo.isWindows) {
        size = (Integer)Toolkit.getDefaultToolkit().getDesktopProperty("win.system.font.height");
      }
    } catch (Exception e) {//
    }
    if (size == -1) {
      size = Fonts.label().getSize();
    }
    if (size <= 13) return 1.0f;
    if (size <= 16) return 1.25f;
    if (size <= 18) return 1.5f;
    if (size < 24)  return 1.75f;

    return 2.0f;
  }

  private static float dpiToSupportedScaleFactor(int dpi) {
    float previousScaleFactor = supportedScaleFactors[0];
    for (float scaleFactor : supportedScaleFactors) {
      int dpiForScaleFactor = Math.round(scaleFactor * DEFAULT_DPI);
      if (dpi < dpiForScaleFactor) {
        return previousScaleFactor;
      }
      previousScaleFactor = scaleFactor;
    }
    return supportedScaleFactors[supportedScaleFactors.length - 1];
  }

  private static int getSystemDPI() {
    // On Linux, application scaling is usually done by looking at the default font size,
    // as this is a setting that is available through system preferences UI.
    // See https://codereview.chromium.org/1171693008
    // See https://github.com/derat/font-config-info
    // See com.sun.java.swing.plaf.gtk.PangoFonts
    if (SystemInfo.isUnix) {
      Object value = Toolkit.getDefaultToolkit().getDesktopProperty("gnome.Xft/DPI");
      if (value instanceof Integer) {
        int dpiValue = ((Integer)value).intValue();
        if (dpiValue == -1)
          return DEFAULT_DPI;
        return Math.round(dpiValue / 1024.0f);
      }
    }

    // Fallback to primary display DPI.
    try {
      return Toolkit.getDefaultToolkit().getScreenResolution();
    } catch (HeadlessException e) {
      return DEFAULT_DPI;
    }
  }

  public static void setScaleFactor(float scale) {
    scale = dpiToSupportedScaleFactor(Math.round(scale * DEFAULT_DPI));

    if (SystemInfo.isLinux && scale == 1.25f) {
      //Default UI font size for Unity and Gnome is 15. Scaling factor 1.25f works badly on Linux
      scale = 1f;
    }
    SCALE_FACTOR = scale;
    IconLoader.setScale(scale);
  }

  public static int scale(int i) {
    return (int)(SCALE_FACTOR * i);
  }

  public static int scaleFontSize(int fontSize) {
    if (SCALE_FACTOR == 1.25f) return (int)(fontSize * 1.34f);
    if (SCALE_FACTOR == 1.75f) return (int)(fontSize * 1.67f);
    return scale(fontSize);
  }

  public static JBDimension size(int width, int height) {
    return new JBDimension(width, height);
  }

  public static JBDimension size(int widthAndHeight) {
    return new JBDimension(widthAndHeight, widthAndHeight);
  }

  public static JBDimension size(Dimension size) {
    return size instanceof JBDimension ? ((JBDimension)size) : new JBDimension(size.width, size.height);
  }

  public static JBInsets insets(int top, int left, int bottom, int right) {
    return new JBInsets(top, left, bottom, right);
  }

  public static JBInsets insets(int all) {
    return insets(all, all, all, all);
  }

  public static JBInsets insets(int topBottom, int leftRight) {
    return insets(topBottom, leftRight, topBottom, leftRight);
  }

  public static JBInsets emptyInsets() {
    return new JBInsets(0, 0, 0, 0);
  }

  public static JBInsets insetsTop(int t) {
    return insets(t, 0, 0, 0);
  }

  public static JBInsets insetsLeft(int l) {
    return insets(0, l, 0, 0);
  }

  public static JBInsets insetsBottom(int b) {
    return insets(0, 0, b, 0);
  }

  public static JBInsets insetsRight(int r) {
    return insets(0, 0, 0, r);
  }

  public static EmptyIcon emptyIcon(int i) {
    return (EmptyIcon)EmptyIcon.create(scale(i));
  }

  public static JBDimension emptySize() {
    return new JBDimension(0, 0);
  }

  public static float scale(float f) {
    return f * SCALE_FACTOR;
  }

  public static JBInsets insets(Insets insets) {
    return JBInsets.create(insets);
  }

  public static boolean isHiDPI() {
    return SCALE_FACTOR > 1.0f;
  }

  public static class Fonts {
    public static JBFont label() {
      return JBFont.create(UIManager.getFont("Label.font"), false);
    }

    public static JBFont label(float size) {
      return label().deriveFont(scale(size));
    }

    public static JBFont smallFont() {
      return label().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.SMALL));
    }

    public static JBFont miniFont() {
      return label().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.MINI));
    }

    public static JBFont create(String fontFamily, int size) {
      return JBFont.create(new Font(fontFamily, Font.PLAIN, size));
    }
  }

  public static class Borders {
    public static JBEmptyBorder empty(int top, int left, int bottom, int right) {
      return new JBEmptyBorder(top, left, bottom, right);
    }

    public static JBEmptyBorder empty(int topAndBottom, int leftAndRight) {
      return empty(topAndBottom, leftAndRight, topAndBottom, leftAndRight);
    }

    public static JBEmptyBorder emptyTop(int offset) {
      return empty(offset, 0, 0, 0);
    }

    public static JBEmptyBorder emptyLeft(int offset) {
      return empty(0, offset,  0, 0);
    }

    public static JBEmptyBorder emptyBottom(int offset) {
      return empty(0, 0, offset, 0);
    }

    public static JBEmptyBorder emptyRight(int offset) {
      return empty(0, 0, 0, offset);
    }

    public static JBEmptyBorder empty() {
      return empty(0, 0, 0, 0);
    }

    public static Border empty(int offsets) {
      return empty(offsets, offsets, offsets, offsets);
    }

    public static Border customLine(Color color, int top, int left, int bottom, int right) {
      return new CustomLineBorder(color, insets(top, left, bottom, right));
    }

    public static Border customLine(Color color, int thickness) {
      return customLine(color, thickness, thickness, thickness, thickness);
    }

    public static Border merge(@Nullable Border source, @NotNull Border extra, boolean extraIsOutside) {
      if (source == null) return extra;
      return new CompoundBorder(extraIsOutside ? extra : source, extraIsOutside? source : extra);
    }
  }

  //TODO: BorderLayoutPanel in 15+ only.
  /*
  public static class Panels {
    public static BorderLayoutPanel simplePanel() {
      return new BorderLayoutPanel();
    }

    public static BorderLayoutPanel simplePanel(Component comp) {
      return simplePanel().addToCenter(comp);
    }

    public static BorderLayoutPanel simplePanel(int hgap, int vgap) {
      return new BorderLayoutPanel(hgap, vgap);
    }
  }
  */
}