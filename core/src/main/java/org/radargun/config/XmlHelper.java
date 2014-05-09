package org.radargun.config;

import java.util.Locale;

/**
 * Regrettably, conversion is not always reversible: "ASDFooBar" -> "asd-foo-bar" -> "AsdFooBar"
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class XmlHelper {
   public static String dashToCamelCase(String name, boolean firstLetterUpper) {
      int currIndex = 0, dashIndex;
      StringBuilder camelName = new StringBuilder(name.length());
      if (firstLetterUpper && !name.isEmpty()) {
         camelName.append(String.valueOf(name.charAt(0)).toUpperCase(Locale.ENGLISH));
         currIndex = 1;
      }
      while (currIndex < name.length() && (dashIndex = name.indexOf('-', currIndex)) >= 0) {
         camelName.append(name.substring(currIndex, dashIndex));
         if (dashIndex + 1 < name.length()) {
            camelName.append(String.valueOf(name.charAt(dashIndex + 1)).toUpperCase(Locale.ENGLISH));
         }
         currIndex = dashIndex + 2;
      }
      if (currIndex < name.length()) {
         camelName.append(name.substring(currIndex));
      }
      return camelName.toString();
   }

   public static String camelCaseToDash(String string) {
      StringBuilder sb = new StringBuilder(2 * string.length());
      boolean prevLowerCase = false, prevIsAlpha = false;
      for (int i = 0; i < string.length(); ++i) {
         boolean nextLowerCase = i < string.length() - 1 ? Character.isLowerCase(string.charAt(i + 1)) : false;
         char c = string.charAt(i);
         if (Character.isUpperCase(c)) {
            if ((prevLowerCase || nextLowerCase) && prevIsAlpha) sb.append('-');
            sb.append(String.valueOf(c).toLowerCase(Locale.ENGLISH));
         } else if (c == '.') {
            sb.append('-');
         } else {
            sb.append(c);
         }
         prevLowerCase = Character.isLowerCase(c);
         prevIsAlpha = Character.isAlphabetic(c);
      }
      return sb.toString();
   }
}
