package com.twitter.classpreloader;

/**
   Copyright 2012 Twitter, Inc.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Original credit to Sam Pullara: https://gist.github.com/612643
 *
 * Records classes that are loaded. Then loads them first the next time. This can
 * dramatically decrease the initial hit on a first request to web service or other
 * cases where things are loaded lazily it would be nice to precache them. It only
 * loads the classes and does not run their static initializers. I try and skip
 * most generated classes from Java and Guice.
 *
 * To create the agent jar:
 * jar cmf util/util-class-preloader/src/main/java/com/twitter/class-preloader/manifest \
 * preload.jar -C util/util-class-preloader/target/classes/ .
 *
 * To use the agent jar:
 * java -javaagent:/path/to/preload.jar=/tmp/classes.txt Main
 *
 * You can use this agent in combination with other agents. Just make sure that the other agents are
 * first on the command line otherwise preload.jar will load all the classes before they get a chance
 * to add their hook.
 */
public class ClassPreloader {

  private static SortedSet<String> classes = Collections.synchronizedSortedSet(new TreeSet<String>());

  public static void premain(final String agentArgs, Instrumentation inst) {
    try {
      System.err.println("PRELOAD: Agent initialized using " + agentArgs);
      File file = new File(agentArgs);
      if (file.exists()) {
        BufferedReader br = new BufferedReader(new FileReader(agentArgs));
        String line;
        while ((line = br.readLine()) != null) {
          try {
            ClassPreloader.class.getClassLoader().loadClass(line);
            classes.add(line);
          } catch (Throwable e) {
          }
        }
        br.close();
      }
      BufferedWriter bw = new BufferedWriter(new FileWriter(agentArgs));
      for (String className : classes) {
        bw.write(className);
        bw.write("\n");
      }
      bw.close();
      inst.addTransformer(new ClassFileTransformer() {
        @Override
        public byte[] transform(ClassLoader classLoader, String s, Class<?> aClass, ProtectionDomain protectionDomain, byte[] bytes) throws IllegalClassFormatException {
          if (skip(s)) return null;
          String name = s.replace("/", ".");
          if (classes.contains(name)) return null;
          synchronized (ClassPreloader.class) {
            try {
              BufferedWriter bw = new BufferedWriter(new FileWriter(agentArgs, true));
              bw.write(name);
              bw.write("\n");
              bw.close();
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
          return null;
        }
      }, true);
      System.err.println("PRELOAD: Loaded " + classes.size() + " classes");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static boolean skip(String name) {
    return name.startsWith("$Proxy") ||
            name.startsWith("sun/reflect/Generated") ||
            name.startsWith("java/") ||
            name.contains("$$");
  }

}
