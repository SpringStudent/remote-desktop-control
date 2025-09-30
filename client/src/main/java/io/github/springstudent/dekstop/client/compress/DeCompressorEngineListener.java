package io.github.springstudent.dekstop.client.compress;


import io.github.springstudent.dekstop.client.bean.Capture;

public interface DeCompressorEngineListener {
	/**
	 * Called from within a de-compressor engine thread (!)
	 */
	void onDeCompressed(Capture capture, int cacheHits, double compressionRatio);
}
