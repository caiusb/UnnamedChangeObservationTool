package edu.oregonstate.edu.recorders;

public interface Recorder {
	
	public void record(String fileName, int offset, int length, String content);
	
}
