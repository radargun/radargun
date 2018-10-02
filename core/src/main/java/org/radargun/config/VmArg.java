package org.radargun.config;

import java.io.Serializable;
import java.util.List;

public interface VmArg extends Serializable {
   /* Override arguments */
   void setArgs(List<String> args);
}