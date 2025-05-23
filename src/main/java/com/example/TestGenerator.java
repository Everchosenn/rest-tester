package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// Клас для генерації та збереження тест-кейсів
public class TestGenerator {
    // Метод для збереження тест-кейсу у файл
    public static void saveTestCase(TestCase testCase, String testsDirectory) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            // Перевірка: чи існує папка і чи є вона папкою
            File directory = new File(testsDirectory);
            if (!directory.exists()) {
                // Спроба створити папку
                if (!directory.mkdirs()) {
                    throw new IOException("Не вдалося створити папку для тестів: " + testsDirectory);
                }
            } else if (!directory.isDirectory()) {
                throw new IOException("Шлях для тестів не є папкою: " + testsDirectory);
            }

            // Перевірка: чи є права на запис у папку
            if (!directory.canWrite()) {
                throw new IOException("Немає прав на запис у папку для тестів: " + testsDirectory);
            }

            // Генеруємо унікальне ім'я файлу на основі URL та методу
            String fileName = testCase.method() + "_" + testCase.url().replaceAll("[^a-zA-Z0-9]", "_") + ".json";
            File file = new File(testsDirectory + File.separator + fileName);

            // Перевірка: чи можна створити файл
            if (file.exists() && !file.canWrite()) {
                throw new IOException("Немає прав на запис у файл: " + file.getAbsolutePath());
            }

            // Записуємо тест-кейс у JSON-файл
            mapper.writeValue(file, testCase);
        } catch (IOException e) {
            throw new RuntimeException("Помилка при збереженні тесту: " + e.getMessage(), e);
        }
    }

    // Метод для завантаження всіх тест-кейсів із папки
    public static List<TestCase> loadTestCases(String testsDirectory) {
        List<TestCase> testCases = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        File directory = new File(testsDirectory);

        // Перевірка: чи існує папка і чи є вона папкою
        if (!directory.exists() || !directory.isDirectory()) {
            System.err.println("Папка для тестів не існує або не є папкою: " + testsDirectory);
            return testCases; // Повертаємо порожній список
        }

        // Перевірка: чи є права на читання папки
        if (!directory.canRead()) {
            System.err.println("Немає прав на читання папки для тестів: " + testsDirectory);
            return testCases; // Повертаємо порожній список
        }

        File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                // Перевірка: чи є права на читання файлу
                if (!file.canRead()) {
                    System.err.println("Немає прав на читання файлу: " + file.getAbsolutePath());
                    continue;
                }

                try {
                    TestCase testCase = mapper.readValue(file, TestCase.class);
                    testCases.add(testCase);
                } catch (IOException e) {
                    System.err.println("Помилка при завантаженні тесту з файлу " + file.getAbsolutePath() + ": " + e.getMessage());
                }
            }
        }
        return testCases;
    }
}