/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.util;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * @TODO: Spectrum will have its own pastebin-like service with more debug data (os, java version used, etc...)
 * @deprecated
 */
public class PastebinPoster {
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 5000;
    
    public static void paste(String code, PasteCallback callback) {
        PasteProcessor processor = new PasteProcessor(code, callback);
        Thread thread = new Thread(processor);
        thread.start();
    }

    public interface PasteCallback {
        void handleSuccess(String url);
        void handleError(String err);
    }
    
    private static class PasteProcessor implements Runnable {
        private String code;
        private PasteCallback callback;
        
        public PasteProcessor(String code, PasteCallback callback) {
            this.code = code;
            this.callback = callback;
        }
        
        @Override
        public void run() {
            HttpURLConnection conn = null;
            OutputStream out = null; 
            InputStream in = null;
            
            try {
                URL url = new URL("https://pastebin.com/api/api_post.php");
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);
                conn.setRequestMethod("POST");
                conn.addRequestProperty("Content-type", "application/x-www-form-urlencoded");
                conn.setInstanceFollowRedirects(false);
                conn.setDoOutput(true);
                out = conn.getOutputStream();
                
                out.write(("api_option=paste"
                        + "&api_dev_key=" + URLEncoder.encode("4867eae74c6990dbdef07c543cf8f805", StandardCharsets.UTF_8)
                        + "&api_paste_code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                        + "&api_paste_private=" + URLEncoder.encode("0", StandardCharsets.UTF_8)
                        + "&api_paste_name=" + URLEncoder.encode("", StandardCharsets.UTF_8)
                        + "&api_paste_expire_date=" + URLEncoder.encode("1D", StandardCharsets.UTF_8)
                        + "&api_paste_format=" + URLEncoder.encode("text", StandardCharsets.UTF_8)
                        + "&api_user_key=" + URLEncoder.encode("", StandardCharsets.UTF_8)).getBytes());
                out.flush();
                out.close();
                
                if (conn.getResponseCode() == 200) {     
                    in = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                    String line;
                    StringBuilder response = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                        response.append("\r\n");
                    }
                    reader.close();
                    
                    String result = response.toString().trim();
                    
                    if (result.matches("^https?://.*")) {
                        callback.handleSuccess(result.trim());
                    } else {
                        String err = result.trim();
                        if (err.length() > 100) {
                            err = err.substring(0, 100);
                        }
                        callback.handleError(err);
                    }
                } else {
                    callback.handleError("An error occurred while uploading the text.");
                }
            } catch (IOException e) {
                callback.handleError(e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ignored) {
                    }
                }
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
        
    }
    
}
