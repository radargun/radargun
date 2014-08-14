package org.radargun;

import java.io.File;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Directories {
   private final static boolean allowBroken = Boolean.getBoolean("radargun.directories.allow_broken");
   public final static File ROOT_DIR;
   public final static File LIB_DIR;
   public final static File PLUGINS_DIR;
   public final static File SPECIFIC_DIR;
   public final static File REPORTERS_DIR;

   static {
      String path = Directories.class.getProtectionDomain().getCodeSource().getLocation().getPath();
      File coreJar = new File(path.indexOf('!') < 0 ? path : path.substring(0, path.lastIndexOf('!')));
      if (!coreJar.exists()) throw new IllegalStateException("Core JAR not found: " + coreJar);
      LIB_DIR = coreJar.getParentFile();
      if (!allowBroken && (!LIB_DIR.exists() || !LIB_DIR.isDirectory()))
         throw new IllegalStateException("Lib directory not found: " + LIB_DIR);
      ROOT_DIR = LIB_DIR.getParentFile();
      if (!allowBroken && (!ROOT_DIR.exists() || !ROOT_DIR.isDirectory()))
         throw new IllegalStateException("Root directory not found: " + ROOT_DIR);
      PLUGINS_DIR = new File(ROOT_DIR, "plugins");
      if (!allowBroken && (!PLUGINS_DIR.exists() || !PLUGINS_DIR.isDirectory()))
         throw new IllegalStateException("Plugins directory not found: " + PLUGINS_DIR);
      SPECIFIC_DIR = new File(ROOT_DIR, "specific");
      if (!allowBroken && (!SPECIFIC_DIR.exists() || !SPECIFIC_DIR.isDirectory()))
         throw new IllegalStateException("Specific directory not found: " + SPECIFIC_DIR);
      REPORTERS_DIR = new File(ROOT_DIR, "reporters");
      if (!allowBroken && (!REPORTERS_DIR.exists() || !REPORTERS_DIR.isDirectory()))
         throw new IllegalStateException("Reporters directory not found: " + REPORTERS_DIR);
   }
}
