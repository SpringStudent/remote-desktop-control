package io.github.springstudent.dekstop.client.capture;


import io.github.springstudent.dekstop.client.bean.Capture;

public interface CaptureEngineListener {
	/**
	 * Must not block.
	 */
	void onCaptured(Capture capture);

	/**
	 * Must not block: debugging purpose.
	 */
	void onRawCaptured(int id, byte[] grays);
}
