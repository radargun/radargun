/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.radargun.stressors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.CacheWrapper;
import org.radargun.config.Property;
import org.radargun.config.Stressor;
import org.radargun.state.SlaveState;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Stressor which writes Queryable data into the cache.
 *
 * @author Anna Manukyan
 */
@Stressor(doc = "Executes put and get operations against the cache wrapper. The puts and gets are performed on indexed cache.")
public class DataForQueryStressor extends StressTestStressor {

   private static final Log log = LogFactory.getLog(DataForQueryStressor.class);

   /**
    * The name of the key in slave state, which returns the word to be used in the queries.
    */
   public static final String MATCH_WORD_PROP_NAME = "matchingWord";

   @Property(doc = "The length of the generated indexed entry. Default is 100.")
   private int propertyLength = 100;

   @Property(doc = "Specifies whether the key generation should be done according to wildcard logic or no. Default is false.")
   private boolean isWildCard = false;

   @Property(doc = "Specifies the full path of the property file which contains different words for querying. No default value is provided. This property is mandatory.")
   private String dataPath = null;

   private List<String> wordsFromFile = null;

   private Map<String, Integer> matchingWords = new Hashtable<String, Integer>();

   public DataForQueryStressor(final SlaveState slaveState) {
      this.slaveState = slaveState;
   }

   public DataForQueryStressor() {
   }

   @Override
   protected void init(CacheWrapper wrapper) {
      if(dataPath == null) {
         throw new IllegalArgumentException("The path to the data file should be provided. The attribute 'dataPath' should be not empty.");
      }

      wordsFromFile = readDataFromFile();
      super.init(wrapper);
   }

   @Override
   public Object generateValue(int maxValueSize) {
      char[] letters = "abcdefghijklmnopqrstuvw 1234567890".toCharArray();
      Random rand = new Random();
      StringBuffer str = new StringBuffer();

      if (isWildCard) {
         String word = wordsFromFile.get(rand.nextInt(wordsFromFile.size()));
         str.append(word);

         saveUsedWordsStatistics(word);

         while(str.length() < propertyLength) {
            char symbol = letters[rand.nextInt(letters.length)];
            str.append(symbol);
         }
      } else {
         while(str.length() < propertyLength) {
            String word = wordsFromFile.get(rand.nextInt(wordsFromFile.size()));
            str.append(word).append(" ");

            saveUsedWordsStatistics(word);
         }
      }

      return str.toString();
   }

   private void saveUsedWordsStatistics(final String word) {
      Integer num = 0;
      if(matchingWords.containsKey(word)) {
         num = matchingWords.get(word);
      }

      matchingWords.put(word, ++num);
   }

   private List<String> readDataFromFile() {
      List<String> wordsFromFile = new ArrayList<String>();
      BufferedReader br = null;

      try {
         br = new BufferedReader(new FileReader(dataPath));
         String line;
         while ((line = br.readLine()) != null) {
            wordsFromFile.add(line);
         }
      } catch(IOException ex) {
         log.error("Error is thrown during file reading!", ex);
         throw new RuntimeException(ex);
      } finally {
         try {
            if(br != null) br.close();
         } catch (IOException ex) {
            log.error("Error is thrown during closing file stream!", ex);
            throw new RuntimeException(ex);
         }
      }

      return wordsFromFile;
   }

   private void findAndStoreTheMostUsedWord() {
      //Writing the most used word into the slave state object

      Integer max = 0;
      String key = null;
      for(Map.Entry<String, Integer> elem : matchingWords.entrySet()) {
         if (elem.getValue() > max) {
            max = elem.getValue();
            key = elem.getKey();
         }
      }

      slaveState.put(MATCH_WORD_PROP_NAME, key);
   }

   @Override
   public void destroy() {
      findAndStoreTheMostUsedWord();
   }

   @Override
   public String toString() {
      return "DataForQueryStressor {" +
            "isWildcard =" + isWildCard +
            ", dataPath =" + dataPath +
            ", propertyLength =" + propertyLength +
            "}";
   }
}
