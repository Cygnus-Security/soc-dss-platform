package com.socdss.wazuh;

public class AlertClassifier {
    private AlertClassifier() {}

    public static String classify(String ruleId, String description, String groups) {
        String text = ((ruleId == null ? "" : ruleId) + " " +
                (description == null ? "" : description) + " " +
                (groups == null ? "" : groups)).toLowerCase();

        if (containsAny(text, "sshd", "authentication", "invalid user", "login failed", "brute", "5710", "5712")) {
            return "SSH Brute Force / Authentication Attack";
        }
        if (containsAny(text, "syscheck", "fim", "integrity", "file added", "file modified", "webshell")) {
            return "File Integrity Incident";
        }
        if (containsAny(text, "cve", "vulnerability", "vulnerab")) {
            return "Vulnerability Incident";
        }
        if (containsAny(text, "sql injection", "xss", "web attack", "apache", "nginx", "http", "directory traversal")) {
            return "Web Attack";
        }
        if (containsAny(text, "nmap", "scan", "recon", "suricata")) {
            return "Reconnaissance / Network Scan";
        }
        return "General Security Alert";
    }

    private static boolean containsAny(String text, String... patterns) {
        for (String pattern : patterns) {
            if (text.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}
