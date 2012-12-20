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
package org.radargun.stages;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;

/**
 * Loads the contents of the specified file into the cache using the specified
 * sized values. All slaves are used to read from the file and write keys to
 * the cache.
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
@Stage(doc = "Loads the contents of a file into the cache.")
public class LoadFileStage extends AbstractDistStage {
	private CacheWrapper cacheWrapper;

	@Property(optional = false, doc = "Full pathname to the file.")
	private String filePath;

	@Property(doc = "The size of the values to put into the cache from the contents"
			+ " of the file. The default size is 1MB (1024 * 1024)")
	private int valueSize = 1024 * 1024;

	@Property(doc = "The name of the bucket where keys are written. The default is null")
	private String bucket = null;
	
	private long putCount = 0;

	@Override
	public DistStageAck executeOnSlave() {
		DefaultDistStageAck result = newDefaultStageAck();
		int totalWriters = getActiveSlaveCount();
		long fileOffset = valueSize * getSlaveIndex();// index starts at 0

		if (slaveState.getCacheWrapper() == null) {
			result.setErrorMessage("Not running test on this slave as the wrapper hasn't been configured.");
		} else {
			cacheWrapper = slaveState.getCacheWrapper();
			Charset charset = Charset.defaultCharset();  
			FileChannel theFile = null;
         long totalBytesRead = 0;
			
			try {
				theFile = new FileInputStream(filePath).getChannel();
				theFile.position(fileOffset);
				
            log.info("Size of file '" + filePath + "' is : " + theFile.size());
				while (true) {
					ByteBuffer buffer = ByteBuffer.allocate(valueSize);
					long initPos = theFile.position();
					String key = Integer.toString(getSlaveIndex()) + "-" + Long.toString(initPos);
					int bytesRead = theFile.read(buffer);
					log.debug("bytesRead = " + bytesRead);
               if (bytesRead != -1) {
                  totalBytesRead += bytesRead;
                  log.debug("Writing to cache key: " + key + " at position " + theFile.position());
                  buffer.rewind();
                  CharsetDecoder decoder = charset.newDecoder();
                  CharBuffer charBuffer = decoder.decode(buffer);
                  cacheWrapper.put(bucket, key, charBuffer.toString());
                  putCount++;
                  theFile.position(initPos + (valueSize * totalWriters));
               } else {
						theFile.close();
						theFile = null;
						break;
					}
				}
				
		      log.info("Slave " + getSlaveIndex() + " wrote " + putCount + 
		            " values to the cache with a total size of " + totalBytesRead + " bytes");
			} catch (FileNotFoundException e) {
				log.fatal("File not find at path: " + filePath, e);
				result.setError(true);
				result.setErrorMessage("File not find at path: " + filePath);
			} catch (Exception e) {
				log.fatal("An exception occurred", e);
				result.setError(true);
				result.setErrorMessage("An exception occurred");
			} finally {
				if (theFile != null) {
					try {
						theFile.close();
					} catch (IOException e) {
						log.fatal("An exception occurred closing the file", e);
					}
				}
			}
		}

		return result;
	}

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	public int getValueSize() {
		return valueSize;
	}

	public void setValueSize(int valueSize) {
		this.valueSize = valueSize;
	}

	public String getBucket() {
		return bucket;
	}

	public void setBucket(String bucket) {
		this.bucket = bucket;
	}

}
