package eu.fasten.analyzer.licensedetector.utils;

import org.json.JSONObject;

public class JSONUtils {
	
	public static String optStringByPath(JSONObject jo, String path) {
		return optStringByPath(jo, path, null);
	}
	
	public static String optStringByPath(JSONObject jo, String path, String def) {
		if (path == null) return def;
		String[] apath = path.trim().split("[/]");
		for (int i = 0; i < apath.length; i++) {
			if (i == apath.length - 1) {
				return jo.optString(apath[i], def);
			}
			jo = jo.optJSONObject(apath[i]);
			if (jo == null) return def;
		}
		return def;
	}

}
