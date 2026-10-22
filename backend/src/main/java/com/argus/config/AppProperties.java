package com.argus.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All secrets and environment-specific settings are bound here from environment-backed
 * properties - never hard-coded in source.
 */
@ConfigurationProperties(prefix = "argus")
public class AppProperties {

    private final Security security = new Security();
    private final Cors cors = new Cors();
    private final Upload upload = new Upload();
    private final Feed feed = new Feed();
    private final Sim sim = new Sim();

    public Security getSecurity() {
        return security;
    }

    public Cors getCors() {
        return cors;
    }

    public Upload getUpload() {
        return upload;
    }

    public Feed getFeed() {
        return feed;
    }

    public Sim getSim() {
        return sim;
    }

    public static class Security {
        private String jwtSecret = "";
        private long jwtTtlMinutes = 720;
        private int maxFailedAttempts = 5;
        private int lockoutMinutes = 15;
        private String adminUsername = "admin";
        private String adminPassword = "";
        private String analystPassword = "";
        private String viewerPassword = "";

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }

        public long getJwtTtlMinutes() {
            return jwtTtlMinutes;
        }

        public void setJwtTtlMinutes(long jwtTtlMinutes) {
            this.jwtTtlMinutes = jwtTtlMinutes;
        }

        public int getMaxFailedAttempts() {
            return maxFailedAttempts;
        }

        public void setMaxFailedAttempts(int maxFailedAttempts) {
            this.maxFailedAttempts = maxFailedAttempts;
        }

        public int getLockoutMinutes() {
            return lockoutMinutes;
        }

        public void setLockoutMinutes(int lockoutMinutes) {
            this.lockoutMinutes = lockoutMinutes;
        }

        public String getAdminUsername() {
            return adminUsername;
        }

        public void setAdminUsername(String adminUsername) {
            this.adminUsername = adminUsername;
        }

        public String getAdminPassword() {
            return adminPassword;
        }

        public void setAdminPassword(String adminPassword) {
            this.adminPassword = adminPassword;
        }

        public String getAnalystPassword() {
            return analystPassword;
        }

        public void setAnalystPassword(String analystPassword) {
            this.analystPassword = analystPassword;
        }

        public String getViewerPassword() {
            return viewerPassword;
        }

        public void setViewerPassword(String viewerPassword) {
            this.viewerPassword = viewerPassword;
        }
    }

    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Upload {
        private String dir = "./data/uploads";
        private long maxBytes = 5_242_880L;
        private List<String> allowedExtensions = new ArrayList<>(
                List.of("json", "csv", "txt", "log", "md", "png", "jpg", "jpeg", "pdf"));

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }

        public long getMaxBytes() {
            return maxBytes;
        }

        public void setMaxBytes(long maxBytes) {
            this.maxBytes = maxBytes;
        }

        public List<String> getAllowedExtensions() {
            return allowedExtensions;
        }

        public void setAllowedExtensions(List<String> allowedExtensions) {
            this.allowedExtensions = allowedExtensions;
        }
    }

    public static class Feed {
        private int maxEvents = 400;

        public int getMaxEvents() {
            return maxEvents;
        }

        public void setMaxEvents(int maxEvents) {
            this.maxEvents = maxEvents;
        }
    }

    public static class Sim {
        private boolean autopilotEnabled = true;
        private long tickMs = 2100;

        public boolean isAutopilotEnabled() {
            return autopilotEnabled;
        }

        public void setAutopilotEnabled(boolean autopilotEnabled) {
            this.autopilotEnabled = autopilotEnabled;
        }

        public long getTickMs() {
            return tickMs;
        }

        public void setTickMs(long tickMs) {
            this.tickMs = tickMs;
        }
    }
}
