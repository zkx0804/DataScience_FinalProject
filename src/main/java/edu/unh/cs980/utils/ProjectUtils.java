package edu.unh.cs980.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.CharArraySet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ProjectUtils {
	// This class will contain some common utility methods for Project.
	public static Gson gson;

	private static final Logger logger = Logger.getLogger(ProjectUtils.class);

	// Create custom stop words for lucene.
	public static CharArraySet getCustomStopWordSet() {
		String stopword_dir = "stop_word.cfg";
		ArrayList<String> list = new ArrayList<String>();
		String line = "";
		try {
			/* FileReader reads text files in the default encoding */
			InputStream fis = Thread.currentThread().getContextClassLoader().getResourceAsStream(stopword_dir);

			/* always wrap the FileReader in BufferedReader */
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fis));

			while ((line = bufferedReader.readLine()) != null) {
				// System.out.println(line);
				if (!line.isEmpty()) {
					list.add(line.replace(" ", ""));
				}
			}

			/* always close the file after use */
			bufferedReader.close();
			CharArraySet stopword = new CharArraySet(list, true);
			// System.out.println(list);
			return stopword;
		} catch (IOException ex) {
			System.out.println("Error reading file named '" + stopword_dir + "'");
			return null;
		}

	}

	public static void writeToFile(String filePath, ArrayList<String> runfileStrings) {
		String fullpath = filePath;
		logger.info("Writting run file: " + filePath);
		try (FileWriter runfile = new FileWriter(new File(fullpath))) {
			for (String line : runfileStrings) {
				runfile.write(line + "\n");
			}
			logger.info("Done.");
			runfile.close();
		} catch (IOException e) {
			logger.error("Could not open " + fullpath);
		}
	}

	public static HashMap<String, Float> sortByValueDesc(Map<String, Float> unsortMap) {

		return getTopValuesInMap(unsortMap, 0);
	}

	// Sort by the value of key, descending.
	// Return the top k values, if k = 0, return all sorted map.
	public static HashMap<String, Float> getTopValuesInMap(Map<String, Float> unsortMap, int k) {
		List<Map.Entry<String, Float>> list = new LinkedList<Map.Entry<String, Float>>(unsortMap.entrySet());

		Collections.sort(list, new Comparator<Map.Entry<String, Float>>() {

			public int compare(Map.Entry<String, Float> o1, Map.Entry<String, Float> o2) {
				return (o2.getValue()).compareTo(o1.getValue());
			}
		});

		HashMap<String, Float> sortedMap = new LinkedHashMap<String, Float>();
		int i = 0;
		for (Map.Entry<String, Float> entry : list)

		{
			if (i < k || k == 0) {
				sortedMap.put(entry.getKey(), entry.getValue());
				i++;
			} else {
				break;
			}
		}

		return sortedMap;
	}

	public static Gson getGsonStringBuilder() {
		if (gson == null) {
			// Create gson to exclude Non-expose fields in entity class.
			gson = new GsonBuilder().create();
		}
		return gson;
	}
}
