package com.boardexam.util;

import java.util.Scanner;

/**
 * Centralised UI helper — all ANSI colours, box drawing, and input parsing live here.
 * The rest of the app calls ConsoleUI methods, never System.out directly.
 * This makes it trivial to swap to a web UI later.
 */
public final class ConsoleUI {

    // ANSI colour codes
    public static final String RESET  = "\033[0m";
    public static final String BOLD   = "\033[1m";
    public static final String RED    = "\033[31m";
    public static final String GREEN  = "\033[32m";
    public static final String YELLOW = "\033[33m";
    public static final String BLUE   = "\033[34m";
    public static final String CYAN   = "\033[36m";
    public static final String WHITE  = "\033[37m";
    public static final String BG_BLUE= "\033[44m";

    private static final Scanner SCANNER = new Scanner(System.in);

    private ConsoleUI() {}

    // ── Print helpers ─────────────────────────────────────────────────────────

    public static void print(String msg)             { System.out.print(msg); }
    public static void println(String msg)           { System.out.println(msg); }
    public static void println()                     { System.out.println(); }

    public static void success(String msg)  { println(GREEN  + "✔  " + msg + RESET); }
    public static void error(String msg)    { println(RED    + "✘  " + msg + RESET); }
    public static void warn(String msg)     { println(YELLOW + "⚠  " + msg + RESET); }
    public static void info(String msg)     { println(CYAN   + "ℹ  " + msg + RESET); }
    public static void bold(String msg)     { println(BOLD   + msg + RESET); }
    public static void header(String msg)   { println(BLUE + BOLD + msg + RESET); }

    // ── Banner ────────────────────────────────────────────────────────────────

    public static void printBanner() {
        println(BLUE + BOLD);
        println("╔══════════════════════════════════════════════════════════════╗");
        println("║         CBSE — Examination & Evaluation Management System    ║");
        println("║                       EEMS 2026 v2.0                         ║");
        println("║     Central Board of Secondary Education, New Delhi          ║");
        println("╚══════════════════════════════════════════════════════════════╝");
        println(RESET);
    }

    public static void printSectionHeader(String title) {
        println();
        println(CYAN + BOLD + "┌─────────────────────────────────────────────────────────────┐");
        println("│  " + padRight(title, 61) + "│");
        println("└─────────────────────────────────────────────────────────────┘" + RESET);
    }

    public static void printDivider() {
        println(BLUE + "─".repeat(65) + RESET);
    }

    public static void printTable(String[] headers, String[][] rows) {
        int[] widths = new int[headers.length];
        for (int i = 0; i < headers.length; i++) widths[i] = headers[i].length();
        for (String[] row : rows)
            for (int i = 0; i < row.length; i++)
                if (row[i] != null) widths[i] = Math.max(widths[i], row[i].length());

        StringBuilder sep = new StringBuilder("+");
        for (int w : widths) sep.append("-".repeat(w + 2)).append("+");

        println(CYAN + sep.toString());
        StringBuilder hdr = new StringBuilder("|");
        for (int i = 0; i < headers.length; i++)
            hdr.append(" ").append(BOLD).append(padRight(headers[i], widths[i]))
               .append(RESET).append(CYAN).append(" |");
        println(hdr.toString());
        println(sep.toString());
        for (String[] row : rows) {
            StringBuilder r = new StringBuilder("|");
            for (int i = 0; i < row.length; i++)
                r.append(" ").append(padRight(row[i] == null ? "" : row[i], widths[i])).append(" |");
            println(r.toString());
        }
        println(sep.toString() + RESET);
    }

    // ── Input helpers ─────────────────────────────────────────────────────────

    public static String prompt(String message) {
        print(YELLOW + message + " → " + RESET);
        return SCANNER.nextLine().trim();
    }

    public static int promptInt(String message) {
        while (true) {
            String input = prompt(message);
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                error("Please enter a valid integer.");
            }
        }
    }

    public static int promptInt(String message, int min, int max) {
        while (true) {
            int val = promptInt(message);
            if (val >= min && val <= max) return val;
            error("Please enter a number between " + min + " and " + max + ".");
        }
    }

    public static String promptPassword(String message) {
        // In a real TTY, use Console.readPassword(); fallback to Scanner
        java.io.Console console = System.console();
        if (console != null) {
            char[] pass = console.readPassword(YELLOW + message + " → " + RESET);
            return new String(pass);
        }
        return prompt(message); // fallback for IDEs
    }

    public static boolean promptYesNo(String message) {
        while (true) {
            String ans = prompt(message + " (y/n)").toLowerCase();
            if (ans.equals("y") || ans.equals("yes")) return true;
            if (ans.equals("n") || ans.equals("no"))  return false;
            error("Please type 'y' or 'n'.");
        }
    }

    public static void pressEnterToContinue() {
        prompt(CYAN + "\nPress ENTER to continue...");
    }

    // ── Formatting utils ──────────────────────────────────────────────────────

    public static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    public static void printKeyValue(String key, String value) {
        println("  " + BOLD + padRight(key + ":", 28) + RESET + value);
    }
}
