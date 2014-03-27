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
package org.radargun.stages.cache.stresstest;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stats.Statistics;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Queryable;

/**
 * Stage for inserting data into indexed cache for processing.
 *
 * @author Anna Manukyan
 */
@Stage(doc = "Stage which executes puts/gets indexed entries against index enabled cache.")
public class DataForQueryStage extends StressTestStage {
   /**
    * The name of the key in slave state, which returns the word to be used in the queries.
    */
   public static final String MATCH_WORD_PROP_NAME = "matchingWord";

   @Property(doc = "The length of the generated indexed entry. Default is 100.")
   private int propertyLength = 100;

   @Property(doc = "Specifies whether the key generation should be done according to wildcard logic or no. Default is false.")
   private boolean isWildcard = false;

   @Property(doc = "Specifies the full path of the property file which contains different words for querying. No default value is provided. This property is mandatory.", optional = false)
   private String dataPath = null;

   @InjectTrait
   private Queryable queryable;

   private transient List<String> wordsFromFile = null;
   private transient Map<String, Integer> matchingWords = new Hashtable<String, Integer>();

   @Init
   public void init() {
      wordsFromFile = readDataFromFile();
   }

   @Override
   protected Stressor createStressor(int threadIndex) {
      Stressor stressor = super.createStressor(threadIndex);
      stressor.setQueryable(queryable);
      return stressor;
   }

   @Override
   // TODO: replace this with custom value generator
   public Object generateValue(Object key, int maxValueSize) {
      char[] letters = "abcdefghijklmnopqrstuvw 1234567890".toCharArray();
      Random rand = ThreadLocalRandom.current();
      StringBuffer str = new StringBuffer();

      if (isWildcard) {
         String word = wordsFromFile.get(rand.nextInt(wordsFromFile.size()));
         str.append(word);

         updateUsedWordsStatistics(word);

         while(str.length() < propertyLength) {
            char symbol = letters[rand.nextInt(letters.length)];
            str.append(symbol);
         }
      } else {
         while(str.length() < propertyLength) {
            String word = wordsFromFile.get(rand.nextInt(wordsFromFile.size()));
            str.append(word).append(" ");

            updateUsedWordsStatistics(word);
         }
      }

      return str.toString();
   }

   private void updateUsedWordsStatistics(final String word) {
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

   @Override
   protected List<Statistics> gatherResults() {
      //Writing the most used word into the slave state object
      int max = 0;
      String key = null;
      for(Map.Entry<String, Integer> elem : matchingWords.entrySet()) {
         if (elem.getValue() > max) {
            max = elem.getValue();
            key = elem.getKey();
         }
      }
      slaveState.put(MATCH_WORD_PROP_NAME, key);
      return super.gatherResults();
   }
}
