package edu.oregonstate.ucor.listeners;

import edu.oregonstate.edu.recorders.Recorder;

public class DummyRecorder implements Recorder {

	public String fileName;
	public int offset;
	public int length;
	public String content;

	@Override
	public void record(String fileName, int offset, int length, String content) {
		this.fileName = fileName;
		this.offset = offset;
		this.length = length;
		this.content = content;
	}

}
