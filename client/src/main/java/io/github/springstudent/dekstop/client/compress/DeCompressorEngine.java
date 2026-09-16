package io.github.springstudent.dekstop.client.compress;

import io.github.springstudent.dekstop.client.bean.Capture;
import io.github.springstudent.dekstop.client.bean.Listeners;
import io.github.springstudent.dekstop.client.concurrent.DefaultThreadFactoryEx;
import io.github.springstudent.dekstop.client.concurrent.Executable;
import io.github.springstudent.dekstop.client.squeeze.Compressor;
import io.github.springstudent.dekstop.client.squeeze.NullTileCache;
import io.github.springstudent.dekstop.client.squeeze.RegularTileCache;
import io.github.springstudent.dekstop.client.squeeze.TileCache;
import io.github.springstudent.dekstop.common.command.CmdCapture;
import io.github.springstudent.dekstop.common.configuration.CompressorEngineConfiguration;
import io.github.springstudent.dekstop.common.log.Log;
import io.netty.channel.Channel;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DeCompressorEngine{
	private final Listeners<DeCompressorEngineListener> listeners = new Listeners<>();

	private ThreadPoolExecutor executor;

	/**
	 * 已入队但尚未解压完成的帧数;由网络线程与解压线程共同读写。
	 */
	private final AtomicInteger pendingCaptures = new AtomicInteger();

	/**
	 * 积压到该帧数即停止读取以施加背压,降到一半时恢复读取。
	 */
	private volatile int queueSize;

	private TileCache cache;

	public DeCompressorEngine(DeCompressorEngineListener listener) {
		listeners.add(listener);
	}

	public void start(int queueSize) {
		// THREAD = 1
		//
		// The parallel processing is within the de-compressor itself - here we
		// want
		// to ensure a certain order of processing - if need more than one
		// thread then
		// have a look how the de-compressed data are sent to the GUI (!)

		executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());

		executor.setThreadFactory(new DefaultThreadFactoryEx("DeCompressorEngine"));

		// 解压跟不上时的背压
		//
		// 队列积压到 queueSize 帧时,不再从通道读取(而不是阻塞网络线程),
		// TCP 接收窗口随之关闭,背压一路传回被控端,由被控端降低采集帧率。
		// 原先的实现是在网络线程上 semaphore.acquire():同样能施加背压,但会把该线程上
		// 所有其它消息(心跳、鼠标键盘命令)一起压住,而这正是"输入偶尔卡一下"的来源。
		this.queueSize = queueSize;
		// 重新 start 时旧任务可能已不会执行完,计数必须归零,否则积压数会一直偏高,
		// 导致读取被永久停掉(画面不再刷新)。
		this.pendingCaptures.set(0);
	}

	/**
	 * Should not block as called from the network incoming message thread (!)
	 */
	public void handleCapture(CmdCapture capture, Channel channel) {
		if (pendingCaptures.incrementAndGet() > queueSize) {
			pauseRead(channel);
		}
		try {
			executor.execute(new MyExecutable(executor, channel, capture));
		} catch (RejectedExecutionException ex) {
			pendingCaptures.decrementAndGet(); // 引擎已停止(不常见)
		}
	}

	/**
	 * 停止从该通道读取。通道为空(例如单元测试)时静默跳过。
	 */
	private static void pauseRead(Channel channel) {
		if (channel != null && channel.config().isAutoRead()) {
			channel.config().setAutoRead(false);
			Log.info("解压队列积压,暂停读取以施加背压");
		}
	}

	private static void resumeRead(Channel channel) {
		if (channel != null && !channel.config().isAutoRead()) {
			channel.config().setAutoRead(true);
			Log.info("解压队列已消化,恢复读取");
		}
	}

	private class MyExecutable extends Executable {
		private final CmdCapture message;

		private final Channel channel;

		MyExecutable(ExecutorService executor, Channel channel, CmdCapture message) {
			super(executor);
			this.channel = channel;
			this.message = message;
		}

		@Override
		protected void execute() throws IOException {
			try {
				final Compressor compressor = Compressor.get(message.getCompressionMethod());

				final CompressorEngineConfiguration configuration = message.getCompressionConfiguration();
				if (configuration != null) {
					cache = configuration.useCache() ? new RegularTileCache(configuration.getCacheMaxSize(), configuration.getCachePurgeSize())
							: new NullTileCache();

					Log.info("De-Compressor engine has been reconfigured [tile:" + message.getId() + "]" + configuration);
				}

				cache.clearHits();

				final Capture capture = compressor.decompress(cache, message.getPayload());
				final double ratio = capture
						.computeCompressionRatio(1/* magic-number */ + message.getWireSize());

				fireOnDeCompressed(capture, cache.getHits(), ratio);
			} finally {
				if (cache != null) {
					cache.onCaptureProcessed();
				}
				if (pendingCaptures.decrementAndGet() <= queueSize / 2) {
					resumeRead(channel);
				}
			}
		}

		private void fireOnDeCompressed(Capture capture, int cacheHits, double compressionRatio) {
			listeners.getListeners().forEach(listener -> listener.onDeCompressed(capture, cacheHits, compressionRatio));
		}
	}

}
