package io.onedev.server.job.log;

import java.io.InputStream;
import java.util.List;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import io.onedev.commons.utils.TaskLogger;
import io.onedev.server.buildspec.job.log.JobLogEntryEx;
import io.onedev.server.model.Build;

public interface LogService {
		
	TaskLogger newLogger(Build build);
	
	/**
	 * Read specified number of log entries from specified build, starting from specified index 
	 * 
	 * @param build
	 * 			build to read log entries from
	 * @param offset
	 * 			index of the log entry to start read
	 * @param count
	 * 			number of log entries to read. Specifically use <tt>0</tt> to read all entries 
	 * 			since offset
	 * @return
	 * 			log entries. Number of entries may be less than required count if there is no 
	 * 			enough log entries
	 */
	List<JobLogEntryEx> readLogEntries(Long projectId, Long buildNumber, int offset, int count);
	
	void registerListener(LogListener listener);
	
	void deregisterListener(LogListener logListener);
	
	boolean matches(Build build, Pattern pattern);
	
	/**
	 * Read specified number of log entries starting from end of the log
	 * 
	 * @param build
	 * 			build to read log entries from 
	 * @param count
	 * 			number of log entries to read
	 * @return
	 * 			log entries with normal order. Number of entries may be less than required count 
	 * 			if there is no enough log entries
	 */
	LogSnippet readLogSnippetReversely(Long projectId, Long buildNumber, int count);
	
	InputStream openLogStream(Long projectId, Long buildNumber);
	
	@Nullable
	TaskLogger getJobLogger(String jobToken);
	
	void addJobLogger(String jobToken, TaskLogger logger);
	
	void removeJobLogger(String jobToken);
	
}
