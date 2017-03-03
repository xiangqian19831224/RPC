package com.bird.utils;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Properties;

public class PropertyUtils {
	/**
	 * getProperties("application.properties")
	 * @param proName
	 * @return
	 */
	public static final String propertyName = "application.properties";
	
	public static Properties getProperties(String proName){
		Properties res = new Properties(); 
		InputStream in = PropertyUtils.class.getClassLoader().getResourceAsStream(proName); 
		try {
			res.load(in);
			in.close(); 
		} catch (IOException e) {
			e.printStackTrace();
		} 
		return res;
	}
	
	public static String getProValu(String proName,String key){
		Properties prop = getProperties(proName);
		String value = prop.get(key).toString();
		return value;
	}
	
	public static String getProValu(String key){
		return getProValu(propertyName,key);
	}
	
	/**
     * 返回模板信息
     * @param key
     * @param args
     * @return
     */
    public static String getMessage(String key, String... args) {
        String result = "";
        result = getProValu(key);
        if (args != null && args.length > 0) {
            return MessageFormat.format(result, args);
        }
        return result;
    }
    
    public static void main(String[] args) {
		int defaultNum = 10;
		defaultNum = Integer.parseInt(PropertyUtils.getProValu("test").trim());
		System.out.println(defaultNum);
		System.out.println(PropertyUtils.getProValu("ReaderCount"));
	}
}
