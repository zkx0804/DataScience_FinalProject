package edu.unh.cs980.entityLinking;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.log4j.Logger;

public class SpotLightClient {
	private static final String spotlightAPIurl = "http://model.dbpedia-spotlight.org/en/annotate?";
	private static final Logger logger = Logger.getLogger(SpotLightClient.class);

	public static String getAnootatedJson(String text) throws Exception {
		// Remove spaces.
		String httpUrl = spotlightAPIurl + "text=" + text.replace(" ", "%20") + "&confidence=0.5";
		String responseStr = sendGet(httpUrl);

		// Remove "@" in key of json string
		responseStr = responseStr.replace("\"@", "\"");
		// logger.info(responseStr);

		return responseStr;
	}

	private static String sendGet(String url) throws Exception {
		StringBuffer result = new StringBuffer();

		try {
			HttpClient client = HttpClients.createDefault();

			HttpGet request = new HttpGet(url);

			request.addHeader("Accept", "application/json");

			HttpResponse response = client.execute(request);

			// logger.debug("Response Code : " +
			// response.getStatusLine().getStatusCode());
			BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
			String line = "";
			while ((line = rd.readLine()) != null) {
				result.append(line);
			}

			// logger.debug(result.toString());
		} catch (Exception e) {
			// logger.error("Can't get response from SpotLight API.");
		}

		return result.toString();
	}
}
