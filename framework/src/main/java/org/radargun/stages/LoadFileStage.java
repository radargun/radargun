package org.radargun.stages;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;

/**
 * Loads the contents of the specified file into the cache using the specified
 * sized values.
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

	@Override
	public DistStageAck executeOnSlave() {
		DefaultDistStageAck result = newDefaultStageAck();
		int totalWriters = getActiveSlaveCount();
		long fileOffset = valueSize * getSlaveIndex();// index starts at 0

		if (slaveState.getCacheWrapper() == null) {
			result.setErrorMessage("Not running test on this slave as the wrapper hasn't been configured.");
		} else {
			cacheWrapper = slaveState.getCacheWrapper();

			FileChannel theFile = null;
			try {
				theFile = new FileInputStream(filePath).getChannel();
				theFile.position(fileOffset);

				while (true) {
					ByteBuffer buffer = ByteBuffer.allocate(valueSize);
					String key = Integer.toString(getSlaveIndex()) + "-" + Long.toString(theFile.position());
					if (theFile.read(buffer) > 0) {
						log.info("Writing to cache key: " + key + " at position " + theFile.position());
						cacheWrapper.put(bucket, key, buffer.asCharBuffer().toString());
						theFile.position(theFile.position()
								+ (valueSize * totalWriters));
					} else {
						theFile.close();
						break;
					}
				}
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
