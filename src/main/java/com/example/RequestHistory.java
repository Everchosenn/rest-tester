package com.example;

import java.util.ArrayList;
import java.util.List;

// Клас для збереження історії запитів
public class RequestHistory {
    private final TestCase testCase; // Тест-кейс
    private final List<TestResult> results; // Список результатів виконання
    private final int displayRunCount; // Кількість результатів для відображення на графіку

    // Конструктор
    public RequestHistory(TestCase testCase, int displayRunCount) {
        this.testCase = testCase;
        this.results = new ArrayList<>();
        this.displayRunCount = displayRunCount;
    }

    // Додавання результату
    public void addResult(TestResult result) {
        this.results.add(result);
    }

    // Геттери
    public TestCase getTestCase() {
        return testCase;
    }

    public List<TestResult> getResults() {
        return results;
    }

    public int getDisplayRunCount() {
        return displayRunCount;
    }

    // Відображення у списку
    @Override
    public String toString() {
        return testCase.method() + " " + testCase.url() + (testCase.params().isEmpty() ? "" : "?" + testCase.params());
    }
}