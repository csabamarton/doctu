package com.csmarton.doctu.logprocessing;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.common.io.Resources;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ThreadingRegexpMatcher {
	private static final String LOG_DIRECTORY_PATH = System.getProperty("logdir");
	private static final String OUTPUT_LOG_DIRECTORY_PATH = System.getProperty("outputdir");
	private static final String TXT_SUFFIX = ".txt";
	private static final String REGEXP_CONFIGURATION_FILENAME = "regexconfig.properties";

	private RegExpConfiguration regExpConfiguration;
	private LogProcessorExecutor logProcessorExecutor;

	private static String content;

	private class RegExpConfiguration extends Properties {

		private Map<String, String> regExpConfigurations = Maps.newHashMap();
		private Map<String, Pattern> regExpPatterns = Maps.newHashMap();

		public Map<String, Pattern> getRegExpPatterns()
		{
			return regExpPatterns;
		}

		public Map<String, String> getRegExpConfigurations()
		{
			return regExpConfigurations;
		}

		public RegExpConfiguration() throws IOException
		{
			loadPropValues();
			setConfigurationMap();
		}

		public void loadPropValues() throws IOException
		{
			InputStream inputStream = null;

			try {
				inputStream = getClass().getClassLoader()
						.getResourceAsStream(REGEXP_CONFIGURATION_FILENAME);

				if (inputStream != null) {
					this.load(inputStream);
				} else {
					throw new FileNotFoundException(
							String.format("property file '%s' not found in the classpath",
									REGEXP_CONFIGURATION_FILENAME));
				}
			} catch (IOException e) {
				System.out.println(String.format("Exception: %s", e.getMessage()));
			} finally {
				inputStream.close();
			}
		}

		private void setConfigurationMap()
		{
			this.stringPropertyNames().stream().forEach(propKey -> {

				String regExpValue = this.getProperty(propKey);
				regExpConfigurations.put(propKey, regExpValue);
			});
		}
	}

	private class RegexpMatcherTask implements Callable<Integer> {
		String pattern;
		String fileName;

		public RegexpMatcherTask(String fileName, String pattern)
		{
			this.fileName = fileName;
			this.pattern = pattern;
		}

		@Override
		public Integer call() throws InterruptedException, IOException
		{
			Pattern regexp;
			Map<String, Pattern> regExpPatterns = regExpConfiguration.getRegExpPatterns();

			if (!regExpPatterns.containsKey(fileName)) {
				regexp = Pattern.compile(pattern, Pattern.MULTILINE);
				regExpPatterns.put(fileName, regexp);
			} else {
				regexp = regExpPatterns.get(fileName);
			}

			Matcher matcher = regexp.matcher(content);

			int count = 0;

			StringBuffer buffer = new StringBuffer();

			while (matcher.find()) {
				count++;
				buffer.append(matcher.group());
				buffer.append("\n");
			}

			if (count > 0) {
				File file = new File(OUTPUT_LOG_DIRECTORY_PATH + fileName + TXT_SUFFIX);
				Files.append(buffer.toString(), file, Charsets.UTF_8);
			}

			return count;
		}
	}

	public static void main(String[] args) throws IOException, InterruptedException
	{
		ThreadingRegexpMatcher app = new ThreadingRegexpMatcher();

		app.startProcessing();
	}

	private void startProcessing() throws IOException, InterruptedException
	{
		regExpConfiguration = new RegExpConfiguration();
		logProcessorExecutor = new LogProcessorExecutor();

		File folder = new File(LOG_DIRECTORY_PATH);
		File[] listOfFiles = folder.listFiles();

		Arrays.stream(listOfFiles).forEach(file -> {
			if (file.isFile()) {
				try {
					logProcessorExecutor.processLogFile(file);
				} catch (IOException e) {
					throw new IllegalArgumentException(
							"Something went wrong with the file creation");
				} catch (InterruptedException e) {
					new InterruptedException(String.format(
							"Processing %s file has stoped with an Interrupted Exception: %s",
							file.getName(), e.getMessage()));
				}
			}
		});

		logProcessorExecutor.finishProcessing();
	}

	private class LogProcessorExecutor {
		private ExecutorService executor;

		public LogProcessorExecutor()
		{
			int nCores = Runtime.getRuntime().availableProcessors();
			this.executor = Executors.newFixedThreadPool(nCores);
		}

		private void processLogFile(File file) throws IOException, InterruptedException
		{
			content = Resources.toString(file.toURI().toURL(), Charsets.UTF_8);

			System.out.println(String.format("Processing: %s", file.getName()));

			List<Future<Integer>> futures = Lists.newArrayList();

			regExpConfiguration.getRegExpConfigurations().forEach((key, value) -> {
				futures.add(executor.submit(new RegexpMatcherTask(key, value)));
			});

			futures.forEach(future -> {
				try {
					Integer numOfMatches = future.get();
					System.out.println(
							String.format("number of matches: %d",
									numOfMatches));
				} catch (ExecutionException e) {
					future.cancel(true);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			});

			System.out.println("-----------------");
		}

		private void finishProcessing() throws InterruptedException
		{
			executor.shutdown();
			executor.awaitTermination(1, TimeUnit.SECONDS);
		}
	}
}
