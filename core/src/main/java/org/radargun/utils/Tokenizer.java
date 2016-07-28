package org.radargun.utils;

import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * Tokenizer that allows string delims instead of char delims
 */
public class Tokenizer implements Enumeration<String> {

   private String string;
   private String[] delims;
   private int pos = 0;
   private int nextDelimPos = -1;
   private String nextDelim;
   private boolean returnDelims;
   private boolean nextIsDelim = false;
   private boolean returnEmpty = false;

   public Tokenizer(String string, String[] delims) {
      this(string, delims, false, false, -1);
   }

   /**
    * Return delims and return empty cannot be true both as it wouldn't make sense to return empty string between
    * two consecutive delimiters.
    *
    * @param string
    * @param delims
    * @param returnDelims
    * @param returnEmpty
    */
   public Tokenizer(String string, String[] delims, boolean returnDelims, boolean returnEmpty, int startIndex) {
      this.string = string;
      if (startIndex >= 0) {
         pos = startIndex;
      }
      if (string.isEmpty() && !returnEmpty) {
         pos = -1;
      }
      this.delims = delims;
      if (returnDelims && returnEmpty) {
         throw new IllegalArgumentException();
      }
      this.returnDelims = returnDelims;
      this.returnEmpty = returnEmpty;
      findNextDelim();
      if (nextDelimPos == pos) {
         nextIsDelim = true;
         pos += nextDelim.length();
         if (pos >= string.length()) {
            pos = -1;
         }
      }
   }

   private void findNextDelim() {
      nextDelimPos = -1;
      nextDelim = null;
      for (String delim : delims) {
         int next = string.indexOf(delim, pos);
         if (next < 0) continue;
         if (nextDelimPos < 0 || next < nextDelimPos || (next == nextDelimPos && nextDelim.length() < delim.length())) {
            nextDelimPos = next;
            nextDelim = delim;
         }
      }
   }

   @Override
   public boolean hasMoreElements() {
      return hasMoreTokens();
   }

   public boolean hasMoreTokens() {
      return (nextIsDelim && returnDelims) || pos >= 0;
   }

   @Override
   public String nextElement() {
      return nextToken();
   }

   public String nextToken() {
      if (returnDelims && nextIsDelim) {
         nextIsDelim = false;
         String delim = nextDelim;
         if (pos >= 0) {
            findNextDelim();
            if (nextDelimPos == pos) {
               nextIsDelim = true;
               pos += nextDelim.length();
               if (pos >= string.length()) {
                  pos = -1;
               }
            }
         }
         return delim;
      }
      if (pos < 0) {
         throw new NoSuchElementException();
      }
      String token;
      if (nextDelimPos >= 0) {
         token = string.substring(pos, nextDelimPos);
         pos = nextDelimPos + nextDelim.length();
         if (returnDelims) {
            nextIsDelim = true;
         } else {
            for (; ; ) {
               findNextDelim();
               if (returnEmpty) {
                  break;
               }
               if (nextDelimPos == pos) {
                  pos += nextDelim.length();
               } else {
                  break;
               }
            }
         }
         if (pos >= string.length()) {
            pos = -1;
         }
      } else {
         token = string.substring(pos);
         pos = -1;
      }
      return token;
   }

   /* After calling nextToken it returns the position just after this token */
   public int getPosition() {
      if (pos < 0) return string.length();
      return pos;
   }
}
